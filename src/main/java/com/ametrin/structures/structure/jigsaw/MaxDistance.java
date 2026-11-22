package com.ametrin.structures.structure.jigsaw;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.dimension.DimensionType;

import java.util.function.Function;

/// How far a [JigsawSource] layout may grow from its center, like `max_distance_from_center` of later vanilla versions. A single number limits both directions.
public record MaxDistance(int horizontal, int vertical) {
    private static final Codec<Integer> HORIZONTAL_CODEC = Codec.intRange(1, 128);
    private static final Codec<MaxDistance> FULL_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    HORIZONTAL_CODEC.fieldOf("horizontal").forGetter(MaxDistance::horizontal),
                    ExtraCodecs.intRange(1, DimensionType.Y_SIZE).optionalFieldOf("vertical", DimensionType.Y_SIZE).forGetter(MaxDistance::vertical))
            .apply(instance, MaxDistance::new));
    public static final Codec<MaxDistance> CODEC = Codec.either(FULL_CODEC, HORIZONTAL_CODEC).xmap(
            either -> either.map(Function.identity(), MaxDistance::new),
            distance -> distance.horizontal == distance.vertical ? Either.right(distance.horizontal) : Either.left(distance));

    public MaxDistance(int value) {
        this(value, value);
    }
}
