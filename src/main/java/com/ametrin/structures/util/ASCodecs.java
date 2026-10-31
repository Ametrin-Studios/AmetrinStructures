package com.ametrin.structures.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

public final class ASCodecs {
    private ASCodecs() {
    }

    /// A codec that dispatches on a registry of type objects, each exposing the [MapCodec] of its values.
    public static <V, T> Codec<V> dispatch(Supplier<Registry<T>> registry, Function<V, T> typeGetter, Function<T, MapCodec<? extends V>> codecGetter) {
        return Codec.lazyInitialized(() -> registry.get().byNameCodec().dispatch(typeGetter, codecGetter));
    }

    /// Success with `value`, or an error with `problem`.
    public static <T> DataResult<T> check(T value, Optional<String> problem) {
        return problem.<DataResult<T>>map(message -> DataResult.error(() -> message)).orElseGet(() -> DataResult.success(value));
    }
}
