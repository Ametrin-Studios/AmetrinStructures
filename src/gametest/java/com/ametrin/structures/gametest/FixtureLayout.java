package com.ametrin.structures.gametest;

import com.ametrin.structures.fixture.*;
import com.ametrin.structures.registry.ASAttachments;
import com.ametrin.structures.registry.ASBlocks;
import com.ametrin.structures.registry.ASSpawnerProfiles;
import com.ametrin.structures.spawner.SpawnerAccess;
import com.ametrin.structures.structure.simple.SimpleStructurePiece;
import com.ametrin.structures.structure.simple.TerrainBox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.FrontAndTop;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerConfig;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

/// One marker per fixture type, placed through a real [SimpleStructurePiece]. Each test places the whole layout and checks only its own marker.
///
/// The markers are written into the test area and captured into a template, so the fixtures are defined here with their codecs instead of in a hand-made file.
final class FixtureLayout {
    static final Identifier STRUCTURE = ASGameTests.id("fixture_layout");
    private static final Identifier TEMPLATE = ASGameTests.id("fixture_layout_captured");
    // The size of the fixture_layout structure: a stone floor at Y 0.
    private static final Vec3i SIZE = new Vec3i(43, 10, 5);
    private static final ResourceKey<TrialSpawnerConfig> TRIAL_HUSK =
            ResourceKey.create(Registries.TRIAL_SPAWNER_CONFIG, Identifier.withDefaultNamespace("trial_chamber/melee/husk/normal"));

    record Entry(String name, BlockPos pos, Marker marker, BiConsumer<GameTestHelper, BlockPos> check) {}

    /// @param orientation empty for the default
    /// @param alternatives built from the marker's position in the level, for conditions that need it
    record Marker(Optional<FrontAndTop> orientation, Vec3 offset, Function<BlockPos, List<WeightedFixture>> alternatives) {
        static Marker of(Fixture fixture) {
            return new Marker(Optional.empty(), Vec3.ZERO, _ -> List.of(new WeightedFixture(1, fixture)));
        }

        Marker facing(FrontAndTop orientation) {
            return new Marker(Optional.of(orientation), offset, alternatives);
        }
    }

    static final List<Entry> ENTRIES = List.of(
            // Turned away from the default, so a chest placed with the default facing fails the check.
            new Entry("loot_container", slot(0), Marker.of(LootContainerFixture.chest(BuiltInLootTables.SIMPLE_DUNGEON)).facing(FrontAndTop.EAST_UP), (helper, pos) -> {
                helper.assertBlockPresent(Blocks.CHEST, pos);
                helper.assertBlockProperty(pos, ChestBlock.FACING, Direction.EAST);
                helper.assertValueEqual(helper.getBlockEntity(pos, ChestBlockEntity.class).getLootTable(),
                        BuiltInLootTables.SIMPLE_DUNGEON, "loot table");
            }),
            entry("fill_container", 1, new FillContainerFixture(BuiltInLootTables.SIMPLE_DUNGEON, Blocks.FURNACE.defaultBlockState()), (helper, pos) -> {
                helper.assertBlockPresent(Blocks.FURNACE, pos);
                helper.assertFalse(helper.getBlockEntity(pos, FurnaceBlockEntity.class).isEmpty(), "furnace is empty");
            }),
            entry("entity", 2, EntityFixture.of(EntityTypes.ARMOR_STAND), (helper, pos) ->
                    helper.assertEntityPresent(EntityTypes.ARMOR_STAND, pos)),
            entry("villager", 3, new VillagerFixture(Optional.empty(), Optional.of(VillagerProfession.LIBRARIAN), false), (helper, pos) ->
                    helper.assertTrue(helper.getEntities(EntityTypes.VILLAGER).stream()
                            .anyMatch(villager -> villager.getVillagerData().profession().is(VillagerProfession.LIBRARIAN)), "no librarian")),
            entry("spawner", 4, SpawnerFixture.of(EntityTypes.ZOMBIE), (helper, pos) -> {
                var next = SpawnerAccess.nextSpawnData(helper.getBlockEntity(pos, SpawnerBlockEntity.class).getSpawner());
                helper.assertTrue(next != null && next.entityToSpawn().getStringOr("id", "").equals("minecraft:zombie"), "spawner doesn't spawn zombies");
            }),
            entry("spawner_profile", 5, new SpawnerProfileFixture(ASSpawnerProfiles.DEFAULT, false), (helper, pos) ->
                    helper.assertTrue(helper.getBlockEntity(pos, SpawnerBlockEntity.class).hasData(ASAttachments.SPAWNER_PROFILE), "spawner has no profile")),
            entry("trial_spawner", 6, new TrialSpawnerFixture(TRIAL_HUSK, Optional.empty(), 36000, 14), (helper, pos) -> {
                var configs = helper.getLevel().registryAccess().lookupOrThrow(Registries.TRIAL_SPAWNER_CONFIG);
                helper.assertValueEqual(helper.getBlockEntity(pos, TrialSpawnerBlockEntity.class).getTrialSpawner().normalConfig(),
                        configs.getValueOrThrow(TRIAL_HUSK), "normal config");
            }),
            entry("vault", 7, new VaultFixture(false, Optional.of(BuiltInLootTables.TRIAL_CHAMBERS_REWARD), Optional.empty(), Optional.empty()), (helper, pos) ->
                    helper.assertValueEqual(helper.getBlockEntity(pos, VaultBlockEntity.class).getConfig().lootTable(),
                            BuiltInLootTables.TRIAL_CHAMBERS_REWARD, "loot table")),
            entry("block_state", 8, new BlockStateFixture(Blocks.LANTERN.defaultBlockState()), (helper, pos) ->
                    helper.assertBlockPresent(Blocks.LANTERN, pos)),
            entry("archaeology", 9, new ArchaeologyFixture(Optional.of(BuiltInLootTables.DESERT_PYRAMID_ARCHAEOLOGY), Blocks.SUSPICIOUS_SAND.defaultBlockState()), (helper, pos) -> {
                var saved = helper.getBlockEntity(pos, BrushableBlockEntity.class).saveWithoutMetadata(helper.getLevel().registryAccess());
                helper.assertValueEqual(saved.getStringOr("LootTable", ""), BuiltInLootTables.DESERT_PYRAMID_ARCHAEOLOGY.identifier().toString(), "loot table");
            }),
            // Three blocks up, so there's a gap to fill down to the floor.
            new Entry("extend_down", slot(10).above(2), Marker.of(new ExtendFixture(ExtendFixture.Vertical.DOWN, Optional.of(Blocks.COBBLESTONE.defaultBlockState()), 8)), (helper, pos) -> {
                helper.assertBlockPresent(Blocks.COBBLESTONE, pos);
                helper.assertBlockPresent(Blocks.COBBLESTONE, pos.below(2));
            }),
            // The conditions see the action position, two blocks below the marker: only gold may generate there.
            // Gravity would do the same, but the test area's barrier ceiling is the surface it falls to.
            new Entry("condition_at_action", slot(11).above(2), new Marker(Optional.empty(), new Vec3(0, -2, 0), marker -> List.of(
                    new WeightedFixture(1, 1.0F, List.of(atHeight(marker.getY() - 2)), new BlockStateFixture(Blocks.GOLD_BLOCK.defaultBlockState())),
                    new WeightedFixture(1, 1.0F, List.of(atHeight(marker.getY())), new BlockStateFixture(Blocks.IRON_BLOCK.defaultBlockState())))), (helper, pos) -> {
                helper.assertBlockPresent(Blocks.GOLD_BLOCK, pos.below(2));
                helper.assertBlockPresent(Blocks.AIR, pos);
            }),
            new Entry("condition_fails", slot(12), new Marker(Optional.empty(), Vec3.ZERO, _ -> List.of(new WeightedFixture(
                    1, 1.0F, List.of(new FixtureConditions.InDimension(List.of(Level.NETHER))), new BlockStateFixture(Blocks.GOLD_BLOCK.defaultBlockState())))), (helper, pos) ->
                    helper.assertBlockPresent(Blocks.AIR, pos)),
            // Last, so the tree's leaves don't reach other markers. The tree needs the dirt under it and the structure's height above it.
            entry("feature", 13, new FeatureFixture(TreeFeatures.OAK), (helper, pos) ->
                    helper.assertBlockPresent(Blocks.OAK_LOG, pos)));

    private FixtureLayout() {}

    private static Entry entry(String name, int slot, Fixture fixture, BiConsumer<GameTestHelper, BlockPos> check) {
        return new Entry(name, slot(slot), Marker.of(fixture), check);
    }

    private static FixtureCondition atHeight(int y) {
        return new FixtureConditions.HeightRange(Optional.of(y), Optional.of(y));
    }

    private static BlockPos slot(int slot) {
        return new BlockPos(1 + 3 * slot, 1, 2);
    }

    /// Writes every marker, captures the area into a template and places that template back through a [SimpleStructurePiece].
    static void place(GameTestHelper helper) {
        var level = helper.getLevel();
        var registries = level.registryAccess();
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        for (var entry : ENTRIES) {
            var marker = entry.marker();
            var alternatives = marker.alternatives().apply(helper.absolutePos(entry.pos()));
            if (alternatives.stream().anyMatch(alternative -> alternative.fixture() instanceof FeatureFixture)) {
                helper.setBlock(entry.pos().below(), Blocks.DIRT);
            }
            var state = ASBlocks.FIXTURE.get().defaultBlockState();
            helper.setBlock(entry.pos(), marker.orientation().map(orientation -> state.setValue(FixtureBlock.ORIENTATION, orientation)).orElse(state));
            var blockEntity = helper.getBlockEntity(entry.pos(), FixtureBlockEntity.class);
            blockEntity.setFixtureData(alternatives.stream()
                    .map(alternative -> WeightedFixture.MAP_CODEC.codec().encodeStart(ops, alternative).getOrThrow())
                    .toList(), registries);
            blockEntity.setOffset(marker.offset());
        }

        var origin = helper.absolutePos(BlockPos.ZERO);
        var templates = level.getStructureTemplateManager();
        templates.getOrCreate(TEMPLATE).fillFromWorld(level, origin, SIZE, false, List.of());
        for (var entry : ENTRIES) {
            helper.setBlock(entry.pos(), Blocks.AIR);
        }

        var piece = new SimpleStructurePiece(templates, TEMPLATE, origin, Rotation.NONE, Optional.empty(), TerrainAdjustment.NONE, TerrainBox.template(), 0);
        piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), level.getRandom(), BoundingBox.infinite(), ChunkPos.containing(origin), origin);
    }
}
