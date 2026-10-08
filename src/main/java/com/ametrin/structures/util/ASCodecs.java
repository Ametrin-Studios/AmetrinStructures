package com.ametrin.structures.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

public final class ASCodecs {
    /// [BlockState#CODEC] that also reads the `{Name, Properties}` form block states were stored in before
    /// Minecraft 26.3, so data saved back then keeps its block states. Writes the current form.
    // TODO 27.1: remove the legacy form.
    public static final Codec<BlockState> BLOCK_STATE = Codec.withAlternative(BlockState.CODEC, legacyBlockState());

    private ASCodecs() {
    }

    /// A codec that dispatches on a registry of type objects, each exposing the [MapCodec] of its values.
    public static <V, T> Codec<V> dispatch(Supplier<Registry<T>> registry, Function<V, T> typeGetter, Function<T, MapCodec<? extends V>> codecGetter) {
        return Codec.lazyInitialized(() -> registry.get().byNameCodec().dispatch(typeGetter, codecGetter));
    }

    private static Codec<BlockState> legacyBlockState() {
        return BuiltInRegistries.BLOCK.byNameCodec().dispatch("Name", BlockState::getBlock, block -> block.getStateDefinition().propertiesCodec().codec()
                .lenientOptionalFieldOf("Properties")
                .xmap(state -> state.orElseGet(block::defaultBlockState), Optional::of));
    }
}
