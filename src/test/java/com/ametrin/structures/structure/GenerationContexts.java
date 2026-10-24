package com.ametrin.structures.structure;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.DebugLevelSource;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.function.Predicate;

/// Generation contexts for chunk 0, 0 of an overworld-sized world.
public final class GenerationContexts {
    private GenerationContexts() {}

    /// On a debug generator, which has no terrain, over plains.
    public static Structure.GenerationContext overPlains(MinecraftServer server, long seed, Predicate<Holder<Biome>> validBiome) {
        var plains = server.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
        return create(server, new DebugLevelSource(plains), new FixedBiomeSource(plains), seed, validBiome);
    }

    public static Structure.GenerationContext create(
            MinecraftServer server, ChunkGenerator generator, BiomeSource biomes, long seed, Predicate<Holder<Biome>> validBiome) {
        return create(server, server.registryAccess(), generator, biomes, seed, validBiome);
    }

    /// @param registries what the structure sees, the noise settings still come from the server
    public static Structure.GenerationContext create(
            MinecraftServer server, RegistryAccess registries, ChunkGenerator generator, BiomeSource biomes, long seed, Predicate<Holder<Biome>> validBiome) {
        var serverRegistries = server.registryAccess();
        var settings = serverRegistries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
        var randomState = RandomState.create(serverRegistries.lookupOrThrow(Registries.NOISE), seed, settings);
        return new Structure.GenerationContext(
                registries,
                generator,
                biomes,
                randomState.createClimateSampler(SamplerContext.EMPTY_UNCACHED),
                randomState,
                server.getStructureTemplateManager(),
                seed,
                new ChunkPos(0, 0),
                LevelHeightAccessor.create(-64, 384),
                validBiome);
    }
}
