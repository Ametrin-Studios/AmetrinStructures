package com.ametrin.structures.structure.simple;

import com.ametrin.structures.structure.filter.TerrainSampler;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.stream.IntStream;

/// How a heightmap anchor measures the terrain under the structure
public enum HeightMode implements StringRepresentable {
    /// At the structure's origin. The cheapest.
    ORIGIN("origin"),
    /// The mean of the four footprint corners, like vanilla's igloo.
    CORNERS_MEAN("corners_mean"),
    /// The lowest of the four footprint corners, so no corner floats.
    LOWEST_CORNER("lowest_corner"),
    /// The highest of the four footprint corners.
    HIGHEST_CORNER("highest_corner");

    public static final Codec<HeightMode> CODEC = StringRepresentable.fromEnum(HeightMode::values);

    private final String name;

    HeightMode(String name) {
        this.name = name;
    }

    public int measure(TerrainSampler terrain, Heightmap.Types heightmap, BlockPos origin, BoundingBox footprint) {
        if (this == ORIGIN) {
            return terrain.surfaceHeight(heightmap, origin.getX(), origin.getZ());
        }

        var corners = IntStream.of(
                terrain.surfaceHeight(heightmap, footprint.minX(), footprint.minZ()),
                terrain.surfaceHeight(heightmap, footprint.minX(), footprint.maxZ()),
                terrain.surfaceHeight(heightmap, footprint.maxX(), footprint.minZ()),
                terrain.surfaceHeight(heightmap, footprint.maxX(), footprint.maxZ())
        );

        return switch (this) {
            case LOWEST_CORNER -> corners.min().orElseThrow();
            case HIGHEST_CORNER -> corners.max().orElseThrow();
            case CORNERS_MEAN -> corners.sum() / 4;
            case ORIGIN -> throw new RuntimeException();
        };
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
