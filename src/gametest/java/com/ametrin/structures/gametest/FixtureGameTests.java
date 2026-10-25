package com.ametrin.structures.gametest;

import com.ametrin.structures.AmetrinStructures;
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

import java.util.function.Consumer;

@EventBusSubscriber(modid = AmetrinStructures.MOD_ID)
public final class FixtureGameTests {
    // uses their own namespace, so `export_templates` puts the templates in this source set
    private static final String NAMESPACE = "ametrin_structures_test";

    private FixtureGameTests() {}

    @SubscribeEvent
    public static void registerFunctions(RegisterEvent event) {
        for (var entry : FixtureLayout.ENTRIES) {
            event.register(Registries.TEST_FUNCTION, layoutFunction(entry).identifier(), () -> helper -> {
                FixtureLayout.place(helper);
                entry.check().accept(helper, entry.pos());
                helper.succeed();
            });
        }
    }

    @SubscribeEvent
    public static void registerTests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(id("default"));
        for (var entry : FixtureLayout.ENTRIES) {
            var function = layoutFunction(entry);
            event.registerTest(function.identifier(), new FunctionGameTestInstance(function,
                    new TestData<>(environment, FixtureLayout.STRUCTURE, 20, 0, true)));
        }
    }

    private static ResourceKey<Consumer<GameTestHelper>> layoutFunction(FixtureLayout.Entry entry) {
        return function("placed_" + entry.name());
    }

    private static ResourceKey<Consumer<GameTestHelper>> function(String name) {
        return ResourceKey.create(Registries.TEST_FUNCTION, id(name));
    }

    static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NAMESPACE, path);
    }
}
