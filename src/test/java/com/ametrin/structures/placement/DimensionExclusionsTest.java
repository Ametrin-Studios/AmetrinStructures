package com.ametrin.structures.placement;

import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.CheckerboardColumnBiomeSource;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BuiltinStructureSets;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

/// The test data map excludes the igloos from a dimension that doesn't exist.
@ExtendWith(EphemeralTestServerProvider.class)
class DimensionExclusionsTest {
    private static final ResourceKey<Level> VOID = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("ametrin_structures", "test_void"));

    @Test
    void excludedSetsAreLeftOut(MinecraftServer server) {
        var state = state(server);
        DimensionExclusions.apply(state, VOID);

        var sets = server.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET);
        assertFalse(state.possibleStructureSets().contains(sets.getOrThrow(BuiltinStructureSets.IGLOOS)));
        assertTrue(state.possibleStructureSets().contains(sets.getOrThrow(BuiltinStructureSets.VILLAGES)));
        assertTrue(state.getPlacementsForStructure(server.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(BuiltinStructures.IGLOO)).isEmpty());
    }

    @Test
    void otherDimensionsKeepEverySet(MinecraftServer server) {
        var state = state(server);
        var before = state.possibleStructureSets();
        DimensionExclusions.apply(state, Level.NETHER);
        assertEquals(before, state.possibleStructureSets());
    }

    private static ChunkGeneratorStructureState state(MinecraftServer server) {
        var registries = server.registryAccess();
        var biomes = registries.lookupOrThrow(Registries.BIOME);
        var biomeSource = new CheckerboardColumnBiomeSource(
                HolderSet.direct(biomes.getOrThrow(Biomes.PLAINS), biomes.getOrThrow(Biomes.SNOWY_PLAINS)), 2);
        var settings = registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
        var randomState = RandomState.create(registries.lookupOrThrow(Registries.NOISE), 0, settings);
        return ChunkGeneratorStructureState.createForNormal(randomState, 0, new ChunkPos(0, 0), biomeSource, registries.lookupOrThrow(Registries.STRUCTURE_SET));
    }
}
