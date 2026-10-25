package com.ametrin.structures.gametest;

import com.ametrin.structures.AmetrinStructures;
import com.ametrin.structures.debug.LootStatsGameTests;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@EventBusSubscriber(modid = AmetrinStructures.MOD_ID)
public final class ASGameTests {
    // uses their own namespace, so `export_templates` puts the templates in this source set
    private static final String NAMESPACE = "ametrin_structures_test";
    // A stone floor, for tests that place nothing.
    private static final Identifier EMPTY = id("empty");

    private record GameTest(String name, Identifier structure, Consumer<GameTestHelper> test) {}

    private static final List<GameTest> TESTS = tests();

    private ASGameTests() {}

    private static List<GameTest> tests() {
        var tests = new ArrayList<GameTest>();
        for (var entry : FixtureLayout.ENTRIES) {
            tests.add(new GameTest("placed_" + entry.name(), FixtureLayout.STRUCTURE, helper -> {
                FixtureLayout.place(helper);
                entry.check().accept(helper, entry.pos());
                helper.succeed();
            }));
        }
        LootStatsGameTests.TESTS.forEach((name, test) -> tests.add(new GameTest(name, EMPTY, test)));
        return tests;
    }

    @SubscribeEvent
    public static void registerFunctions(RegisterEvent event) {
        for (var test : TESTS) {
            event.register(Registries.TEST_FUNCTION, function(test).identifier(), test::test);
        }
    }

    @SubscribeEvent
    public static void registerTests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(id("default"));
        for (var test : TESTS) {
            var function = function(test);
            event.registerTest(function.identifier(), new FunctionGameTestInstance(function, new TestData<>(environment, test.structure(), 20, 0, true)));
        }
    }

    private static ResourceKey<Consumer<GameTestHelper>> function(GameTest test) {
        return ResourceKey.create(Registries.TEST_FUNCTION, id(test.name()));
    }

    static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NAMESPACE, path);
    }
}
