package com.ametrin.structures.structure.filter;

import com.ametrin.structures.structure.GenerationContexts;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
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
        var plains = new FixedBiomeSource(registries.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS));
        var settings = registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        return GenerationContexts.create(server, new NoiseBasedChunkGenerator(plains, settings), plains, 0, _ -> true);
    }
}
