package com.ametrin.structures.structure;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.MultiRegistryBootstrap;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.SingleRegistryBootstrap;
import net.minecraft.data.registries.RegistryPatchGenerator;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.data.worldgen.Pools;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.neoforged.neoforge.data.event.DatapackRegistryGatherer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/// Sets can take structures registered elsewhere, and structures can be registered without a set.
class StructureBootstrapTest {
    @Test
    void aSetTakesVanillaStructures() {
        var structures = new StructureBootstrap("test");
        var keys = structures.registerSet("mixed", set -> set
                .simple(tower -> tower.single("tower"))
                .existing(BuiltinStructures.IGLOO, 3));
        assertEquals(Map.of(keys.structure(""), 1, BuiltinStructures.IGLOO, 3), weights(build(structures), keys.key()));
        assertEquals(List.of(BuiltinStructures.IGLOO), keys.existing());
        assertEquals(List.of(keys.structure(""), BuiltinStructures.IGLOO), keys.structures());
        assertThrows(IllegalStateException.class, keys::structure);
    }

    @Test
    void theOnlyStructureCanBeAnExistingOne() {
        var keys = new StructureBootstrap("test").registerSet("igloos", set -> set.existing(BuiltinStructures.IGLOO));
        assertEquals(BuiltinStructures.IGLOO, keys.structure());
    }

    @Test
    void aStructureWithoutASetCanJoinOneLater() {
        var structures = new StructureBootstrap("test");
        var tower = structures.registerSimple("tower", builder -> builder.single("tower"));
        var shrine = structures.registerJigsaw("shrine", Pools.EMPTY, jigsaw -> jigsaw.size(1), _ -> {});
        var keys = structures.registerSet("towers", set -> set.existing(tower).existing(shrine, 2));

        var registries = build(structures);
        assertTrue(registries.lookupOrThrow(Registries.STRUCTURE).get(tower).isPresent());
        assertEquals(Map.of(tower, 1, shrine, 2), weights(registries, keys.key()));
        assertTrue(keys.declared().isEmpty());
        assertEquals(List.of(tower, shrine), keys.structures());
    }

    @Test
    void aNameIsTakenOnceAcrossSetsAndStructures() {
        var structures = new StructureBootstrap("test");
        structures.registerSimple("tower", builder -> builder.single("tower"));
        assertThrows(IllegalStateException.class, () -> structures.registerSet("tower", set -> set.simple(tower -> tower.single("tower"))));
        assertThrows(IllegalStateException.class, () -> structures.registerSimple("tower", builder -> builder.single("tower")));
    }

    @Test
    void existingStructuresAreChecked() {
        var structures = new StructureBootstrap("test");
        assertThrows(IllegalStateException.class, () -> structures.registerSet("zero", set -> set.existing(BuiltinStructures.IGLOO, 0)));
        assertThrows(IllegalStateException.class, () -> structures.registerSet("twice", set -> set
                .existing(BuiltinStructures.IGLOO)
                .existing(BuiltinStructures.IGLOO)));
        assertThrows(IllegalStateException.class, () -> structures.registerSet("own", set -> set
                .simple(tower -> tower.single("tower"))
                .existing(ResourceKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath("test", "own")))));
    }

    @Test
    void aStructureWithoutASetIsCheckedRightAway() {
        assertThrows(IllegalStateException.class, () -> new StructureBootstrap("test").registerSimple("tower", builder -> builder.surface()));
    }

    private static HolderLookup.Provider build(StructureBootstrap structures) {
        var builder = new RegistrySetBuilder();
        structures.addTo(new DatapackRegistryGatherer() {
            @Override
            public <T> DatapackRegistryGatherer add(ResourceKey<? extends Registry<T>> registryKey, SingleRegistryBootstrap<T> bootstrap) {
                builder.add(registryKey, bootstrap);
                return this;
            }

            @Override
            public DatapackRegistryGatherer add(MultiRegistryBootstrap bootstrap) {
                builder.add(bootstrap);
                return this;
            }
        });
        return RegistryPatchGenerator.createWorldLookup(CompletableFuture.completedFuture(VanillaRegistries.createWorldLookup()), builder).join().full();
    }

    private static Map<ResourceKey<Structure>, Integer> weights(HolderLookup.Provider registries, ResourceKey<StructureSet> set) {
        return registries.lookupOrThrow(Registries.STRUCTURE_SET).getOrThrow(set).value().structures().stream()
                .collect(Collectors.toMap(entry -> entry.structure().unwrapKey().orElseThrow(), StructureSet.StructureSelectionEntry::weight));
    }
}
