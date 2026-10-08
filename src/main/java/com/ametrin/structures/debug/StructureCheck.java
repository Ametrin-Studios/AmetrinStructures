package com.ametrin.structures.debug;

import com.ametrin.structures.fixture.*;
import com.ametrin.structures.registry.ASBlocks;
import com.ametrin.structures.registry.ASRegistries;
import com.ametrin.structures.structure.jigsaw.ExtendedJigsawStructure;
import com.ametrin.structures.structure.simple.SimpleStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.Pools;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.ListPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.DirectPoolAlias;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasBinding;
import net.minecraft.world.level.levelgen.structure.pools.alias.RandomGroupPoolAlias;
import net.minecraft.world.level.levelgen.structure.pools.alias.RandomPoolAlias;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

/// Finds what would silently go wrong once structures generate: templates that don't exist, jigsaws
/// that can't connect, and loot tables, fixture presets and other entries that templates name but
/// that aren't registered. Looks into simple and jigsaw structures and skips other types.
///
/// Loads templates but places nothing, so it is safe to run off the server thread.
final class StructureCheck {
    record Problem(Subject subject, Identifier id, Component message) {
        enum Subject {
            STRUCTURE,
            TEMPLATE,
            FIXTURE_PRESET
        }
    }

    record Report(int structures, Map<String, Integer> skipped, int templates, List<Problem> problems) {}

    private final StructureTemplateManager templates;
    private final HolderLookup.Provider registries;
    private final HolderLookup.Provider lootRegistries;
    private final HolderLookup.RegistryLookup<StructureTemplatePool> pools;
    private final RegistryOps<Tag> ops;
    // Jigsaw order doesn't matter here.
    private final RandomSource random = RandomSource.create(0);

    private final Set<Problem> problems = new LinkedHashSet<>();
    private final Map<Identifier, Optional<StructureTemplate>> checkedTemplates = new HashMap<>();
    private final Set<ResourceKey<FixturePreset>> checkedPresets = new HashSet<>();

    /// @param lootRegistries holds the loot tables, which `registries` doesn't
    StructureCheck(StructureTemplateManager templates, HolderLookup.Provider registries, HolderLookup.Provider lootRegistries) {
        this.templates = templates;
        this.registries = registries;
        this.lootRegistries = lootRegistries;
        this.pools = registries.lookupOrThrow(Registries.TEMPLATE_POOL);
        this.ops = registries.createSerializationContext(NbtOps.INSTANCE);
    }

    static Report run(MinecraftServer server, Predicate<Holder.Reference<Structure>> structures) {
        var check = new StructureCheck(server.getStructureManager(), server.registryAccess(), server.reloadableRegistries().lookup());
        var checked = new LinkedHashMap<Identifier, Structure>();
        server.registryAccess().lookupOrThrow(Registries.STRUCTURE).listElements()
                .filter(structures)
                .forEach(structure -> checked.put(structure.key().identifier(), structure.value()));
        return check.check(checked);
    }

    Report check(Map<Identifier, Structure> structures) {
        int checked = 0;
        var skipped = new TreeMap<String, Integer>();
        for (var entry : structures.entrySet()) {
            if (check(entry.getKey(), entry.getValue())) {
                checked++;
            } else {
                skipped.merge(String.valueOf(BuiltInRegistries.STRUCTURE_TYPE.getKey(entry.getValue().type())), 1, Integer::sum);
            }
        }
        var sorted = problems.stream()
                .sorted(Comparator.comparing(Problem::subject).thenComparing(Problem::id))
                .toList();
        return new Report(checked, skipped, checkedTemplates.size(), sorted);
    }

    /// @return false for a type it can't look into
    private boolean check(Identifier id, Structure structure) {
        switch (structure) {
            case SimpleStructure simple -> simple.pieces().templates().forEach(entry -> template(id, entry.template()));
            case ExtendedJigsawStructure jigsaw ->
                    checkJigsaw(id, jigsaw.startPool(), jigsaw.startJigsawName(), jigsaw.poolAliases());
            // Vanilla's start jigsaw name has no getter.
            case JigsawStructure jigsaw ->
                    checkJigsaw(id, jigsaw.getStartPool(), Optional.empty(), jigsaw.getPoolAliases());
            default -> {
                return false;
            }
        }
        return true;
    }

    /// The template, whose contents are checked the first time it is asked for. A missing one is the structure's problem.
    private Optional<StructureTemplate> template(Identifier structure, Identifier id) {
        var template = checkedTemplates.get(id);
        if (template == null) {
            template = templates.get(id);
            checkedTemplates.put(id, template);
            template.ifPresent(found -> checkContents(id, found));
        }
        if (template.isEmpty()) {
            problem(Problem.Subject.STRUCTURE, structure, "missing_template", id.toString());
        }
        return template;
    }

    private void checkContents(Identifier id, StructureTemplate template) {
        for (var palette : template.palettes) {
            for (var block : palette.blocks()) {
                var nbt = block.nbt();
                if (nbt == null) {
                    continue;
                }
                var at = Component.translatable("commands.ametrin_structures.check.at", block.pos().toShortString());
                if (block.state().is(ASBlocks.FIXTURE.get())) {
                    nbt.read(FixtureBlockEntity.FIXTURES_KEY, WeightedFixture.LIST_CODEC, ops).orElseGet(List::of)
                            .forEach(alternative -> checkFixture(Problem.Subject.TEMPLATE, id, at, alternative.fixture()));
                    nbt.read(FixtureBlockEntity.OFFSET_KEY, Vec3.CODEC)
                            .filter(offset -> !FixtureBlockEntity.isValidOffset(offset))
                            .ifPresent(offset -> problem(Problem.Subject.TEMPLATE, id, "fixture_offset", at, offset.toString(), FixtureBlockEntity.MAX_OFFSET));
                }
                nbt.read(RandomizableContainer.LOOT_TABLE_TAG, LootTable.KEY_CODEC)
                        .filter(table -> !exists(table))
                        .ifPresent(table -> problem(Problem.Subject.TEMPLATE, id, "missing_loot_table", at, table.identifier().toString()));
            }
        }
    }

    private void checkFixture(Problem.Subject subject, Identifier id, Component where, Fixture fixture) {
        if (fixture instanceof Fixture.Unreadable unreadable) {
            problem(subject, id, "unreadable_fixture", where, unreadable.error());
            return;
        }
        fixture.references()
                .filter(key -> !exists(key))
                .forEach(key -> problem(subject, id, "missing_reference", where, key.registry().toString(), key.identifier().toString()));
        if (fixture instanceof Fixtures.Preset(var key) && checkedPresets.add(key)) {
            registries.lookupOrThrow(ASRegistries.FIXTURE_PRESET).get(key).ifPresent(preset -> {
                var alternatives = preset.value().fixtures();
                for (int i = 0; i < alternatives.size(); i++) {
                    var alternative = Component.translatable("commands.ametrin_structures.check.alternative", i + 1);
                    checkFixture(Problem.Subject.FIXTURE_PRESET, key.identifier(), alternative, alternatives.get(i).fixture());
                }
            });
        }
    }

    private boolean exists(ResourceKey<?> key) {
        return exists(registries, key) || exists(lootRegistries, key);
    }

    private static <T> boolean exists(HolderLookup.Provider registries, ResourceKey<T> key) {
        return registries.lookup(key.registryKey()).flatMap(lookup -> lookup.get(key)).isPresent();
    }

    private record Connector(StructureTemplate.JigsawBlockInfo jigsaw, String template) {
        Component at() {
            return Component.translatable("commands.ametrin_structures.check.at", jigsaw.info().pos().toShortString());
        }
    }

    /// @param complete false when some of its templates are missing.
    private record PoolJigsaws(List<Connector> connectors, boolean complete) {}

    private record Link(Connector source, Holder<StructureTemplatePool> target) {}

    private void checkJigsaw(Identifier structure, Holder<StructureTemplatePool> startPool, Optional<Identifier> startJigsawName, List<PoolAliasBinding> poolAliases) {
        // Follows every jigsaw from the start pool, as generation could, and checks that each can connect.
        var aliases = new HashMap<ResourceKey<StructureTemplatePool>, Set<ResourceKey<StructureTemplatePool>>>();
        poolAliases.forEach(binding -> collectAliases(binding, aliases));
        // Per structure, so each reports its own missing templates.
        var jigsaws = new HashMap<Holder<StructureTemplatePool>, PoolJigsaws>();

        var start = jigsaws(structure, startPool, jigsaws);
        startJigsawName
                .filter(name -> start.complete() && start.connectors().stream().noneMatch(connector -> connector.jigsaw().name().equals(name)))
                .ifPresent(name -> problem(Problem.Subject.STRUCTURE, structure, "start_jigsaw", poolName(startPool), name.toString()));

        var links = new ArrayList<Link>();
        var queue = new ArrayDeque<>(List.of(startPool));
        var visited = new HashSet<Holder<StructureTemplatePool>>();
        while (!queue.isEmpty()) {
            var pool = queue.poll();
            if (!visited.add(pool)) {
                continue;
            }
            var fallback = pool.value().getFallback();
            if (isEmpty(fallback)) {
                problem(Problem.Subject.STRUCTURE, structure, "empty_fallback", poolName(pool), poolName(fallback));
            }
            queue.add(fallback);
            for (var connector : jigsaws(structure, pool, jigsaws).connectors()) {
                var named = connector.jigsaw().pool();
                for (var key : aliases.getOrDefault(named, Set.of(named))) {
                    var target = pools.get(key);
                    if (target.isEmpty()) {
                        problem(Problem.Subject.STRUCTURE, structure, "missing_pool", connector.at(), connector.template(), key.identifier().toString());
                    } else if (isEmpty(target.get())) {
                        problem(Problem.Subject.STRUCTURE, structure, "empty_pool", connector.at(), connector.template(), key.identifier().toString());
                    } else if (!target.get().is(Pools.EMPTY)) {
                        queue.add(target.get());
                        links.add(new Link(connector, target.get()));
                    }
                }
            }
        }

        // A jigsaw another piece attaches to is taken by that piece, so where it would lead doesn't
        // matter. Such jigsaws often point back the way they came, which never fits.
        var receivers = new HashSet<Connector>();
        for (var link : links) {
            candidates(structure, link, jigsaws)
                    .filter(candidate -> canFace(link.source().jigsaw(), candidate.jigsaw()))
                    .forEach(receivers::add);
        }
        for (var link : links) {
            if (!receivers.contains(link.source())) {
                checkConnection(structure, link, jigsaws);
            }
        }
    }

    /// The jigsaws in the link's target pool, or its fallback, that the source names.
    private Stream<Connector> candidates(Identifier structure, Link link, Map<Holder<StructureTemplatePool>, PoolJigsaws> jigsaws) {
        return Stream.of(link.target(), link.target().value().getFallback())
                .flatMap(pool -> jigsaws(structure, pool, jigsaws).connectors().stream())
                .filter(candidate -> candidate.jigsaw().name().equals(link.source().jigsaw().target()));
    }

    private void checkConnection(Identifier structure, Link link, Map<Holder<StructureTemplatePool>, PoolJigsaws> jigsaws) {
        var source = link.source();
        var named = candidates(structure, link, jigsaws).toList();
        var targetName = source.jigsaw().target().toString();
        if (named.isEmpty()) {
            // A missing template may hold the match.
            if (jigsaws(structure, link.target(), jigsaws).complete() && jigsaws(structure, link.target().value().getFallback(), jigsaws).complete()) {
                problem(Problem.Subject.STRUCTURE, structure, "unmatched_jigsaw", source.at(), source.template(), targetName, poolName(link.target()));
            }
        } else if (named.stream().noneMatch(candidate -> canFace(source.jigsaw(), candidate.jigsaw()))) {
            problem(Problem.Subject.STRUCTURE, structure, "misfacing_jigsaw", source.at(), source.template(), targetName, poolName(link.target()));
        }
    }

    // Pieces only turn around the vertical axis: a sideways jigsaw can meet any other sideways one, an upward one only a downward one.
    static boolean canFace(StructureTemplate.JigsawBlockInfo source, StructureTemplate.JigsawBlockInfo target) {
        var from = JigsawBlock.getFrontFacing(source.info().state());
        var to = JigsawBlock.getFrontFacing(target.info().state());
        return from.getAxis().isHorizontal() ? to.getAxis().isHorizontal() : to == from.getOpposite();
    }

    private PoolJigsaws jigsaws(Identifier structure, Holder<StructureTemplatePool> pool, Map<Holder<StructureTemplatePool>, PoolJigsaws> cache) {
        var cached = cache.get(pool);
        if (cached != null) {
            return cached;
        }
        var connectors = new ArrayList<Connector>();
        var complete = true;
        for (var entry : pool.value().getTemplates()) {
            var element = entry.getFirst();
            if (!templatesExist(structure, element)) {
                complete = false;
                continue;
            }
            var name = elementName(element);
            element.getShuffledJigsawBlocks(templates, BlockPos.ZERO, Rotation.NONE, random)
                    .forEach(jigsaw -> connectors.add(new Connector(jigsaw, name)));
        }
        var jigsaws = new PoolJigsaws(List.copyOf(connectors), complete);
        cache.put(pool, jigsaws);
        return jigsaws;
    }

    private boolean templatesExist(Identifier structure, StructurePoolElement element) {
        return switch (element) {
            case SinglePoolElement single -> template(structure, single.getTemplateLocation()).isPresent();
            case ListPoolElement list -> {
                var all = true;
                for (var child : list.getElements()) {
                    all &= templatesExist(structure, child);
                }
                yield all;
            }
            default -> true;
        };
    }

    private static String elementName(StructurePoolElement element) {
        return switch (element) {
            case SinglePoolElement single -> single.getTemplateLocation().toString();
            // A list element's jigsaws are its first element's.
            case ListPoolElement list when !list.getElements().isEmpty() -> elementName(list.getElements().getFirst());
            default -> element.toString();
        };
    }

    // As generation sees it: the empty pool is meant to be empty.
    private static boolean isEmpty(Holder<StructureTemplatePool> pool) {
        return pool.value().size() == 0 && !pool.is(Pools.EMPTY);
    }

    private static String poolName(Holder<StructureTemplatePool> pool) {
        return pool.unwrapKey().map(key -> key.identifier().toString()).orElse("<inline>");
    }

    /// Every pool each alias can stand for.
    static void collectAliases(PoolAliasBinding binding, Map<ResourceKey<StructureTemplatePool>, Set<ResourceKey<StructureTemplatePool>>> aliases) {
        switch (binding) {
            case DirectPoolAlias(var alias, var target) ->
                    aliases.computeIfAbsent(alias, _ -> new HashSet<>()).add(target);
            case RandomPoolAlias(var alias, var targets) ->
                    targets.unwrap().forEach(target -> aliases.computeIfAbsent(alias, _ -> new HashSet<>()).add(target.value()));
            case RandomGroupPoolAlias(var groups) ->
                    groups.unwrap().forEach(group -> group.value().forEach(member -> collectAliases(member, aliases)));
            // Only one of the choices of a binding of another type shows.
            default ->
                    binding.forEachResolved(RandomSource.create(0), (alias, target) -> aliases.computeIfAbsent(alias, _ -> new HashSet<>()).add(target));
        }
    }

    private void problem(Problem.Subject subject, Identifier id, String key, Object... arguments) {
        problems.add(new Problem(subject, id, Component.translatable("commands.ametrin_structures.check." + key, arguments)));
    }
}
