package com.ametrin.structures.structure.simple;

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
class SimpleStructureBuilderTest {
    private static final String ID = "tower";
    private static final Identifier TEMPLATE = Identifier.fromNamespaceAndPath("test", "tower");

    private static StructureSetKeys register(Consumer<StructureSetBuilder> configure) {
        return new StructureBootstrap("test").registerSet(ID, configure);
    }

    @Test
    void missingPieceSourceFails() {
        assertNamedFailure(() -> register(set -> set.simple(tower -> tower.surface())));
    }

    @Test
    void emptySetFails() {
        assertNamedFailure(() -> register(set -> {}));
    }

    @Test
    void duplicateStructureFails() {
        assertNamedFailure(() -> register(set -> set
                .simple(tower -> tower.single(b -> b.template(TEMPLATE)))
                .simple(tower -> tower.single(b -> b.template(TEMPLATE)))));
    }

    @Test
    void weightBelowOneFails() {
        assertNamedFailure(() -> register(set -> set.simple(tower -> tower.single(b -> b.template(TEMPLATE)).weight(0))));
    }

    @Test
    void probabilityOutsideZeroToOneFails() {
        assertNamedFailure(() -> register(set -> set
                .scatteredGridPlacement(24, 1.5F)
                .simple(tower -> tower.single(b -> b.template(TEMPLATE)))));
        assertNamedFailure(() -> register(set -> set
                .scatteredGridPlacement(24, -0.1F)
                .simple(tower -> tower.single(b -> b.template(TEMPLATE)))));
    }

    @Test
    void negativeSpacingFails() {
        assertNamedFailure(() -> register(set -> set
                .scatteredGridPlacement(-4)
                .simple(tower -> tower.single(b -> b.template(TEMPLATE)))));
    }

    @Test
    void anInvalidFilterFails() {
        assertNamedFailure(() -> register(set -> set.simple(tower -> tower.single(b -> b.template(TEMPLATE)).filterFlatness(-1))));
    }

    @Test
    void filtersOnAPlainCustomStructureFail() {
        assertNamedFailure(() -> register(set -> set.structure((settings, context) -> null, custom -> custom.filterFlatness(2))));
    }

    @Test
    void aStructureBuildsOutsideASet() {
        var key = ResourceKey.create(Registries.STRUCTURE, TEMPLATE);
        var standalone = new RegistrySetBuilder().add(Registries.STRUCTURE, context ->
                context.register(key, SimpleStructure.builder(TEMPLATE).single("tower").filterFlatness(2).build(context)));
        var registries = RegistryPatchGenerator.createLookup(CompletableFuture.completedFuture(VanillaRegistries.createLookup()), standalone)
                .join()
                .full();
        var structure = (SimpleStructure) registries.lookupOrThrow(Registries.STRUCTURE).getOrThrow(key).value();
        assertEquals(1, structure.filters().size());
    }

    @Test
    void registeringASetTwiceFails() {
        var structures = new StructureBootstrap("test");
        structures.registerSet(ID, set -> set.simple(tower -> tower.single("tower")));
        assertNamedFailure(() -> structures.registerSet(ID, set -> set.simple(tower -> tower.single("tower"))));
    }

    @Test
    void theKeysNameEveryStructure() {
        var keys = register(set -> set
                .simple("small", tower -> tower.single("tower"))
                .simple("large", tower -> tower.single("tower")));
        assertEquals("test:tower", keys.key().identifier().toString());
        assertEquals("test:tower/large", keys.structure("large").identifier().toString());
        assertEquals(List.of("small", "large"), List.copyOf(keys.structures().keySet()));
        assertThrows(IllegalArgumentException.class, () -> keys.structure("medium"));
        assertThrows(IllegalStateException.class, keys::structure);
    }

    @Test
    void theCommonCaseBuilds() {
        assertDoesNotThrow(() -> register(set -> set
                .scatteredGridPlacement(24, 0.6F)
                .simple(tower -> tower.single(b -> b.template(TEMPLATE)).surface())));
    }

    private static void assertNamedFailure(Runnable action) {
        var failure = assertThrows(IllegalStateException.class, action::run);
        assertTrue(failure.getMessage().contains(ID), "message should name the structure, was: " + failure.getMessage());
    }
}
