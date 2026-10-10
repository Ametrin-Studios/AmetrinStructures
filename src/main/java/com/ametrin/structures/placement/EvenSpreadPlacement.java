package com.ametrin.structures.placement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Optional;

/// Spreads structures evenly without a visible grid: no two are closer than `min_distance` chunks,
/// and on average they are about 1.3 times that apart. `probability` thins them further.
public class EvenSpreadPlacement extends RandomSpreadStructurePlacement { // extends RandomSpreadStructurePlacement because /locate and similar have no generic case
    public static final int MAX_DISTANCE = 4096;

    public static final MapCodec<EvenSpreadPlacement> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Vec3i.CODEC.optionalFieldOf("locate_offset", Vec3i.ZERO).forGetter(EvenSpreadPlacement::locateOffset),
                    Codec.floatRange(0.0F, 1.0F).optionalFieldOf("probability", 1.0F).forGetter(EvenSpreadPlacement::frequency),
                    Codec.INT.optionalFieldOf("salt", 0).forGetter(EvenSpreadPlacement::salt),
                    TagExclusionZone.CODEC.optionalFieldOf("exclusion_zone").forGetter(EvenSpreadPlacement::structureExclusionZone),
                    Codec.intRange(1, MAX_DISTANCE).fieldOf("min_distance").forGetter(EvenSpreadPlacement::minDistance),
                    ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("min_chunks_from_center", 0).forGetter(EvenSpreadPlacement::minChunksFromCenter))
            .apply(instance, EvenSpreadPlacement::new));

    private final Optional<TagExclusionZone> tagExclusionZone;
    private final int minDistance;
    private final int minChunksFromCenter;
    private final int reach;

    // One candidate per cell. A candidate is dropped if a higher-priority candidate that isn't dropped itself is closer than minDistance (Matérn type III).
    public EvenSpreadPlacement(
            Vec3i locateOffset,
            float probability,
            int salt,
            Optional<TagExclusionZone> exclusionZone,
            int minDistance,
            int minChunksFromCenter) {
        // Small enough cells that the thinning doesn't leave gaps.
        int cell = Math.max(1, minDistance / 2);
        super(locateOffset, FrequencyReductionMethod.DEFAULT, probability, salt, Optional.empty(), cell, 0, RandomSpreadType.LINEAR);
        this.tagExclusionZone = exclusionZone;
        this.minDistance = minDistance;
        this.minChunksFromCenter = minChunksFromCenter;
        // Spots d cells apart are at least d * cell - (cell - 1) chunks apart, so farther cells can't come within minDistance.
        this.reach = (minDistance + cell - 2) / cell;
    }

    public int minDistance() {
        return minDistance;
    }

    public int minChunksFromCenter() {
        return minChunksFromCenter;
    }

    public boolean isTooCloseToCenter(int chunkX, int chunkZ) {
        return StructurePlacements.isTooCloseToCenter(chunkX, chunkZ, minChunksFromCenter);
    }

    public Optional<TagExclusionZone> structureExclusionZone() {
        return tagExclusionZone;
    }

    @Override
    public boolean applyAdditionalChunkRestrictions(int x, int z, long seed) {
        return !isTooCloseToCenter(x, z)
                && survives(seed, Math.floorDiv(x, spacing()), Math.floorDiv(z, spacing()), new Long2BooleanOpenHashMap())
                && super.applyAdditionalChunkRestrictions(x, z, seed);
    }

    @Override
    public boolean applyInteractionsWithOtherStructures(ChunkGeneratorStructureState state, int x, int z) {
        return tagExclusionZone.isEmpty() || !tagExclusionZone.get().isForbidden(state, x, z, this);
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

    // RandomSpreadStructurePlacement declares its codec with its own type, so this one has to be cast to it.
    @Override
    @SuppressWarnings("unchecked")
    public MapCodec<RandomSpreadStructurePlacement> codec() {
        return (MapCodec<RandomSpreadStructurePlacement>) (MapCodec<?>) CODEC;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(int minDistance, float probability) {
        return new Builder().minDistance(minDistance).probability(probability);
    }

    public static class Builder {
        private Vec3i locateOffset = Vec3i.ZERO;
        private float probability = 1.0F;
        @Nullable
        private Integer salt;
        private Optional<TagExclusionZone> exclusionZone = Optional.empty();
        private int minDistance = 12;
        private int minChunksFromCenter = 0;

        public Builder locateOffset(Vec3i locateOffset) {
            this.locateOffset = locateOffset;
            return this;
        }

        public Builder probability(float probability) {
            this.probability = probability;
            return this;
        }

        public Builder salt(int salt) {
            this.salt = salt;
            return this;
        }

        public Builder salt(String namespace, String id) {
            this.salt = StructurePlacements.salt(namespace, id);
            return this;
        }

        public Builder exclusionZone(TagKey<Structure> structures, int chunkCount) {
            this.exclusionZone = Optional.of(new TagExclusionZone(structures, chunkCount));
            return this;
        }

        public Builder minDistance(int minDistance) {
            this.minDistance = minDistance;
            return this;
        }

        public Builder minChunksFromCenter(int minChunksFromCenter) {
            this.minChunksFromCenter = minChunksFromCenter;
            return this;
        }

        public EvenSpreadPlacement build() {
            return build(salt == null ? 0 : salt);
        }

        /// salt defaults to one derived from `id`.
        public EvenSpreadPlacement build(Identifier id) {
            return build(id.getNamespace(), id.getPath());
        }

        /// salt defaults to one derived from `namespace` and `id`.
        public EvenSpreadPlacement build(String namespace, String id) {
            return build(salt == null ? StructurePlacements.salt(namespace, id) : salt);
        }

        private EvenSpreadPlacement build(int salt) {
            if (minDistance < 1 || minDistance > MAX_DISTANCE) {
                throw new IllegalStateException("even spread placement: min distance " + minDistance + " is outside 1 to " + MAX_DISTANCE);
            }
            if (probability < 0.0F || probability > 1.0F) {
                throw new IllegalStateException("even spread placement: probability " + probability + " is outside 0 to 1");
            }
            if (minChunksFromCenter < 0) {
                throw new IllegalStateException("even spread placement: min chunks from center " + minChunksFromCenter + " is negative");
            }
            exclusionZone.ifPresent(zone -> zone.validate("even spread placement"));
            return new EvenSpreadPlacement(
                    locateOffset,
                    probability,
                    salt,
                    exclusionZone,
                    minDistance,
                    minChunksFromCenter);
        }
    }
}
