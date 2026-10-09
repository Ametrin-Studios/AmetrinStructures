package com.ametrin.structures.gametest;

import com.ametrin.structures.AmetrinStructures;
import com.ametrin.structures.fixture.FixtureBlock;
import com.ametrin.structures.fixture.FixtureGeneration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.function.Consumer;

/// Tests, their environment and their templates use their own namespace, so `export_templates` puts the templates in this source set.
@EventBusSubscriber(modid = AmetrinStructures.MOD_ID)
public final class FixtureGameTests {
    private static final String NAMESPACE = "ametrin_structures_test";
    private static final ResourceKey<Consumer<GameTestHelper>> LOOT_CHEST = function("loot_chest");

    private FixtureGameTests() {}

    @SubscribeEvent
    public static void registerFunctions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, LOOT_CHEST.identifier(), () -> FixtureGameTests::lootChest);
    }

    @SubscribeEvent
    public static void registerTests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(id("default"));
        event.registerTest(LOOT_CHEST.identifier(), new FunctionGameTestInstance(LOOT_CHEST,
                new TestData<>(environment, LOOT_CHEST.identifier(), 20, 0, true)));
    }

    private static void lootChest(GameTestHelper helper) {
        var marker = new BlockPos(1, 1, 1);
        // Read before the marker is consumed, so it doesn't matter which way the template's marker faces.
        var facing = FixtureBlock.horizontalFacing(helper.getBlockState(marker));

        FixtureGeneration.runNow(helper.getLevel(), helper.absolutePos(marker));

        helper.assertBlockPresent(Blocks.CHEST, marker);
        helper.assertBlockProperty(marker, ChestBlock.FACING, facing);
        helper.assertValueEqual(helper.getBlockEntity(marker, ChestBlockEntity.class).getLootTable(),
                BuiltInLootTables.SIMPLE_DUNGEON, "loot table");
        helper.succeed();
    }

    private static ResourceKey<Consumer<GameTestHelper>> function(String name) {
        return ResourceKey.create(Registries.TEST_FUNCTION, id(name));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NAMESPACE, path);
    }
}
