package com.ametrin.structures.structure.jigsaw;

import com.ametrin.structures.structure.ExtendedStructure;
import com.ametrin.structures.structure.ExtendedStructureSettings;
import com.ametrin.structures.structure.Foundation;
import com.ametrin.structures.structure.simple.HeightAnchor;
import com.ametrin.structures.structure.simple.PieceSource;
import com.ametrin.structures.structure.simple.PieceSources;
import com.ametrin.structures.structure.simple.SimpleStructure;
import com.ametrin.structures.structure.simple.StartHeight;
import com.ametrin.structures.structure.simple.TemplateEntry;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.DebugLevelSource;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pools.DimensionPadding;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class JigsawSourceTest {
    private static final int SEEDS = 25;
    private static final int FAILED = -1;
    private static final int UNREACHED = -2;
    private static final ResourceKey<StructureTemplatePool> TOWN_CENTERS = pool("village/plains/town_centers");
    private static final ResourceKey<StructureTemplatePool> HOUSES = pool("village/plains/houses");
    private static final Identifier SMALL_HOUSE = Identifier.withDefaultNamespace("village/plains/houses/plains_small_house_1");
    private static final Identifier TEMPLE = Identifier.withDefaultNamespace("village/plains/houses/plains_temple_3");

    @Test
    void aVillageGrowsLikeVanilla(MinecraftServer server) {
        assertGrowsLikeVanilla(server, new JigsawSource.Builder(holder(server, TOWN_CENTERS)).size(6).useExpansionHack(true).build(), 64);
    }

    @Test
    void anAncientCityGrowsLikeVanilla(MinecraftServer server) {
        var source = new JigsawSource.Builder(holder(server, pool("ancient_city/city_center")))
                .startJigsawName(Identifier.withDefaultNamespace("city_anchor"))
                .size(7)
                .maxDistanceFromCenter(116)
                .build();
        assertGrowsLikeVanilla(server, source, -27);
    }

    @Test
    void aBastionGrowsLikeVanilla(MinecraftServer server) {
        assertGrowsLikeVanilla(server, new JigsawSource.Builder(holder(server, pool("bastion/starts"))).size(6).build(), 33);
    }

    @Test
    void aMaxCountIsNeverExceeded(MinecraftServer server) {
        var unlimited = countAcrossSeeds(server, SMALL_HOUSE, element -> element);
        assertTrue(unlimited.stream().anyMatch(count -> count > 1), "the test needs a house that appears more than once on its own, got " + unlimited);

        var limited = countAcrossSeeds(server, SMALL_HOUSE, element -> new CountedPoolElement(element, 0, Optional.of(1)));
        assertTrue(limited.stream().allMatch(count -> count <= 1), "at most one, got " + limited);
    }

    @Test
    void aMinCountIsMetByBacktracking(MinecraftServer server) {
        var unlimited = countAcrossSeeds(server, TEMPLE, element -> element);
        assertTrue(unlimited.stream().anyMatch(count -> count >= 0 && count < 2), "the test needs a house that's often missing on its own, got " + unlimited);

        var required = countAcrossSeeds(server, TEMPLE, element -> new CountedPoolElement(element, 2, Optional.empty()));
        var generated = required.stream().filter(count -> count != FAILED).toList();
        assertTrue(generated.stream().allMatch(count -> count == UNREACHED || count >= 2), "at least two wherever the houses are reached, got " + required);
        assertTrue(generated.size() >= SEEDS * 3 / 4, "most layouts can meet it, got " + required);
    }

    @Test
    void jigsawsThatCantLeadToAMissingElementWait(MinecraftServer server) {
        var lookup = housesWith(server, TEMPLE, element -> new CountedPoolElement(element, 1, Optional.empty()));
        int checked = 0;
        for (int seed = 0; seed < SEEDS; seed++) {
            // Without backtracking, so the order is what growing produced.
            var names = assemble(server, lookup.lookup(), seed, 0).stream().flatMap(List::stream)
                    .map(piece -> piece.getElement() == lookup.replacement() ? "temple" : piece.getElement().toString())
                    .toList();
            int firstVillager = indexOf(names, name -> name.contains("/villagers/"));
            if (firstVillager < 0) {
                continue;
            }
            checked++;
            // Villagers only come from houses, so they wait until the temple is in or the streets and houses are done.
            var after = names.subList(firstVillager, names.size());
            assertTrue(names.subList(0, firstVillager).contains("temple") || after.stream().noneMatch(name -> name.contains("/streets/") || name.contains("/houses/")), "seed " + seed + ": " + names);
        }
        assertTrue(checked > 0, "the test needs layouts with villagers");
    }

    @Test
    void anUnreachableMinCountDoesNotStopTheLayout(MinecraftServer server) {
        var pools = server.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
        var unreachable = new CountedPoolElement(StructurePoolElement.single(TEMPLE.toString()).apply(StructureTemplatePool.Projection.RIGID), 1, Optional.empty());
        var pool = new StructureTemplatePool(pools.getOrThrow(pool("empty")), List.of(Pair.of(unreachable, 1)));
        var lookup = overriding(server, pool("unused"), pool);
        assertTrue(assemble(server, lookup, 0, JigsawSource.DEFAULT_MAX_BACKTRACKS).isPresent());
    }

    @Test
    void theStartPieceStandsOnTheStartHeight(MinecraftServer server) {
        var structure = SimpleStructure.builder(Identifier.fromNamespaceAndPath("test", "village"))
                .jigsaw(TOWN_CENTERS, jigsaw -> jigsaw.size(2).useExpansionHack(true))
                .verticalPlacement(StartHeight.at(HeightAnchor.absolute(70)))
                .build(bootstrap(server));
        var generated = assertInstanceOf(ExtendedStructure.Evaluation.Generated.class, structure.evaluateGenerationPoint(context(server, 0)));
        var pieces = generated.stub().getPiecesBuilder().build().pieces();
        var start = (PoolElementStructurePiece) pieces.getFirst();
        assertEquals(70, start.getBoundingBox().minY() + start.getGroundLevelDelta());
        assertTrue(pieces.size() > 1, "the layout grew from the start piece");
    }

    @Test
    void grownPiecesKeepClearOfTheOtherSourcesPieces(MinecraftServer server) {
        var village = new JigsawSource.Builder(holder(server, TOWN_CENTERS)).size(4).useExpansionHack(true).build();
        var temple = new PieceSources.SingleSource(TemplateEntry.of(TEMPLE));
        var overlapsAlone = false;
        for (int seed = 0; seed < SEEDS; seed++) {
            var templeBox = grow(server, temple, seed).getFirst().getBoundingBox();
            overlapsAlone |= grow(server, village, seed).stream().skip(1).anyMatch(piece -> piece.getBoundingBox().intersects(templeBox));
            // The temple and the start piece come first, then what grew.
            var together = grow(server, new PieceSources.CompoundSource(List.of(temple, village)), seed);
            assertTrue(together.stream().skip(2).noneMatch(piece -> piece.getBoundingBox().intersects(templeBox)), "seed " + seed);
        }
        assertTrue(overlapsAlone, "the test needs a village that grows into the temple on its own");
    }

    @Test
    void theStructuresFoundationReachesExtendedElements(MinecraftServer server) {
        var element = new ExtendedSinglePoolElement(Either.left(Identifier.withDefaultNamespace("igloo/top")), Holder.direct(new StructureProcessorList(List.of())),
                StructureTemplatePool.Projection.RIGID, Optional.empty(), true, Optional.empty());
        var pools = server.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
        var start = Holder.direct(new StructureTemplatePool(pools.getOrThrow(pool("empty")), List.of(Pair.of(element, 1))));
        var structure = new SimpleStructure(new Structure.StructureSettings(HolderSet.empty()), ExtendedStructureSettings.DEFAULT, new JigsawSource.Builder(start).size(0).build(),
                StartHeight.at(HeightAnchor.absolute(64)), LiquidSettings.IGNORE_WATERLOGGING, Optional.of(Foundation.repeating()), Optional.empty());
        var generated = assertInstanceOf(ExtendedStructure.Evaluation.Generated.class, structure.evaluateGenerationPoint(context(server, 0)));
        var placed = (PoolElementStructurePiece) generated.stub().getPiecesBuilder().build().pieces().getFirst();
        assertEquals(Optional.of(Foundation.repeating()), assertInstanceOf(ExtendedSinglePoolElement.class, placed.getElement()).foundation());
    }

    @Test
    void anElementsOwnFoundationAndVanillaElementsStayAsTheyAre() {
        var own = Foundation.of(Blocks.STONE.defaultBlockState(), 4);
        var element = new ExtendedSinglePoolElement(Either.left(TEMPLE), Holder.direct(new StructureProcessorList(List.of())),
                StructureTemplatePool.Projection.RIGID, Optional.empty(), true, Optional.of(own));
        assertSame(element, ExtendedSinglePoolElement.withDefaultFoundation(element, Foundation.repeating()));
        var vanilla = StructurePoolElement.single(TEMPLE.toString()).apply(StructureTemplatePool.Projection.RIGID);
        assertSame(vanilla, ExtendedSinglePoolElement.withDefaultFoundation(vanilla, Foundation.repeating()));
    }

    @Test
    void readsFromJson(MinecraftServer server) {
        var json = JsonParser.parseString("""
                {"type": "ametrin_structures:jigsaw", "start_pool": "minecraft:village/plains/town_centers", "size": 3}
                """);
        var source = assertInstanceOf(JigsawSource.class, PieceSource.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, server.registryAccess()), json).getOrThrow());
        assertEquals(Optional.of(TOWN_CENTERS), source.startPool().unwrapKey());
        assertEquals(JigsawSource.DEFAULT_MAX_BACKTRACKS, source.maxBacktracks());
    }

    @Test
    void aMaxDistanceBeyondTheStructureRangeIsRejected(MinecraftServer server) {
        var json = JsonParser.parseString("""
                {"type": "ametrin_structures:jigsaw", "start_pool": "minecraft:village/plains/town_centers", "max_distance_from_center": 117}
                """);
        assertTrue(PieceSource.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, server.registryAccess()), json).isError());
    }

    @Test
    void invalidCountsAreRejectedWhenBuilt() {
        var element = StructurePoolElement.single(TEMPLE.toString()).apply(StructureTemplatePool.Projection.RIGID);
        assertThrows(IllegalArgumentException.class, () -> new CountedPoolElement(element, 2, Optional.of(1)));
        assertThrows(IllegalArgumentException.class, () -> new CountedPoolElement(element, -1, Optional.empty()));
    }

    @Test
    void aMaxCountBelowTheMinCountIsRejected(MinecraftServer server) {
        var json = JsonParser.parseString("""
                {"element_type": "ametrin_structures:counted", "min_count": 2, "max_count": 1,
                 "element": {"element_type": "minecraft:empty_pool_element"}}
                """);
        assertTrue(StructurePoolElement.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, server.registryAccess()), json).isError());
    }

    private static void assertGrowsLikeVanilla(MinecraftServer server, JigsawSource source, int y) {
        for (int seed = 0; seed < SEEDS; seed++) {
            var vanilla = JigsawPlacement.addPieces(context(server, seed), source.startPool(), source.startJigsawName(), source.size().sample(RandomSource.create()), new BlockPos(0, y, 0), source.useExpansionHack(), Optional.empty(), source.maxDistanceFromCenter(), PoolAliasLookup.EMPTY, DimensionPadding.ZERO, LiquidSettings.IGNORE_WATERLOGGING);
            var expected = vanilla.orElseThrow().getPiecesBuilder().build().pieces().stream().map(Placed::of).toList();

            var context = context(server, seed);
            var pieces = new ArrayList<StructurePiece>();
            var growth = source.appendPieces(pieces, new PieceSource.Context(context, BlockPos.ZERO, Rotation.getRandom(context.random()), TerrainAdjustment.NONE, LiquidSettings.IGNORE_WATERLOGGING));
            pieces.forEach(piece -> piece.move(0, y, 0));
            assertTrue(growth.grow(pieces, new BlockPos(0, y, 0)));

            assertEquals(expected, pieces.stream().map(Placed::of).toList(), "seed " + seed);
        }
    }

    // The source's pieces at Y 0, grown.
    private static List<StructurePiece> grow(MinecraftServer server, PieceSource source, long seed) {
        var context = context(server, seed);
        var pieces = new ArrayList<StructurePiece>();
        var growth = source.appendPieces(pieces, new PieceSource.Context(context, BlockPos.ZERO, Rotation.getRandom(context.random()), TerrainAdjustment.NONE, LiquidSettings.IGNORE_WATERLOGGING));
        assertTrue(growth.grow(pieces, BlockPos.ZERO));
        return pieces;
    }

    // How often `template` appears in a village layout at each seed, with the houses pool's element for it replaced by `replace`.
    // Zombie villages have houses of their own, so they're UNREACHED.
    private static List<Integer> countAcrossSeeds(MinecraftServer server, Identifier template, Function<StructurePoolElement, StructurePoolElement> replace) {
        var houses = housesWith(server, template, replace);
        var counts = new ArrayList<Integer>();
        for (int seed = 0; seed < SEEDS; seed++) {
            counts.add(assemble(server, houses.lookup(), seed, JigsawSource.DEFAULT_MAX_BACKTRACKS)
                    .map(pieces -> pieces.stream().noneMatch(piece -> houses.elements().contains(piece.getElement()))
                            ? UNREACHED
                            : (int) pieces.stream().filter(piece -> piece.getElement() == houses.replacement()).count())
                    .orElse(FAILED));
        }
        return counts;
    }

    private record Houses(Function<ResourceKey<StructureTemplatePool>, Optional<? extends Holder<StructureTemplatePool>>> lookup, StructurePoolElement replacement, List<StructurePoolElement> elements) {}

    // The pools with the houses pool's element for `template` replaced by `replace`.
    private static Houses housesWith(MinecraftServer server, Identifier template, Function<StructurePoolElement, StructurePoolElement> replace) {
        var houses = server.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL).getOrThrow(HOUSES).value();
        var weights = new LinkedHashMap<StructurePoolElement, Integer>();
        houses.getShuffledTemplates(RandomSource.create(0)).forEach(element -> weights.merge(element, 1, Integer::sum));
        var original = weights.keySet().stream().filter(element -> element instanceof SinglePoolElement single && single.getTemplateLocation().equals(template)).findFirst().orElseThrow();
        var replacement = replace.apply(original);
        var elements = weights.entrySet().stream().map(entry -> Pair.of(entry.getKey() == original ? replacement : entry.getKey(), entry.getValue())).toList();
        var lookup = overriding(server, HOUSES, new StructureTemplatePool(houses.getFallback(), elements));
        return new Houses(lookup, replacement, elements.stream().map(Pair::getFirst).toList());
    }

    private static Optional<List<PoolElementStructurePiece>> assemble(MinecraftServer server, Function<ResourceKey<StructureTemplatePool>, Optional<? extends Holder<StructureTemplatePool>>> pools, long seed, int maxBacktracks) {
        var context = context(server, seed);
        var templates = server.getStructureManager();
        var random = context.random();
        var rotation = Rotation.getRandom(random);
        var element = holder(server, TOWN_CENTERS).value().getRandomTemplate(random);
        var position = new BlockPos(0, 63, 0);
        var start = new PoolElementStructurePiece(templates, element, position, element.getGroundLevelDelta(), rotation, element.getBoundingBox(templates, position, rotation), LiquidSettings.IGNORE_WATERLOGGING);
        var free = JigsawAssembler.freeSpace(start.getBoundingBox(), 64, new JigsawStructure.MaxDistance(80), context.heightAccessor(), DimensionPadding.ZERO);
        var assembler = new JigsawAssembler(context, pools, 6, true, PoolAliasLookup.EMPTY, LiquidSettings.IGNORE_WATERLOGGING, maxBacktracks);
        return assembler.assemble(start, free, Shapes.empty());
    }

    private static int indexOf(List<String> names, Predicate<String> matches) {
        return IntStream.range(0, names.size()).filter(i -> matches.test(names.get(i))).findFirst().orElse(-1);
    }

    private static Function<ResourceKey<StructureTemplatePool>, Optional<? extends Holder<StructureTemplatePool>>> overriding(MinecraftServer server, ResourceKey<StructureTemplatePool> key, StructureTemplatePool pool) {
        var pools = server.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
        return requested -> requested.equals(key) ? Optional.of(Holder.direct(pool)) : pools.get(requested);
    }

    private record Placed(StructurePoolElement element, BlockPos position, Rotation rotation, BoundingBox box, int groundLevelDelta, List<JigsawJunction> junctions) {
        static Placed of(StructurePiece piece) {
            var pooled = (PoolElementStructurePiece) piece;
            return new Placed(pooled.getElement(), pooled.getPosition(), pooled.getRotation(), pooled.getBoundingBox(), pooled.getGroundLevelDelta(), List.copyOf(pooled.getJunctions()));
        }
    }

    private static ResourceKey<StructureTemplatePool> pool(String path) {
        return ResourceKey.create(Registries.TEMPLATE_POOL, Identifier.withDefaultNamespace(path));
    }

    private static Holder<StructureTemplatePool> holder(MinecraftServer server, ResourceKey<StructureTemplatePool> key) {
        return server.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL).getOrThrow(key);
    }

    private static Structure.GenerationContext context(MinecraftServer server, long seed) {
        var plains = server.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
        return new Structure.GenerationContext(
                server.registryAccess(),
                new DebugLevelSource(plains),
                new FixedBiomeSource(plains),
                RandomState.create(server.registryAccess(), NoiseGeneratorSettings.OVERWORLD, 0),
                server.getStructureManager(),
                seed,
                new ChunkPos(0, 0),
                LevelHeightAccessor.create(-64, 384),
                biome -> true);
    }

    private static BootstrapContext<Structure> bootstrap(MinecraftServer server) {
        return new BootstrapContext<>() {
            @Override
            public Holder.Reference<Structure> register(ResourceKey<Structure> key, Structure value, Lifecycle lifecycle) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <S> HolderGetter<S> lookup(ResourceKey<? extends Registry<? extends S>> key) {
                return server.registryAccess().lookupOrThrow(key);
            }
        };
    }
}
