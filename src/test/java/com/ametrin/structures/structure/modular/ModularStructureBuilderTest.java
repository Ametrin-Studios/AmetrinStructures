package com.ametrin.structures.structure.modular;

import com.ametrin.structures.structure.StructureBootstrap;
import com.ametrin.structures.structure.StructureSetBuilder;
import com.ametrin.structures.structure.StructureSetKeys;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.RegistryPatchGenerator;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/// Every one of these is a build-time failure with a message naming the structure. Deferring them to
/// generation time is what makes a misconfigured structure look like a worldgen bug.
class ModularStructureBuilderTest {
    private static final String ID = "tower";
    private static final Identifier TEMPLATE = Identifier.fromNamespaceAndPath("test", "tower");

    private static StructureSetKeys register(Consumer<StructureSetBuilder> configure) {
        return new StructureBootstrap("test").registerSet(ID, configure);
    }

    @Test
    void invalidSetsFailNamingTheStructure() {
        assertNamedFailure(() -> register(set -> set.modular(tower -> tower.surface())), "no piece source");
        assertNamedFailure(() -> register(set -> {}), "no structure");
        assertNamedFailure(() -> register(set -> set
                .modular(tower -> tower.single(b -> b.template(TEMPLATE)))
                .modular(tower -> tower.single(b -> b.template(TEMPLATE)))), "the same structure twice");
        assertNamedFailure(() -> register(set -> set.modular("tower", 0, tower -> tower.single(b -> b.template(TEMPLATE)))), "weight 0");
        assertNamedFailure(() -> register(set -> set
                .scatteredGridPlacement(24, 1.5F)
                .modular(tower -> tower.single(b -> b.template(TEMPLATE)))), "probability above 1");
        assertNamedFailure(() -> register(set -> set
                .scatteredGridPlacement(24, -0.1F)
                .modular(tower -> tower.single(b -> b.template(TEMPLATE)))), "negative probability");
        assertNamedFailure(() -> register(set -> set
                .scatteredGridPlacement(-4)
                .modular(tower -> tower.single(b -> b.template(TEMPLATE)))), "negative spacing");
        assertNamedFailure(() -> register(set -> set.modular(tower -> tower.single(b -> b.template(TEMPLATE)).filterFlatness(-1))), "an invalid filter");
        var structures = new StructureBootstrap("test");
        structures.registerSet(ID, set -> set.modular(tower -> tower.single("tower")));
        assertNamedFailure(() -> structures.registerSet(ID, set -> set.modular(tower -> tower.single("tower"))), "the same set twice");
    }

    @Test
    void aStructureBuildsOutsideASet() {
        var key = ResourceKey.create(Registries.STRUCTURE, TEMPLATE);
        var standalone = new RegistrySetBuilder().add(Registries.STRUCTURE, context ->
                context.register(key, ModularStructure.builder(TEMPLATE).single("tower").filterFlatness(2).build(context)));
        var registries = RegistryPatchGenerator.createWorldLookup(CompletableFuture.completedFuture(VanillaRegistries.createWorldLookup()), standalone)
                .join()
                .full();
        var structure = (ModularStructure) registries.lookupOrThrow(Registries.STRUCTURE).getOrThrow(key).value();
        assertEquals(1, structure.filters().size());
    }

    @Test
    void theKeysNameEveryStructure() {
        var keys = register(set -> set
                .modular("small", 1, tower -> tower.single("tower"))
                .modular("large", 1, tower -> tower.single("tower")));
        assertEquals("test:tower", keys.key().identifier().toString());
        assertEquals("test:tower/large", keys.structure("large").identifier().toString());
        assertEquals(List.of("small", "large"), List.copyOf(keys.declared().keySet()));
        assertThrows(IllegalArgumentException.class, () -> keys.structure("medium"));
        assertThrows(IllegalStateException.class, keys::structure);
    }

    private static void assertNamedFailure(Runnable action, String what) {
        var failure = assertThrows(IllegalStateException.class, action::run, what);
        assertTrue(failure.getMessage().contains(ID), what + ": the message should name the structure, was: " + failure.getMessage());
    }
}
