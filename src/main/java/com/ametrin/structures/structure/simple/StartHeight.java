package com.ametrin.structures.structure.simple;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.ToIntFunction;

/// Where a structure generates vertically: a [HeightAnchor], a random height between two anchors, or a [HeightProvider].
public sealed interface StartHeight {
    Codec<StartHeight> CODEC = Codec.either(HeightAnchor.CODEC, Codec.either(Between.CODEC, HeightProvider.CODEC))
            .xmap(
                    either -> either.map(At::new, rest -> rest.map(between -> between, Provider::new)),
                    height -> switch (height) {
                        case At(HeightAnchor anchor) -> Either.left(anchor);
                        case Between between -> Either.right(Either.left(between));
                        case Provider(HeightProvider provider) -> Either.right(Either.right(provider));
                    });

    static StartHeight at(HeightAnchor anchor) {
        return new At(anchor);
    }

    static StartHeight between(HeightAnchor min, HeightAnchor max) {
        return new Between(min, max);
    }

    static StartHeight of(HeightProvider provider) {
        return new Provider(provider);
    }

    /// Empty when the lower anchor is above the upper one.
    ///
    /// @param terrainSampler the terrain height on a heightmap
    OptionalInt sample(RandomSource random, WorldGenerationContext world, ToIntFunction<Heightmap.Types> terrainSampler);

    /// The heightmap the structure stands on, if any.
    Optional<Heightmap.Types> groundHeightmap();

    record At(HeightAnchor anchor) implements StartHeight {
        @Override
        public OptionalInt sample(RandomSource random, WorldGenerationContext world, ToIntFunction<Heightmap.Types> terrainSampler) {
            return OptionalInt.of(anchor.resolve(world, terrainSampler));
        }

        @Override
        public Optional<Heightmap.Types> groundHeightmap() {
            return anchor.heightmap();
        }
    }

    record Between(HeightAnchor min, HeightAnchor max) implements StartHeight {
        public static final Codec<Between> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        HeightAnchor.CODEC.fieldOf("min").forGetter(Between::min),
                        HeightAnchor.CODEC.fieldOf("max").forGetter(Between::max))
                .apply(instance, Between::new));

        @Override
        public OptionalInt sample(RandomSource random, WorldGenerationContext world, ToIntFunction<Heightmap.Types> terrainSampler) {
            int low = min.resolve(world, terrainSampler);
            int high = max.resolve(world, terrainSampler);
            return low > high ? OptionalInt.empty() : OptionalInt.of(random.nextIntBetweenInclusive(low, high));
        }

        @Override
        public Optional<Heightmap.Types> groundHeightmap() {
            return min.heightmap().or(max::heightmap);
        }
    }

    record Provider(HeightProvider provider) implements StartHeight {
        @Override
        public OptionalInt sample(RandomSource random, WorldGenerationContext world, ToIntFunction<Heightmap.Types> terrainSampler) {
            return OptionalInt.of(provider.sample(random, world));
        }

        @Override
        public Optional<Heightmap.Types> groundHeightmap() {
            return Optional.empty();
        }
    }
}
