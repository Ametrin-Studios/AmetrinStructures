package com.ametrin.structures.debug;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Items;
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

import java.util.Map;
import java.util.function.Consumer;

/// Game tests rather than unit tests, since rolling loot needs a server level.
public final class LootStatsGameTests {
    private static final int SAMPLES = 10_000;

    public static final Map<String, Consumer<GameTestHelper>> TESTS = Map.of(
            "loot_stats_counts_every_item", LootStatsGameTests::countsEveryItem,
            "loot_stats_luck_adds_bonus_rolls", LootStatsGameTests::luckAddsBonusRolls);

    private LootStatsGameTests() {}

    private static void countsEveryItem(GameTestHelper helper) {
        var table = LootTable.lootTable()
                .withPool(LootPool.lootPool().add(LootItem.lootTableItem(Items.DIAMOND).apply(SetItemCountFunction.setCount(ConstantValue.exactly(2)))))
                .withPool(LootPool.lootPool().when(LootItemRandomChanceCondition.randomChance(0.5F)).add(LootItem.lootTableItem(Items.EMERALD)))
                .build();
        var stats = LootStatsCommand.sample(table, params(helper, 0), SAMPLES, RandomSource.create(0));

        helper.assertValueEqual(stats.empty(), 0, "empty samples");
        helper.assertValueEqual(stats.items().get(Items.DIAMOND), new LootStatsCommand.ItemStats(2L * SAMPLES, SAMPLES, 2, 2), "diamonds");
        var emerald = stats.items().get(Items.EMERALD);
        helper.assertTrue(Math.abs((double) emerald.withItem() / SAMPLES - 0.5) < 0.03, "emeralds in about half the samples, got " + emerald.withItem());
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

    private static LootParams params(GameTestHelper helper, float luck) {
        return new LootParams.Builder(helper.getLevel())
                .withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                .withLuck(luck)
                .create(LootContextParamSets.CHEST);
    }
}
