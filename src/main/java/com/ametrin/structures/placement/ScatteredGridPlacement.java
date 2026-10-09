package com.ametrin.structures.placement;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/// A grid-shaped placement: the world is cut into `spacing`-sized cells of chunks, one structure each.
///
/// - `grid_offset` shifts the whole grid. Left out, it is derived from the world seed and the salt, so structures sharing a spacing don't cluster at the same cell corners.
/// - `random_offset` moves each structure up to that many chunks from its cell corner (0 is a strict grid)
/// - `spread_type` picks how that move is rolled. `triangular`, the default, favors the middle of the range, so neighbors rarely end up next to each other; `linear` is uniform, as in vanilla's random spread
///
/// Also has an exclusion zone for a structure tag and a minimum distance from the world origin.
public class ScatteredGridPlacement extends RandomSpreadStructurePlacement { // extends RandomSpreadStructurePlacement because /locate and similar have no generic case
    // Bounded so a mistyped value fails at load rather than at generation.
    public static final int MAX_SPACING = 4096;

    private static final ThreadLocal<Boolean> CHECKING_EXCLUSION = ThreadLocal.withInitial(() -> false);

    public static final MapCodec<ScatteredGridPlacement> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Vec3i.CODEC.optionalFieldOf("locate_offset", Vec3i.ZERO).forGetter(ScatteredGridPlacement::locateOffset),
                    Codec.floatRange(0.0F, 1.0F)
                            .optionalFieldOf("probability", 1.0F)
                            .forGetter(ScatteredGridPlacement::frequency),
                    Codec.INT.fieldOf("salt").forGetter(ScatteredGridPlacement::salt),
                    ExclusionZone.CODEC.optionalFieldOf("exclusion_zone").forGetter(ScatteredGridPlacement::structureExclusionZone),
                    Codec.intRange(1, MAX_SPACING).fieldOf("spacing").forGetter(ScatteredGridPlacement::spacing),
                    GridOffset.CODEC.optionalFieldOf("grid_offset").forGetter(ScatteredGridPlacement::declaredGridOffset),
                    Codec.intRange(0, MAX_SPACING).fieldOf("random_offset").forGetter(ScatteredGridPlacement::randomOffset),
                    RandomSpreadType.CODEC.optionalFieldOf("spread_type", RandomSpreadType.TRIANGULAR).forGetter(ScatteredGridPlacement::spreadType),
                    ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("min_chunks_from_center", 0).forGetter(ScatteredGridPlacement::minChunksFromCenter))
            .apply(instance, ScatteredGridPlacement::new));

    private final Optional<ExclusionZone> tagExclusionZone;
    private final Optional<GridOffset> gridOffset;
    private final int randomOffset;
    private final int minChunksFromCenter;

    public ScatteredGridPlacement(
            Vec3i locateOffset,
            float probability,
            int salt,
            Optional<ExclusionZone> exclusionZone,
            int spacing,
            Optional<GridOffset> gridOffset,
            int randomOffset,
            RandomSpreadType spreadType,
            int minChunksFromCenter) {
        randomOffset = Mth.clamp(randomOffset, 0, spacing - 1);
        super(
                locateOffset,
                FrequencyReductionMethod.DEFAULT,
                probability,
                salt,
                Optional.empty(),
                spacing,
                spacing - 1 - randomOffset,
                spreadType);
        this.tagExclusionZone = exclusionZone;
        this.gridOffset = gridOffset.map(offset -> offset.within(spacing));
        this.randomOffset = randomOffset;
        this.minChunksFromCenter = minChunksFromCenter;
    }

    public GridOffset gridOffset(long seed) {
        return gridOffset.orElseGet(() -> {
            var random = RandomSource.create(seed ^ (long) salt() * 0x5DEECE66DL);
            return new GridOffset(random.nextInt(spacing()), random.nextInt(spacing()));
        });
    }

    /// Minimum distance from the world origin, in chunks
    public int minChunksFromCenter() {
        return minChunksFromCenter;
    }

    public boolean isTooCloseToCenter(int chunkX, int chunkZ) {
        return (long) chunkX * chunkX + (long) chunkZ * chunkZ < (long) minChunksFromCenter * minChunksFromCenter;
    }

    public Optional<ExclusionZone> structureExclusionZone() {
        return tagExclusionZone;
    }

    private Optional<GridOffset> declaredGridOffset() {
        return gridOffset;
    }

    /// How far, in chunks, a structure may move from its cell corner along each axis.
    public int randomOffset() {
        return randomOffset;
    }

    @Override
    public ChunkPos getPotentialStructureChunk(long seed, int x, int z) {
        var shift = gridOffset(seed);
        var inGrid = super.getPotentialStructureChunk(seed, x - shift.x(), z - shift.z());
        return new ChunkPos(inGrid.x() + shift.x(), inGrid.z() + shift.z());
    }

    @Override
    public boolean isStructureChunk(ChunkGeneratorStructureState state, int x, int z) {
        return super.isStructureChunk(state, x, z) && !isTooCloseToCenter(x, z) && !isExcluded(state, x, z);
    }

    // Placements checked for the exclusion zone skip their own exclusion zones. Placements that exclude each other would otherwise check each other forever.
    private boolean isExcluded(ChunkGeneratorStructureState state, int x, int z) {
        if (tagExclusionZone.isEmpty() || CHECKING_EXCLUSION.get()) {
            return false;
        }
        CHECKING_EXCLUSION.set(true);
        try {
            return tagExclusionZone.get().isForbidden(state, x, z, this);
        } finally {
            CHECKING_EXCLUSION.set(false);
        }
    }

    // RandomSpreadStructurePlacement declares its codec with its own type, so this one has to be cast to it.
    @Override
    @SuppressWarnings("unchecked")
    public MapCodec<RandomSpreadStructurePlacement> codec() {
        return (MapCodec<RandomSpreadStructurePlacement>) (MapCodec<?>) CODEC;
    }

    public record GridOffset(int x, int z) {
        private static final Codec<Integer> VALUE_CODEC = Codec.intRange(0, MAX_SPACING);
        private static final Codec<GridOffset> FULL_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        VALUE_CODEC.fieldOf("x").forGetter(GridOffset::x),
                        VALUE_CODEC.fieldOf("z").forGetter(GridOffset::z))
                .apply(instance, GridOffset::new));
        public static final Codec<GridOffset> CODEC = Codec.either(VALUE_CODEC, FULL_CODEC)
                .xmap(
                        either -> either.map(value -> new GridOffset(value, value), full -> full),
                        offset -> offset.x == offset.z
                                ? Either.left(offset.x)
                                : Either.right(offset));

        // Folds the offset into one cell
        GridOffset within(int spacing) {
            return new GridOffset(Math.floorMod(x, spacing), Math.floorMod(z, spacing));
        }
    }

    public record ExclusionZone(TagKey<Structure> structures, int chunkCount) {
        public static final Codec<ExclusionZone> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        TagKey.codec(Registries.STRUCTURE).fieldOf("structures").forGetter(ExclusionZone::structures),
                        Codec.intRange(1, 16).fieldOf("chunk_count").forGetter(ExclusionZone::chunkCount))
                .apply(instance, ExclusionZone::new));

        public boolean isForbidden(ChunkGeneratorStructureState state, int x, int z, StructurePlacement own) {
            return StructurePlacements.hasStructureChunkInRange(
                    state, set -> set.value().placement() != own, holder -> holder.is(structures), x, z, chunkCount);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(int spacing, float probability) {
        return new Builder().spacing(spacing).probability(probability);
    }

    public static Builder builder(int spacing, int randomOffset, float probability) {
        return new Builder().spacing(spacing).randomOffset(randomOffset).probability(probability);
    }

    public static class Builder {
        private Vec3i locateOffset = Vec3i.ZERO;
        private float probability = 1.0F;
        @Nullable
        private Integer salt;
        private Optional<ExclusionZone> exclusionZone = Optional.empty();
        private int spacing = 16;
        private Optional<GridOffset> gridOffset = Optional.empty();
        @Nullable
        private Integer randomOffset;
        private RandomSpreadType spreadType = RandomSpreadType.TRIANGULAR;
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
            this.salt = namespace.hashCode() ^ id.hashCode();
            return this;
        }

        public Builder saltIfUnset(String namespace, String id) {
            return salt == null ? salt(namespace, id) : this;
        }

        public Builder exclusionZone(TagKey<Structure> structures, int chunkCount) {
            this.exclusionZone = Optional.of(new ExclusionZone(structures, chunkCount));
            return this;
        }

        public Builder spacing(int spacing) {
            this.spacing = spacing;
            return this;
        }

        /// Pins the grid shift. Left unset, it is derived from the world seed and the salt.
        public Builder gridOffset(int x, int z) {
            this.gridOffset = Optional.of(new GridOffset(x, z));
            return this;
        }

        /// Defaults to 70% of `spacing`. 0 is a strict grid.
        public Builder randomOffset(int randomOffset) {
            this.randomOffset = randomOffset;
            return this;
        }

        /// Defaults to triangular, which favors the middle of the offset range. Linear is uniform, as in vanilla.
        public Builder spreadType(RandomSpreadType spreadType) {
            this.spreadType = spreadType;
            return this;
        }

        /// Keeps the structure at least this many chunks, in a straight line, from the world origin.
        public Builder minChunksFromCenter(int minChunksFromCenter) {
            this.minChunksFromCenter = minChunksFromCenter;
            return this;
        }

        public ScatteredGridPlacement build() {
            if (spacing < 1) {
                throw new IllegalStateException("grid placement: spacing " + spacing + " is below 1");
            }
            if (probability < 0.0F || probability > 1.0F) {
                throw new IllegalStateException("grid placement: probability " + probability + " is outside 0 to 1");
            }
            if (minChunksFromCenter < 0) {
                throw new IllegalStateException("grid placement: min chunks from center " + minChunksFromCenter + " is negative");
            }
            return new ScatteredGridPlacement(
                    locateOffset,
                    probability,
                    salt == null ? 0 : salt,
                    exclusionZone,
                    spacing,
                    gridOffset,
                    randomOffset == null ? (int) (spacing * 0.7) : randomOffset,
                    spreadType,
                    minChunksFromCenter);
        }
    }
}
