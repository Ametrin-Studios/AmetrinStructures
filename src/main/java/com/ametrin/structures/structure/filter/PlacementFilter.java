package com.ametrin.structures.structure.filter;

import com.ametrin.structures.registry.ASRegistries;
import com.ametrin.structures.util.ASCodecs;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;

public interface PlacementFilter {
    Codec<PlacementFilter> CODEC = ASCodecs.dispatch(() -> ASRegistries.PLACEMENT_FILTER_TYPES, PlacementFilter::type, PlacementFilterType::codec);

    boolean test(Context context);

    PlacementFilterType type();

    /// @param origin    the structure's start position
    /// @param footprint the box around every piece at `origin`
    /// @param terrain   cached terrain lookups
    record Context(Structure.GenerationContext generation, BlockPos origin, BoundingBox footprint,
                   TerrainSampler terrain) {}
}
