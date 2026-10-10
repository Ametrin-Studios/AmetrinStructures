package com.ametrin.structures.structure.simple;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.WorldGenerationContext;

import java.util.Optional;
import java.util.function.ToIntFunction;

/// A vanilla [VerticalAnchor], or a heightmap plus an offset.
public sealed interface HeightAnchor {
    Codec<HeightAnchor> CODEC = Codec.either(VerticalAnchor.CODEC, OnHeightmap.CODEC)
            .xmap(
                    either -> either.map(Fixed::new, onHeightmap -> onHeightmap),
                    anchor -> switch (anchor) {
                        case Fixed(VerticalAnchor fixed) -> Either.left(fixed);
                        case OnHeightmap onHeightmap -> Either.right(onHeightmap);
                    });

    static HeightAnchor absolute(int y) {
        return new Fixed(VerticalAnchor.absolute(y));
    }

    static HeightAnchor aboveBottom(int blocks) {
        return new Fixed(VerticalAnchor.aboveBottom(blocks));
    }

    static HeightAnchor belowTop(int blocks) {
        return new Fixed(VerticalAnchor.belowTop(blocks));
    }

    static HeightAnchor heightmap(Heightmap.Types heightmap, int offset) {
        return new OnHeightmap(heightmap, offset);
    }

    static HeightAnchor surface(int offset) {
        return heightmap(Heightmap.Types.WORLD_SURFACE_WG, offset);
    }

    static HeightAnchor oceanFloor(int offset) {
        return heightmap(Heightmap.Types.OCEAN_FLOOR_WG, offset);
    }

    int resolve(WorldGenerationContext world, ToIntFunction<Heightmap.Types> heightSampler);

    Optional<Heightmap.Types> heightmap();

    record Fixed(VerticalAnchor anchor) implements HeightAnchor {
        @Override
        public int resolve(WorldGenerationContext world, ToIntFunction<Heightmap.Types> heightSampler) {
            return anchor.resolveY(world);
        }

        @Override
        public Optional<Heightmap.Types> heightmap() {
            return Optional.empty();
        }
    }

    record OnHeightmap(Heightmap.Types type, int offset) implements HeightAnchor {
        public static final Codec<OnHeightmap> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Heightmap.Types.CODEC.fieldOf("heightmap").forGetter(OnHeightmap::type),
                        Codec.INT.optionalFieldOf("offset", 0).forGetter(OnHeightmap::offset))
                .apply(instance, OnHeightmap::new));

        @Override
        public int resolve(WorldGenerationContext world, ToIntFunction<Heightmap.Types> heightSampler) {
            return heightSampler.applyAsInt(type) + offset;
        }

        @Override
        public Optional<Heightmap.Types> heightmap() {
            return Optional.of(type);
        }
    }
}
