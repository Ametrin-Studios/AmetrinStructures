package com.ametrin.structures.placement;

import com.ametrin.structures.registry.ASPlacementTypes;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/// Splits the world into a grid of `spacing` by `spacing` chunk cells, with one structure per cell.
///
/// - `grid_offset` shifts the whole grid. If left out, it's derived from the world seed and the salt, so sets with the same spacing don't line up.
/// - `random_offset` moves each structure up to that many chunks away from its cell corner. 0 is a strict grid.
///
/// It can also keep away from structures in a tag and from the world origin.
public class ScatteredGridPlacement extends RandomSpreadStructurePlacement { // extends RandomSpreadStructurePlacement because /locate and similar have no generic case
    // Bounded so a mistyped value fails at load rather than at generation.
    public static final int MAX_SPACING = 4096;

    public static final MapCodec<ScatteredGridPlacement> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Vec3i.CODEC.optionalFieldOf("locate_offset", Vec3i.ZERO).forGetter(ScatteredGridPlacement::locateOffset),
                    Codec.floatRange(0.0F, 1.0F)
                            .optionalFieldOf("probability", 1.0F)
                            .forGetter(ScatteredGridPlacement::frequency),
                    Codec.INT.optionalFieldOf("salt", 0).forGetter(ScatteredGridPlacement::salt),
                    TagExclusionZone.CODEC.optionalFieldOf("exclusion_zone").forGetter(ScatteredGridPlacement::structureExclusionZone),
                    Codec.intRange(1, MAX_SPACING).fieldOf("spacing").forGetter(ScatteredGridPlacement::spacing),
                    GridOffset.CODEC.optionalFieldOf("grid_offset").forGetter(ScatteredGridPlacement::declaredGridOffset),
                    Codec.intRange(0, MAX_SPACING).fieldOf("random_offset").forGetter(ScatteredGridPlacement::randomOffset),
                    ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("min_chunks_from_center", 0).forGetter(ScatteredGridPlacement::minChunksFromCenter))
            .apply(instance, ScatteredGridPlacement::new));

    private final Optional<TagExclusionZone> tagExclusionZone;
    private final Optional<GridOffset> gridOffset;
    private final int randomOffset;
    private final int minChunksFromCenter;

    public ScatteredGridPlacement(
            Vec3i locateOffset,
            float probability,
            int salt,
            Optional<TagExclusionZone> exclusionZone,
            int spacing,
            Optional<GridOffset> gridOffset,
            int randomOffset,
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
                RandomSpreadType.LINEAR);
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
        return StructurePlacements.isTooCloseToCenter(chunkX, chunkZ, minChunksFromCenter);
    }

    public Optional<TagExclusionZone> structureExclusionZone() {
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

    // Locate checks this, not isStructureChunk, so it skips these spots without loading them.
    @Override
    public boolean applyAdditionalChunkRestrictions(int x, int z, long seed) {
        return !isTooCloseToCenter(x, z) && super.applyAdditionalChunkRestrictions(x, z, seed);
    }

    @Override
    public boolean applyInteractionsWithOtherStructures(ChunkGeneratorStructureState state, int x, int z) {
        return tagExclusionZone.isEmpty() || !tagExclusionZone.get().isForbidden(state, x, z, this);
    }

    @Override
    public StructurePlacementType<?> type() {
        return ASPlacementTypes.GRID.get();
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

        // Wraps the offset into a single cell.
        GridOffset within(int spacing) {
            return new GridOffset(Math.floorMod(x, spacing), Math.floorMod(z, spacing));
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
        private Optional<TagExclusionZone> exclusionZone = Optional.empty();
        private int spacing = 16;
        private Optional<GridOffset> gridOffset = Optional.empty();
        @Nullable
        private Integer randomOffset;
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

        public Builder spacing(int spacing) {
            this.spacing = spacing;
            return this;
        }

        /// Fixes the grid shift. If unset, it's derived from the world seed and the salt.
        public Builder gridOffset(int x, int z) {
            this.gridOffset = Optional.of(new GridOffset(x, z));
            return this;
        }

        /// Defaults to 70% of `spacing`. 0 is a strict grid.
        public Builder randomOffset(int randomOffset) {
            this.randomOffset = randomOffset;
            return this;
        }

        /// Keeps the structure at least this many chunks, in a straight line, from the world origin.
        public Builder minChunksFromCenter(int minChunksFromCenter) {
            this.minChunksFromCenter = minChunksFromCenter;
            return this;
        }

        public ScatteredGridPlacement build() {
            return build(salt == null ? 0 : salt);
        }

        /// salt defaults to one derived from `id`.
        public ScatteredGridPlacement build(Identifier id) {
            return build(id.getNamespace(), id.getPath());
        }

        /// salt defaults to one derived from `namespace` and `id`.
        public ScatteredGridPlacement build(String namespace, String id) {
            return build(salt == null ? StructurePlacements.salt(namespace, id) : salt);
        }

        private ScatteredGridPlacement build(int salt) {
            if (spacing < 1 || spacing > MAX_SPACING) {
                throw new IllegalStateException("grid placement: spacing " + spacing + " is outside 1 to " + MAX_SPACING);
            }
            if (probability < 0.0F || probability > 1.0F) {
                throw new IllegalStateException("grid placement: probability " + probability + " is outside 0 to 1");
            }
            if (minChunksFromCenter < 0) {
                throw new IllegalStateException("grid placement: min chunks from center " + minChunksFromCenter + " is negative");
            }
            exclusionZone.ifPresent(zone -> zone.validate("grid placement"));
            return new ScatteredGridPlacement(
                    locateOffset,
                    probability,
                    salt,
                    exclusionZone,
                    spacing,
                    gridOffset,
                    randomOffset == null ? (int) (spacing * 0.7) : randomOffset,
                    minChunksFromCenter);
        }
    }
}
