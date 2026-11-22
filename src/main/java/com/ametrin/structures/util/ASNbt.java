package com.ametrin.structures.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.ApiStatus;

import java.util.Optional;

/// The codec based [CompoundTag] accessors of later vanilla versions.
@ApiStatus.Internal
public final class ASNbt {
    /// Any tag, as it is.
    public static final Codec<Tag> TAG_CODEC = Codec.PASSTHROUGH.xmap(dynamic -> dynamic.convert(NbtOps.INSTANCE).getValue(), tag -> new Dynamic<>(NbtOps.INSTANCE, tag));

    private ASNbt() {}

    public static <T> Optional<T> read(CompoundTag tag, String key, Codec<T> codec) {
        return read(tag, key, codec, NbtOps.INSTANCE);
    }

    public static <T> Optional<T> read(CompoundTag tag, String key, Codec<T> codec, DynamicOps<Tag> ops) {
        var value = tag.get(key);
        return value == null ? Optional.empty() : codec.parse(ops, value).resultOrPartial(error -> ASLog.error("Failed to read field ({}={}): {}", key, value, error));
    }

    public static <T> Optional<T> read(CompoundTag tag, MapCodec<T> codec, DynamicOps<Tag> ops) {
        return codec.decode(ops, ops.getMap(tag).getOrThrow()).resultOrPartial(error -> ASLog.error("Failed to read value ({}): {}", tag, error));
    }

    public static <T> void store(CompoundTag tag, String key, Codec<T> codec, T value) {
        store(tag, key, codec, NbtOps.INSTANCE, value);
    }

    public static <T> void store(CompoundTag tag, String key, Codec<T> codec, DynamicOps<Tag> ops, T value) {
        tag.put(key, codec.encodeStart(ops, value).getOrThrow());
    }

    public static <T> void store(CompoundTag tag, MapCodec<T> codec, DynamicOps<Tag> ops, T value) {
        tag.merge((CompoundTag) codec.encoder().encodeStart(ops, value).getOrThrow());
    }

    public static int getIntOr(CompoundTag tag, String key, int fallback) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getInt(key) : fallback;
    }

    public static boolean getBooleanOr(CompoundTag tag, String key, boolean fallback) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getBoolean(key) : fallback;
    }

    public static float getFloatOr(CompoundTag tag, String key, float fallback) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getFloat(key) : fallback;
    }

    public static String getStringOr(CompoundTag tag, String key, String fallback) {
        return tag.contains(key, Tag.TAG_STRING) ? tag.getString(key) : fallback;
    }
}
