package com.ametrin.structures.structure.simple;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.WorldGenerationContext;

import java.util.Optional;

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
        return heightmap(heightmap, offset, HeightMode.ORIGIN);
    }

    static HeightAnchor heightmap(Heightmap.Types heightmap, int offset, HeightMode mode) {
        return new OnHeightmap(heightmap, offset, mode);
    }

    static HeightAnchor surface(int offset) {
        return surface(offset, HeightMode.ORIGIN);
    }

    static HeightAnchor surface(int offset, HeightMode mode) {
        return heightmap(Heightmap.Types.WORLD_SURFACE_WG, offset, mode);
    }

    static HeightAnchor oceanFloor(int offset) {
        return oceanFloor(offset, HeightMode.ORIGIN);
    }

    static HeightAnchor oceanFloor(int offset, HeightMode mode) {
        return heightmap(Heightmap.Types.OCEAN_FLOOR_WG, offset, mode);
    }

    int resolve(WorldGenerationContext world, Terrain terrain);

    /// The lowest Y [#resolve] can return.
    int lowest(WorldGenerationContext world);

    /// The highest Y [#resolve] can return.
    int highest(WorldGenerationContext world);

    Optional<Heightmap.Types> heightmap();

    /// The terrain height under the structure.
    @FunctionalInterface
    interface Terrain {
        int height(Heightmap.Types heightmap, HeightMode mode);
    }

    record Fixed(VerticalAnchor anchor) implements HeightAnchor {
        @Override
        public int resolve(WorldGenerationContext world, Terrain terrain) {
            return anchor.resolveY(world);
        }

        @Override
        public int lowest(WorldGenerationContext world) {
            return anchor.resolveY(world);
        }

        @Override
        public int highest(WorldGenerationContext world) {
            return anchor.resolveY(world);
        }

        @Override
        public Optional<Heightmap.Types> heightmap() {
            return Optional.empty();
        }
    }

    record OnHeightmap(Heightmap.Types type, int offset, HeightMode mode) implements HeightAnchor {
        public static final Codec<OnHeightmap> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Heightmap.Types.CODEC.fieldOf("heightmap").forGetter(OnHeightmap::type),
                        Codec.INT.optionalFieldOf("offset", 0).forGetter(OnHeightmap::offset),
                        HeightMode.CODEC.optionalFieldOf("mode", HeightMode.ORIGIN).forGetter(OnHeightmap::mode))
                .apply(instance, OnHeightmap::new));

        @Override
        public int resolve(WorldGenerationContext world, Terrain terrain) {
            return terrain.height(type, mode) + offset;
        }

        // A heightmap ranges from the bottom of the world to one above its top.
        @Override
        public int lowest(WorldGenerationContext world) {
            return world.getMinGenY() + offset;
        }

        @Override
        public int highest(WorldGenerationContext world) {
            return world.getMinGenY() + world.getGenDepth() + offset;
        }

        @Override
        public Optional<Heightmap.Types> heightmap() {
            return Optional.of(type);
        }
    }
}
