package com.ametrin.structures.structure.simple;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/// How a heightmap start height measures the terrain under the structure
public enum HeightMode implements StringRepresentable {
    /// At the structure's origin corner. The cheapest.
    CORNER("corner"),
    /// The mean of the four footprint corners, like vanilla's igloo.
    MEAN("mean"),
    /// The lowest of the four footprint corners, so no corner floats.
    LOWEST("lowest");

    public static final Codec<HeightMode> CODEC = StringRepresentable.fromEnum(HeightMode::values);

    private final String name;

    HeightMode(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
