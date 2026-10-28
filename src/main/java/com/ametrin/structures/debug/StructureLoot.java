package com.ametrin.structures.debug;

import com.ametrin.structures.fixture.EmptyFixture;
import com.ametrin.structures.fixture.EntityFixture;
import com.ametrin.structures.fixture.FillContainerFixture;
import com.ametrin.structures.fixture.FixtureBlockEntity;
import com.ametrin.structures.fixture.FixtureCondition;
import com.ametrin.structures.fixture.FixtureGeneration;
import com.ametrin.structures.fixture.LootContainerFixture;
import com.ametrin.structures.fixture.WeightedFixture;
import com.ametrin.structures.registry.ASBlocks;
import com.ametrin.structures.structure.StructureSetOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentUser;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrushableBlock;
import net.minecraft.world.level.block.StructureBlock;
import net.minecraft.world.level.block.state.properties.StructureMode;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pools.EmptyPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.FeaturePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/// The loot containers, armor stands and item frames in real layouts of a structure, without placing it.
///
/// Fixtures are drawn like generation would, with conditions checked at the marker. Processors only run when placing, so containers they would remove still count.
/// Only template pieces are read. Pieces that place their contents in code, like the desert pyramid's, and data markers are only counted.
final class StructureLoot {
    private static final Set<EntityType<?>> ITEM_FRAMES = Set.of(EntityType.ITEM_FRAME, EntityType.GLOW_ITEM_FRAME);

    private StructureLoot() {}

    record Container(ResourceKey<LootTable> lootTable, BlockPos pos) {}

    /// An armor stand or item frame, from a template or an entity fixture. Breaking it drops everything it holds.
    ///
    /// @param equipment an entity fixture's equipment table
    /// @param items     an entity fixture's items per slot
    record Display(EntityType<?> type, CompoundTag data, Optional<ResourceKey<LootTable>> equipment, Map<EquipmentSlot, ItemStackTemplate> items, BlockPos pos) {
        static boolean accepts(EntityType<?> type) {
            return type == EntityType.ARMOR_STAND || ITEM_FRAMES.contains(type);
        }

        boolean holdsAnything() {
            return equipment.isPresent() || !items.isEmpty() || data.contains("equipment") || data.contains("Item");
        }

        /// What breaking it drops, with the equipment table rolled with `random`.
        List<ItemStack> drops(ServerLevel level, RandomSource random) {
            if (ITEM_FRAMES.contains(type)) {
                return data.read("Item", ItemStack.CODEC, level.registryAccess().createSerializationContext(NbtOps.INSTANCE)).stream().toList();
            }
            if (!(EntityType.loadEntityRecursive(type, data.copy(), level, EntitySpawnReason.STRUCTURE, entity -> entity) instanceof ArmorStand stand)) {
                return List.of();
            }
            stand.setPos(Vec3.atBottomCenterOf(pos));
            equipment.ifPresent(table -> equip(stand, table, level, random));
            items.forEach((slot, item) -> stand.setItemSlot(slot, item.create()));
            return Arrays.stream(EquipmentSlot.values())
                    .map(stand::getItemBySlot)
                    .filter(stack -> !stack.isEmpty() && !EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP))
                    .toList();
        }

        // Like EquipmentUser#equip, but rolled with `random`, so the world's random sequence doesn't advance.
        private static void equip(ArmorStand stand, ResourceKey<LootTable> table, ServerLevel level, RandomSource random) {
            var params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, stand.position())
                    .withParameter(LootContextParams.THIS_ENTITY, stand)
                    .create(LootContextParamSets.EQUIPMENT);
            var slots = new EquipmentUser() {
                @Override
                public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
                    stand.setItemSlot(slot, stack);
                }

                @Override
                public ItemStack getItemBySlot(EquipmentSlot slot) {
                    return stand.getItemBySlot(slot);
                }

                @Override
                public void setDropChance(EquipmentSlot slot, float dropChance) {}
            };
            var filled = new ArrayList<EquipmentSlot>();
            for (var stack : level.getServer().reloadableRegistries().getLootTable(table).getRandomItems(params, random)) {
                var slot = slots.resolveSlot(stack, filled);
                if (slot != null) {
                    slots.setItemSlot(slot, slot.limit(stack));
                    filled.add(slot);
                }
            }
        }
    }

    /// @param codePieces  pieces that aren't templates, so they place their contents in code
    /// @param dataMarkers data structure blocks, which the piece turns into something in code
    record Contents(List<Container> containers, List<Display> displays, int codePieces, int dataMarkers) {}

    record Layout(Holder<Structure> structure, Contents contents) {}

    /// @param radius how far it looked, in chunks. When it found enough, the distance of the last layout.
    record Layouts(List<Layout> layouts, int radius) {}

    /// Lays out the structures `structures` accepts at the up to `count` nearest spots their sets pick within `radius` chunks of `center`.
    /// A spot a structure `structures` rejects takes is skipped.
    static Layouts layouts(ServerLevel level, Predicate<Holder<Structure>> structures, ChunkPos center, int radius, int count, RandomSource random) {
        var state = level.getChunkSource().getGeneratorState();
        var sets = state.possibleStructureSets().stream()
                .map(Holder::value)
                .filter(set -> set.structures().stream().anyMatch(entry -> structures.test(entry.structure())))
                .toList();
        var layouts = new ArrayList<Layout>();
        int reached = radius;
        var nearest = new StructureCandidates<>(state, sets, StructureSet::placement, center, radius);
        while (layouts.size() < count && nearest.hasNext()) {
            var spot = nearest.next();
            for (var option : StructureSetOrder.of(spot.source(), state.getLevelSeed(), spot.chunk())) {
                var candidate = option.structure();
                var stub = candidate.value().findValidGenerationPoint(StructureSpread.context(level, state, candidate.value(), spot.chunk()));
                if (stub.isPresent()) {
                    if (structures.test(candidate)) {
                        layouts.add(new Layout(candidate, contents(level, stub.get().getPiecesBuilder().build(), random)));
                        reached = StructureSpread.distance(center, spot.chunk());
                    }
                    break;
                }
            }
        }
        return new Layouts(List.copyOf(layouts), layouts.size() == count ? reached : radius);
    }

    static Contents contents(ServerLevel level, PiecesContainer pieces, RandomSource random) {
        var collector = new Collector(level, random);
        for (var piece : pieces.pieces()) {
            switch (piece) {
                case TemplateStructurePiece template -> collector.collect(template.template(), template.placeSettings(), template.templatePosition());
                case PoolElementStructurePiece pooled -> {
                    switch (pooled.getElement()) {
                        case SinglePoolElement single -> collector.collect(
                                level.getStructureManager().getOrCreate(single.getTemplateLocation()),
                                new StructurePlaceSettings().setRotation(pooled.getRotation()),
                                pooled.getPosition());
                        case EmptyPoolElement _, FeaturePoolElement _ -> {}
                        default -> collector.codePieces++;
                    }
                }
                default -> collector.codePieces++;
            }
        }
        return new Contents(List.copyOf(collector.containers), List.copyOf(collector.displays), collector.codePieces, collector.dataMarkers);
    }

    private static final class Collector {
        private final ServerLevel level;
        private final RandomSource random;
        private final List<Container> containers = new ArrayList<>();
        private final List<Display> displays = new ArrayList<>();
        private int codePieces;
        private int dataMarkers;

        Collector(ServerLevel level, RandomSource random) {
            this.level = level;
            this.random = random;
        }

        void collect(StructureTemplate template, StructurePlaceSettings settings, BlockPos origin) {
            if (!template.palettes.isEmpty()) {
                for (var block : settings.getRandomPalette(template.palettes, origin).blocks()) {
                    collect(block, StructureTemplate.calculateRelativePosition(settings, block.pos()).offset(origin));
                }
            }
            for (var entity : template.entityInfoList) {
                var pos = StructureTemplate.calculateRelativePosition(settings, entity.blockPos).offset(origin);
                entity.nbt.read("id", BuiltInRegistries.ENTITY_TYPE.byNameCodec())
                        .filter(Display::accepts)
                        .map(type -> new Display(type, entity.nbt, Optional.empty(), Map.of(), pos))
                        .filter(Display::holdsAnything)
                        .ifPresent(displays::add);
            }
        }

        private void collect(StructureTemplate.StructureBlockInfo block, BlockPos pos) {
            if (block.state().is(Blocks.STRUCTURE_BLOCK) && block.state().getValue(StructureBlock.MODE) == StructureMode.DATA) {
                dataMarkers++;
            } else if (block.nbt() == null || block.state().getBlock() instanceof BrushableBlock) {
                return;
            } else if (block.state().is(ASBlocks.FIXTURE)) {
                fixture(block.nbt(), pos);
            } else {
                block.nbt().read(RandomizableContainer.LOOT_TABLE_TAG, LootTable.KEY_CODEC).ifPresent(table -> containers.add(new Container(table, pos)));
            }
        }

        private void fixture(CompoundTag marker, BlockPos pos) {
            var ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            var alternatives = marker.read(FixtureBlockEntity.FIXTURES_KEY, WeightedFixture.LIST_CODEC, ops).orElse(List.of());
            switch (FixtureGeneration.draw(alternatives, new FixtureCondition.Context(level, pos), random, false).orElse(EmptyFixture.INSTANCE)) {
                case LootContainerFixture container -> containers.add(new Container(container.lootTable(), pos));
                case FillContainerFixture container -> containers.add(new Container(container.lootTable(), pos));
                case EntityFixture entity -> BuiltInRegistries.ENTITY_TYPE.getOptional(entity.entity())
                        .filter(Display::accepts)
                        .map(type -> new Display(type, entity.nbt().orElseGet(CompoundTag::new), entity.equipment(), entity.items(), pos))
                        .filter(Display::holdsAnything)
                        .ifPresent(displays::add);
                default -> {}
            }
        }
    }
}
