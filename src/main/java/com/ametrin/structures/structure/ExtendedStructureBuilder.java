package com.ametrin.structures.structure;

import com.ametrin.structures.structure.filter.PlacementFilter;
import com.ametrin.structures.structure.filter.PlacementFilters;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import java.util.Optional;

public interface ExtendedStructureBuilder<B extends ExtendedStructureBuilder<B>> {
    B filter(PlacementFilter filter);

    default B filterHeightRange(int min, int max) {
        var filter = new PlacementFilters.HeightRange(min, max);
        if (filter.validate().isError()) {
            throw new IllegalArgumentException("height range " + min + " to " + max + " is not a valid range");
        }
        return filter(filter);
    }

    default B filterGroundCheck(TagKey<Block> blocks) {
        return filter(new PlacementFilters.GroundCheck(blocks));
    }

    default B filterFlatness(int maxVariance) {
        return filterFlatness(maxVariance, 0);
    }

    /// @param margin negative values allowed
    default B filterFlatness(int maxVariance, int margin) {
        if (maxVariance < 0 || maxVariance > PlacementFilters.Flatness.MAX_VARIANCE) {
            throw new IllegalArgumentException("flatness variance " + maxVariance + " is outside 0 to " + PlacementFilters.Flatness.MAX_VARIANCE);
        }
        if (Math.abs(margin) > PlacementFilters.Flatness.MAX_MARGIN) {
            throw new IllegalArgumentException("flatness margin " + margin + " is outside -" + PlacementFilters.Flatness.MAX_MARGIN + " to " + PlacementFilters.Flatness.MAX_MARGIN);
        }
        return filter(new PlacementFilters.Flatness(maxVariance, margin));
    }

    /// Requires at least `minDepth` blocks of fluid above the structure.
    default B filterSubmerged(int minDepth) {
        if (minDepth < 0) {
            throw new IllegalArgumentException("submerged depth " + minDepth + " is negative");
        }
        return filter(new PlacementFilters.Submerged(minDepth));
    }

    /// Keeps the structure out of water deeper than `maxDepth`.
    default B filterMaxWaterDepth(int maxDepth) {
        return filterWaterDepth(0, maxDepth);
    }

    default B filterMinWaterDepth(int minDepth) {
        if (minDepth < 0) {
            throw new IllegalArgumentException("water depth " + minDepth + " is not a valid range");
        }
        return filter(new PlacementFilters.WaterDepth(minDepth, Optional.empty()));
    }

    default B filterWaterDepth(int minDepth, int maxDepth) {
        var filter = new PlacementFilters.WaterDepth(minDepth, Optional.of(maxDepth));
        if (minDepth < 0 || filter.validate().isError()) {
            throw new IllegalArgumentException("water depth " + minDepth + " to " + maxDepth + " is not a valid range");
        }
        return filter(filter);
    }

    default B filterWithinBiome() {
        return filterWithinBiome(0);
    }

    default B filterWithinBiome(int margin) {
        if (margin < 0 || margin > PlacementFilters.WithinBiome.MAX_MARGIN) {
            throw new IllegalArgumentException("biome margin " + margin + " is outside 0 to " + PlacementFilters.WithinBiome.MAX_MARGIN);
        }
        return filter(new PlacementFilters.WithinBiome(Optional.empty(), margin));
    }
}
