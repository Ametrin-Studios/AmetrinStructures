package com.ametrin.structures.debug;

import com.ametrin.structures.placement.EvenSpreadPlacement;
import com.ametrin.structures.placement.ScatteredGridPlacement;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.CheckerboardColumnBiomeSource;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BuiltinStructureSets;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class StructureCandidatesTest {
    private static final ChunkPos CENTER = new ChunkPos(-37, 81);
    private static final int RADIUS = 300;

    @Test
    void findsWhatScanningEveryChunkFinds(MinecraftServer server) {
        var state = state(server);
        var placements = List.of(
                server.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).getOrThrow(BuiltinStructureSets.VILLAGES).value().placement(),
                ScatteredGridPlacement.builder(23, 0.5F).salt(1).build(),
                EvenSpreadPlacement.builder(40, 0.8F).salt(2).build(),
                everySeventhChunk());
        for (var placement : placements) {
            var expected = new HashSet<ChunkPos>();
            for (int x = CENTER.x() - RADIUS; x <= CENTER.x() + RADIUS; x++) {
                for (int z = CENTER.z() - RADIUS; z <= CENTER.z() + RADIUS; z++) {
                    var chunk = new ChunkPos(x, z);
                    if (chunk.distanceSquared(CENTER) <= RADIUS * RADIUS && placement.isStructureChunk(state, x, z)) {
                        expected.add(chunk);
                    }
                }
            }
            var found = new ArrayList<ChunkPos>();
            new StructureCandidates<>(state, List.of(placement), Function.identity(), CENTER, RADIUS).forEachRemaining(candidate -> found.add(candidate.chunk()));

            assertFalse(expected.isEmpty(), placement.toString());
            assertEquals(expected, new HashSet<>(found), placement.toString());
            assertEquals(expected.size(), found.size(), "each chunk once");
            for (int i = 1; i < found.size(); i++) {
                assertTrue(found.get(i - 1).distanceSquared(CENTER) <= found.get(i).distanceSquared(CENTER), "nearest first");
            }
        }
    }

    // Neither a random spread nor concentric rings, so every chunk is checked.
    private static StructurePlacement everySeventhChunk() {
        return new StructurePlacement() {
            @Override
            public boolean isStructureChunk(ChunkGeneratorStructureState state, int x, int z) {
                return Math.floorMod(x + 3 * z, 7) == 0;
            }

            @Override
            public MapCodec<? extends StructurePlacement> codec() {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static ChunkGeneratorStructureState state(MinecraftServer server) {
        var registries = server.registryAccess();
        var biomes = registries.lookupOrThrow(Registries.BIOME);
        var biomeSource = new CheckerboardColumnBiomeSource(HolderSet.direct(biomes.getOrThrow(Biomes.PLAINS)), 2);
        var settings = registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
        var randomState = RandomState.create(registries.lookupOrThrow(Registries.NOISE), 0, settings);
        return ChunkGeneratorStructureState.createForNormal(randomState, 0, new ChunkPos(0, 0), biomeSource, registries.lookupOrThrow(Registries.STRUCTURE_SET));
    }
}
