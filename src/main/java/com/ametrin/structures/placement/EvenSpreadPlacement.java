package com.ametrin.structures.placement;

import com.ametrin.structures.registry.ASPlacementTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Optional;

/// Spreads structures evenly without a visible grid: no two are closer than `min_distance` chunks,
/// and on average they are about 1.3 times that apart. `probability` thins them further.
public class EvenSpreadPlacement extends ExtendedSpreadPlacement {
    public static final int MAX_DISTANCE = 4096;

    public static final MapCodec<EvenSpreadPlacement> CODEC = RecordCodecBuilder.<EvenSpreadPlacement>mapCodec(instance -> spreadCodec(instance)
                    .and(Codec.INT.fieldOf("min_distance").forGetter(EvenSpreadPlacement::minDistance))
                    .apply(instance, EvenSpreadPlacement::new))
            .validate(EvenSpreadPlacement::validate);

    private final int minDistance;
    private final int reach;

    // One candidate per cell. A candidate is dropped if a higher-priority candidate that isn't dropped itself is closer than minDistance (Matérn type III).
    public EvenSpreadPlacement(Vec3i locateOffset, float probability, int salt, Optional<TagExclusionZone> exclusionZone, int minChunksFromCenter, int minDistance) {
        // Small enough cells that the thinning doesn't leave gaps.
        int cell = Math.max(1, minDistance / 2);
        super(locateOffset, probability, salt, exclusionZone, minChunksFromCenter, cell, 0);
        this.minDistance = minDistance;
        // Spots d cells apart are at least d * cell - (cell - 1) chunks apart, so farther cells can't come within minDistance.
        this.reach = (minDistance + cell - 2) / cell;
    }

    @Override
    protected Optional<String> problem() {
        if (minDistance < 1 || minDistance > MAX_DISTANCE) {
            return Optional.of("min_distance " + minDistance + " is outside 1 to " + MAX_DISTANCE);
        }
        return super.problem();
    }

    public int minDistance() {
        return minDistance;
    }

    @Override
    public boolean applyAdditionalChunkRestrictions(int x, int z, long seed) {
        return super.applyAdditionalChunkRestrictions(x, z, seed) && survives(seed, Math.floorDiv(x, spacing()), Math.floorDiv(z, spacing()), new Long2BooleanOpenHashMap());
    }

    private boolean survives(long seed, int cellX, int cellZ, Long2BooleanOpenHashMap known) {
        long key = ChunkPos.pack(cellX, cellZ);
        if (known.containsKey(key)) {
            return known.get(key);
        }
        var candidate = candidate(seed, cellX, cellZ);
        boolean survives = !isTooCloseToCenter(candidate.x(), candidate.z());
        if (survives) {
            long priority = priority(seed, cellX, cellZ);
            var rivals = new ArrayList<Rival>();
            for (int rivalX = cellX - reach; rivalX <= cellX + reach; rivalX++) {
                for (int rivalZ = cellZ - reach; rivalZ <= cellZ + reach; rivalZ++) {
                    long rivalPriority = priority(seed, rivalX, rivalZ);
                    if (rivalPriority > priority) {
                        rivals.add(new Rival(rivalPriority, rivalX, rivalZ));
                    }
                }
            }
            rivals.sort(Comparator.comparingLong(Rival::priority).reversed());
            for (var rival : rivals) {
                if (isCloserThanMinDistance(candidate, candidate(seed, rival.cellX(), rival.cellZ()))
                        && survives(seed, rival.cellX(), rival.cellZ(), known)) {
                    survives = false;
                    break;
                }
            }
        }
        known.put(key, survives);
        return survives;
    }

    private record Rival(long priority, int cellX, int cellZ) {}

    private ChunkPos candidate(long seed, int cellX, int cellZ) {
        return getPotentialStructureChunk(seed, cellX * spacing(), cellZ * spacing());
    }

    private long priority(long seed, int cellX, int cellZ) {
        return HashCommon.mix(seed ^ HashCommon.mix(ChunkPos.pack(cellX, cellZ) ^ HashCommon.mix((long) salt())));
    }

    private boolean isCloserThanMinDistance(ChunkPos a, ChunkPos b) {
        long dx = a.x() - b.x();
        long dz = a.z() - b.z();
        return dx * dx + dz * dz < (long) minDistance * minDistance;
    }

    @Override
    public StructurePlacementType<?> type() {
        return ASPlacementTypes.EVEN_SPREAD.get();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(int minDistance, float probability) {
        return new Builder().minDistance(minDistance).probability(probability);
    }

    public static class Builder extends ExtendedSpreadPlacement.Builder<Builder, EvenSpreadPlacement> {
        private int minDistance = 12;

        public Builder minDistance(int minDistance) {
            this.minDistance = minDistance;
            return this;
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        protected EvenSpreadPlacement create(Vec3i locateOffset, float probability, int salt, Optional<TagExclusionZone> exclusionZone, int minChunksFromCenter) {
            return new EvenSpreadPlacement(locateOffset, probability, salt, exclusionZone, minChunksFromCenter, minDistance);
        }
    }
}
