package com.ametrin.structures.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;

import java.util.function.Function;
import java.util.function.Supplier;

public final class ASCodecs {
    private ASCodecs() {
    }

    /// A codec that dispatches on a registry of type objects, each exposing the [MapCodec] of its values.
    public static <V, T> Codec<V> dispatch(Supplier<Registry<T>> registry, Function<V, T> typeGetter, Function<T, MapCodec<? extends V>> codecGetter) {
        return Codec.lazyInitialized(() -> registry.get().byNameCodec().dispatch(typeGetter, codecGetter));
    }
}
