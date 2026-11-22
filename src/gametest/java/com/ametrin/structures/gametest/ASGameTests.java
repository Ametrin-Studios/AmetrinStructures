package com.ametrin.structures.gametest;

import com.ametrin.structures.AmetrinStructures;
import com.ametrin.structures.debug.LayoutGameTests;
import com.ametrin.structures.debug.LootStatsGameTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@EventBusSubscriber(modid = AmetrinStructures.MOD_ID)
public final class ASGameTests {
    // uses their own namespace, so `export_templates` puts the templates in this source set
    private static final String NAMESPACE = "ametrin_structures_test";
    // A stone floor, for tests that place nothing.
    private static final ResourceLocation EMPTY = id("empty");
    /// Where the test structure starts. Test positions are relative to the structure block below it.
    public static final BlockPos STRUCTURE_ORIGIN = new BlockPos(0, 1, 0);

    private ASGameTests() {}

    @SubscribeEvent
    public static void registerTests(RegisterGameTestsEvent event) {
        event.register(ASGameTests.class);
    }

    @GameTestGenerator
    public static List<TestFunction> tests() {
        var tests = new ArrayList<TestFunction>();
        for (var entry : FixtureLayout.ENTRIES) {
            tests.add(test("placed_" + entry.name(), FixtureLayout.STRUCTURE, helper -> {
                FixtureLayout.place(helper);
                entry.check().accept(helper, entry.pos());
                helper.succeed();
            }));
        }
        LootStatsGameTests.TESTS.forEach((name, test) -> tests.add(test(name, EMPTY, test)));
        LayoutGameTests.TESTS.forEach((name, test) -> tests.add(test(name, EMPTY, test)));
        return tests;
    }

    private static TestFunction test(String name, ResourceLocation structure, Consumer<GameTestHelper> test) {
        return new TestFunction("defaultBatch", id(name).toString(), structure.toString(), 20, 0, true, test);
    }

    static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
    }
}
