package com.ametrin.structures.structure.simple;

import com.ametrin.structures.structure.DeferredStructureRegister;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.RegistryPatchGenerator;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/// Every one of these is a build-time failure with a message naming the structure. Deferring them to
/// generation time is what makes a misconfigured structure look like a worldgen bug.
class SimpleStructureBuilderTest {
    private static final String ID = "tower";
    private static final Identifier TEMPLATE = Identifier.fromNamespaceAndPath("test", "tower");

    private static final DeferredStructureRegister REGISTER = new DeferredStructureRegister("test");

    @Test
    void missingPieceSourceFails() {
        assertNamedFailure(() -> REGISTER.set(ID).simple(tower -> tower.surface()).build());
    }

    @Test
    void emptySetFails() {
        assertNamedFailure(() -> REGISTER.set(ID).build());
    }

    @Test
    void duplicateStructureFails() {
        assertNamedFailure(() -> REGISTER.set(ID)
                .simple(tower -> tower.single(b -> b.template(TEMPLATE)))
                .simple(tower -> tower.single(b -> b.template(TEMPLATE))));
    }

    @Test
    void weightBelowOneFails() {
        assertNamedFailure(() -> REGISTER.set(ID).simple(tower -> tower.single(b -> b.template(TEMPLATE)).weight(0)).build());
    }

    @Test
    void probabilityOutsideZeroToOneFails() {
        assertNamedFailure(() -> REGISTER.set(ID)
                .scatteredGridPlacement(24, 1.5F)
                .simple(tower -> tower.single(b -> b.template(TEMPLATE)))
                .build());
        assertNamedFailure(() -> REGISTER.set(ID)
                .scatteredGridPlacement(24, -0.1F)
                .simple(tower -> tower.single(b -> b.template(TEMPLATE)))
                .build());
    }

    @Test
    void negativeSpacingFails() {
        assertNamedFailure(() -> REGISTER.set(ID)
                .scatteredGridPlacement(-4)
                .simple(tower -> tower.single(b -> b.template(TEMPLATE)))
                .build());
    }

    @Test
    void anInvalidFilterFails() {
        assertNamedFailure(() -> REGISTER.set(ID).simple(tower -> tower.single(b -> b.template(TEMPLATE)).filterFlatness(-1)));
    }

    @Test
    void filtersOnAPlainCustomStructureFail() {
        assertNamedFailure(() -> REGISTER.set(ID).structure((settings, context) -> null, custom -> custom.filterFlatness(2)).build());
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
    void theCommonCaseBuilds() {
        assertDoesNotThrow(() -> REGISTER.set(ID)
                .scatteredGridPlacement(24, 0.6F)
                .simple(tower -> tower.single(b -> b.template(TEMPLATE)).surface())
                .build());
    }

    private static void assertNamedFailure(Runnable action) {
        var failure = assertThrows(IllegalStateException.class, action::run);
        assertTrue(failure.getMessage().contains(ID), "message should name the structure, was: " + failure.getMessage());
    }
}
