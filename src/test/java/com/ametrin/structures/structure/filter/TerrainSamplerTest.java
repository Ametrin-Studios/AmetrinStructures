package com.ametrin.structures.structure.filter;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(EphemeralTestServerProvider.class)
class TerrainSamplerTest {
    @Test
    void heightsReadFromTheColumnMatchTheGenerators(MinecraftServer server) {
        var generation = overworld(server);
        var fromGenerator = new TerrainSampler(generation, Heightmap.Types.WORLD_SURFACE_WG);
        var fromColumn = new TerrainSampler(generation, Heightmap.Types.WORLD_SURFACE_WG);
        var underWater = 0;
        for (int x = -2048; x <= 2048; x += 256) {
            for (int z = -2048; z <= 2048; z += 256) {
                for (var type : new Heightmap.Types[]{Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG}) {
                    assertEquals(fromGenerator.surfaceHeight(type, x, z), fromColumn.columnHeight(type, x, z), type + " at " + x + ", " + z);
                }
                if (fromColumn.columnHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) > fromColumn.columnHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z)) {
                    underWater++;
                }
            }
        }
        assertTrue(underWater > 0, "some spots should be under water, where the two heightmaps differ");
    }

    private static Structure.GenerationContext overworld(MinecraftServer server) {
        var registries = server.registryAccess();
        var plains = registries.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
        var settings = registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        return new Structure.GenerationContext(
                registries,
                new NoiseBasedChunkGenerator(new FixedBiomeSource(plains), settings),
                new FixedBiomeSource(plains),
                RandomState.create(registries, NoiseGeneratorSettings.OVERWORLD, 0),
                server.getStructureManager(),
                0,
                new ChunkPos(0, 0),
                LevelHeightAccessor.create(-64, 384),
                _ -> true);
    }
}
