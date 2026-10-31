package com.ametrin.structures.placement;

import com.ametrin.structures.registry.ASPlacementTypes;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/// Splits the world into a grid of `spacing` by `spacing` chunk cells, with one structure per cell.
///
/// - `grid_offset` shifts the whole grid. If left out, it's derived from the world seed and the salt, so sets with the same spacing don't line up.
/// - `random_offset` moves each structure up to that many chunks away from its cell corner. 0 is a strict grid.
///
/// It can also keep away from structures in a tag and from the world origin.
public class ScatteredGridPlacement extends ExtendedSpreadPlacement {
    // Bounded so a mistyped value fails at load rather than at generation.
    public static final int MAX_SPACING = 4096;

    public static final MapCodec<ScatteredGridPlacement> CODEC = RecordCodecBuilder.<ScatteredGridPlacement>mapCodec(instance -> spreadCodec(instance)
                    .and(instance.group(
                            Codec.INT.fieldOf("spacing").forGetter(ScatteredGridPlacement::spacing),
                            GridOffset.CODEC.optionalFieldOf("grid_offset").forGetter(ScatteredGridPlacement::declaredGridOffset),
                            Codec.intRange(0, MAX_SPACING).fieldOf("random_offset").forGetter(ScatteredGridPlacement::randomOffset)))
                    .apply(instance, ScatteredGridPlacement::new))
            .validate(ScatteredGridPlacement::validate);

    private final Optional<GridOffset> gridOffset;
    private final int randomOffset;

    public ScatteredGridPlacement(Vec3i locateOffset, float probability, int salt, Optional<TagExclusionZone> exclusionZone, int minChunksFromCenter, int spacing, Optional<GridOffset> gridOffset, int randomOffset) {
        randomOffset = Mth.clamp(randomOffset, 0, spacing - 1);
        super(locateOffset, probability, salt, exclusionZone, minChunksFromCenter, spacing, spacing - 1 - randomOffset);
        this.gridOffset = gridOffset;
        this.randomOffset = randomOffset;
    }

    @Override
    protected Optional<String> problem() {
        if (spacing() < 1 || spacing() > MAX_SPACING) {
            return Optional.of("spacing " + spacing() + " is outside 1 to " + MAX_SPACING);
        }
        return super.problem();
    }

    public GridOffset gridOffset(long seed) {
        return gridOffset.map(offset -> offset.within(spacing())).orElseGet(() -> {
            var random = RandomSource.create(seed ^ (long) salt() * 0x5DEECE66DL);
            return new GridOffset(random.nextInt(spacing()), random.nextInt(spacing()));
        });
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
    public StructurePlacementType<?> type() {
        return ASPlacementTypes.GRID.get();
    }

    public record GridOffset(int x, int z) {
        private static final Codec<GridOffset> FULL_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Codec.INT.fieldOf("x").forGetter(GridOffset::x),
                        Codec.INT.fieldOf("z").forGetter(GridOffset::z))
                .apply(instance, GridOffset::new));
        public static final Codec<GridOffset> CODEC = Codec.either(Codec.INT, FULL_CODEC)
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

    public static class Builder extends ExtendedSpreadPlacement.Builder<Builder, ScatteredGridPlacement> {
        private int spacing = 16;
        private Optional<GridOffset> gridOffset = Optional.empty();
        @Nullable
        private Integer randomOffset;

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

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        protected ScatteredGridPlacement create(Vec3i locateOffset, float probability, int salt, Optional<TagExclusionZone> exclusionZone, int minChunksFromCenter) {
            return new ScatteredGridPlacement(locateOffset, probability, salt, exclusionZone, minChunksFromCenter, spacing, gridOffset, randomOffset == null ? (int) (spacing * 0.7) : randomOffset);
        }
    }
}
