package com.ametrin.structures.debug;

import com.ametrin.structures.fixture.EntityFixture;
import com.ametrin.structures.fixture.FixtureBlockEntity;
import com.ametrin.structures.fixture.LootContainerFixture;
import com.ametrin.structures.fixture.WeightedFixture;
import com.ametrin.structures.registry.ASBlocks;
import com.ametrin.structures.structure.simple.SimpleStructurePiece;
import com.ametrin.structures.structure.simple.TerrainBox;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StructureBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.StructureMode;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/// Game tests rather than unit tests, since rolling loot needs a server level.
public final class LootStatsGameTests {
    private static final int SAMPLES = 10_000;

    public static final Map<String, Consumer<GameTestHelper>> TESTS = Map.of(
            "loot_stats_counts_every_item", LootStatsGameTests::countsEveryItem,
            "loot_stats_luck_adds_bonus_rolls", LootStatsGameTests::luckAddsBonusRolls,
            "loot_stats_finds_contents", LootStatsGameTests::findsContents);

    private LootStatsGameTests() {}

    private static void countsEveryItem(GameTestHelper helper) {
        var table = LootTable.lootTable()
                .withPool(LootPool.lootPool().add(LootItem.lootTableItem(Items.DIAMOND).apply(SetItemCountFunction.setCount(ConstantValue.exactly(2)))))
                .withPool(LootPool.lootPool().when(LootItemRandomChanceCondition.randomChance(0.5F)).add(LootItem.lootTableItem(Items.EMERALD)))
                .build();
        var stats = LootStatsCommand.sample(table, params(helper, 0), SAMPLES, RandomSource.create(0));

        helper.assertValueEqual(stats.empty(), 0, "empty samples");
        helper.assertValueEqual(stats.items().get(Items.DIAMOND), new LootStatsCommand.Counts(2L * SAMPLES, SAMPLES, 2, 2), "diamonds");
        var emerald = stats.items().get(Items.EMERALD);
        helper.assertTrue(Math.abs((double) emerald.samplesWith() / SAMPLES - 0.5) < 0.03, "emeralds in about half the samples, got " + emerald.samplesWith());
        helper.assertValueEqual(emerald.min(), 0, "fewest emeralds");
        helper.assertValueEqual(emerald.max(), 1, "most emeralds");
        helper.succeed();
    }

    private static void luckAddsBonusRolls(GameTestHelper helper) {
        var table = LootTable.lootTable()
                .withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(0)).setBonusRolls(ConstantValue.exactly(1)).add(LootItem.lootTableItem(Items.DIAMOND)))
                .build();
        helper.assertValueEqual(LootStatsCommand.sample(table, params(helper, 0), SAMPLES, RandomSource.create(0)).empty(), SAMPLES, "empty samples without luck");
        helper.assertValueEqual(LootStatsCommand.sample(table, params(helper, 2), SAMPLES, RandomSource.create(0)).items().get(Items.DIAMOND).total(),
                2L * SAMPLES, "diamonds with luck 2");
        helper.succeed();
    }

    private static void findsContents(GameTestHelper helper) {
        var level = helper.getLevel();
        var chest = new BlockPos(0, 1, 0);
        helper.setBlock(chest, Blocks.CHEST);
        helper.getBlockEntity(chest, ChestBlockEntity.class).setLootTable(BuiltInLootTables.SIMPLE_DUNGEON);
        var ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var chestMarker = new BlockPos(2, 1, 2);
        marker(helper, chestMarker, new WeightedFixture(1, LootContainerFixture.chest(BuiltInLootTables.DESERT_PYRAMID)));
        var standMarker = new BlockPos(2, 1, 0);
        var stand = new EntityFixture(BuiltInRegistries.ENTITY_TYPE.getResourceKey(EntityType.ARMOR_STAND).orElseThrow(), Optional.empty(), Optional.empty(), 0, Optional.empty(),
                Map.of(EquipmentSlot.MAINHAND, new ItemStackTemplate(Items.STICK)));
        marker(helper, standMarker, new WeightedFixture(1, stand));
        helper.setBlock(new BlockPos(0, 1, 2), Blocks.STRUCTURE_BLOCK.defaultBlockState().setValue(StructureBlock.MODE, StructureMode.DATA));

        var origin = helper.absolutePos(BlockPos.ZERO);
        var id = Identifier.fromNamespaceAndPath("ametrin_structures_test", "loot_stats_captured");
        var template = level.getStructureManager().getOrCreate(id);
        template.fillFromWorld(level, origin, new Vec3i(3, 2, 3), false, List.of());
        // An empty frame holds nothing, so it isn't counted.
        for (var entity : List.of(
                "{id: \"minecraft:armor_stand\", equipment: {head: {id: \"minecraft:diamond_helmet\"}}}",
                "{id: \"minecraft:item_frame\", Item: {id: \"minecraft:emerald\"}}",
                "{id: \"minecraft:glow_item_frame\"}")) {
            template.entityInfoList.add(new StructureTemplate.StructureEntityInfo(new Vec3(1.5, 1, 1.5), new BlockPos(1, 1, 1), nbt(entity)));
        }
        var piece = new SimpleStructurePiece(level.getStructureManager(), id, origin, Rotation.NONE, Optional.empty(), TerrainAdjustment.NONE, TerrainBox.template(), 0);
        var contents = StructureLoot.contents(level, new PiecesContainer(List.of(piece)), RandomSource.create(0));

        helper.assertValueEqual(Set.copyOf(contents.containers()), Set.of(
                new StructureLoot.Container(BuiltInLootTables.SIMPLE_DUNGEON, helper.absolutePos(chest)),
                new StructureLoot.Container(BuiltInLootTables.DESERT_PYRAMID, helper.absolutePos(chestMarker))), "containers");
        var drops = contents.displays().stream()
                .flatMap(display -> display.drops(level, RandomSource.create(0)).stream())
                .map(ItemStack::getItem)
                .collect(Collectors.toSet());
        helper.assertValueEqual(drops, Set.of(Items.DIAMOND_HELMET, Items.EMERALD, Items.STICK), "drops");
        helper.assertValueEqual(contents.displays().size(), 3, "armor stands and item frames");
        helper.assertValueEqual(contents.dataMarkers(), 1, "data markers");
        helper.assertValueEqual(contents.codePieces(), 0, "pieces built in code");

        // A list element places each of its templates, so it has the chests of both.
        var list = StructurePoolElement.list(List.of(StructurePoolElement.single(id.toString()), StructurePoolElement.single(id.toString()))).apply(StructureTemplatePool.Projection.RIGID);
        var listPiece = new PoolElementStructurePiece(level.getStructureManager(), list, origin, 0, Rotation.NONE, piece.getBoundingBox(), LiquidSettings.APPLY_WATERLOGGING);
        var listContents = StructureLoot.contents(level, new PiecesContainer(List.of(listPiece)), RandomSource.create(0));
        helper.assertValueEqual(listContents.containers().size(), 4, "containers of a list element");
        helper.assertValueEqual(listContents.codePieces(), 0, "list elements built in code");
        helper.succeed();
    }

    private static CompoundTag nbt(String snbt) {
        try {
            return TagParser.parseCompoundFully(snbt);
        } catch (CommandSyntaxException exception) {
            throw new IllegalArgumentException(exception);
        }
    }

    private static void marker(GameTestHelper helper, BlockPos pos, WeightedFixture fixture) {
        var level = helper.getLevel();
        helper.setBlock(pos, ASBlocks.FIXTURE.get());
        var data = WeightedFixture.MAP_CODEC.codec().encodeStart(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), fixture).getOrThrow();
        helper.getBlockEntity(pos, FixtureBlockEntity.class).setFixtureData(List.of(data), level.registryAccess());
    }

    private static LootParams params(GameTestHelper helper, float luck) {
        return new LootParams.Builder(helper.getLevel())
                .withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                .withLuck(luck)
                .create(LootContextParamSets.CHEST);
    }
}
