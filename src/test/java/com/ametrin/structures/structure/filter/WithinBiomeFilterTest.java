package com.ametrin.structures.structure.filter;

import com.ametrin.structures.structure.GenerationContexts;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.levelgen.DebugLevelSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/// The whole box has to sit in the allowed biomes. The world here alternates plains and desert every
/// chunk along X, so X 0..15 is plains and 16..31 desert.
@ExtendWith(EphemeralTestServerProvider.class)
class WithinBiomeFilterTest {
    @Test
    void passesWhenTheBoxStaysInItsBiome(MinecraftServer server) {
        assertTrue(test(server, new PlacementFilters.WithinBiome(Optional.empty(), 0), box(2, 13)));
    }

    @Test
    void failsWhenTheBoxReachesIntoAnotherBiome(MinecraftServer server) {
        assertFalse(test(server, new PlacementFilters.WithinBiome(Optional.empty(), 0), box(2, 20)));
    }

    @Test
    void theMarginWidensTheCheckedArea(MinecraftServer server) {
        assertFalse(test(server, new PlacementFilters.WithinBiome(Optional.empty(), 4), box(2, 13)));
    }

    @Test
    void anExplicitListAllowsMoreThanTheStructuresBiomes(MinecraftServer server) {
        var both = HolderSet.direct(biome(server, Biomes.PLAINS), biome(server, Biomes.DESERT));
        assertTrue(test(server, new PlacementFilters.WithinBiome(Optional.of(both), 0), box(2, 20)));
    }

    @Test
    void checksEveryColumnAtTheBottomAndTopLayer(MinecraftServer server) {
        var source = new RecordingSource(biome(server, Biomes.PLAINS), biome(server, Biomes.DESERT), null);
        // Quarts X 0..7, Z 0..4, Y 15..17.
        assertTrue(test(server, new PlacementFilters.WithinBiome(Optional.empty(), 0), new BoundingBox(0, 60, 0, 31, 70, 19), source));
        assertEquals(80, source.sampled.size());
        assertEquals(40, source.sampled.stream().map(cell -> cell.getX() + "," + cell.getZ()).distinct().count());
        assertEquals(Set.of(15, 17), source.sampled.stream().map(BlockPos::getY).collect(Collectors.toSet()));
    }

    @Test
    void findsAPocketInTheMiddle(MinecraftServer server) {
        var source = new RecordingSource(biome(server, Biomes.PLAINS), biome(server, Biomes.DESERT), new BlockPos(4, 15, 2));
        assertFalse(test(server, new PlacementFilters.WithinBiome(Optional.empty(), 0), new BoundingBox(0, 60, 0, 31, 70, 19), source));
    }

    /// Plains everywhere except `pocket`, a quart cell of desert; remembers every cell asked for.
    private static final class RecordingSource extends BiomeSource implements BiomeResolver {
        private final Holder<Biome> plains;
        private final Holder<Biome> desert;
        private final @Nullable BlockPos pocket;
        final List<BlockPos> sampled = new ArrayList<>();

        RecordingSource(Holder<Biome> plains, Holder<Biome> desert, @Nullable BlockPos pocket) {
            this.plains = plains;
            this.desert = desert;
            this.pocket = pocket;
        }

        @Override
        protected MapCodec<? extends BiomeSource> codec() {
            throw new UnsupportedOperationException();
        }

        @Override
        protected Stream<Holder<Biome>> collectPossibleBiomes() {
            return Stream.of(plains, desert);
        }

        @Override
        public BiomeResolver createResolver(Climate.Sampler sampler) {
            return this;
        }

        @Override
        public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ) {
            var cell = new BlockPos(quartX, quartY, quartZ);
            sampled.add(cell);
            return cell.equals(pocket) ? desert : plains;
        }
    }

    @Test
    void biomesAndMarginAreOptional(MinecraftServer server) {
        var ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        var filter = PlacementFilters.WithinBiome.CODEC.codec().parse(ops, JsonParser.parseString("{}")).getOrThrow();
        assertEquals(new PlacementFilters.WithinBiome(Optional.empty(), 0), filter);
    }

    private static BoundingBox box(int minX, int maxX) {
        return new BoundingBox(minX, 60, 2, maxX, 70, 12);
    }

    /// Runs `filter` against the checkerboard world, for a structure that only allows plains.
    private static boolean test(MinecraftServer server, PlacementFilter filter, BoundingBox footprint) {
        var plains = biome(server, Biomes.PLAINS);
        return test(server, filter, footprint, new CheckerboardColumnBiomeSource(HolderSet.direct(plains, biome(server, Biomes.DESERT)), 0));
    }

    private static boolean test(MinecraftServer server, PlacementFilter filter, BoundingBox footprint, BiomeSource source) {
        var generation = GenerationContexts.create(server, new DebugLevelSource(biome(server, Biomes.PLAINS)), source, 0, biome -> biome.is(Biomes.PLAINS));
        var context = new PlacementFilter.Context(
                generation, new BlockPos(footprint.minX(), footprint.minY(), footprint.minZ()), footprint,
                new TerrainSampler(generation, Heightmap.Types.WORLD_SURFACE_WG));
        return filter.test(context);
    }

    private static Holder.Reference<Biome> biome(MinecraftServer server, ResourceKey<Biome> key) {
        return server.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(key);
    }
}
