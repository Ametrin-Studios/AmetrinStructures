package com.ametrin.structures.fixture;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;

import java.util.Optional;
import java.util.function.Function;

/// A field definition of a fixture record.
/// ```java
/// static final FixtureField<Boolean> HANGING = FixtureField.withDefault("hanging", FieldType.bool(), true);
///
/// public static final MapCodec<Lantern> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
///                 HANGING.forGetter(Lantern::hanging))
///         .apply(instance, Lantern::new));
/// ```
public final class FixtureField<C> {
    public enum Presence {
        /// The fixture doesn't decode without it.
        REQUIRED,
        /// It takes its default.
        DEFAULT,
        /// It stays empty.
        OPTIONAL
    }

    private final String key;
    private final FieldType<?> type;
    private final Presence presence;
    private final MapCodec<C> codec;
    private final Function<HolderLookup.Provider, Optional<String>> defaultText;

    private FixtureField(String key, FieldType<?> type, Presence presence, MapCodec<C> codec, Function<HolderLookup.Provider, Optional<String>> defaultText) {
        this.key = key;
        this.type = type;
        this.presence = presence;
        this.codec = codec;
        this.defaultText = defaultText;
    }

    public static <T> FixtureField<T> required(String key, FieldType<T> type) {
        return new FixtureField<>(key, type, Presence.REQUIRED, type.codec().fieldOf(key), _ -> Optional.empty());
    }

    public static <T> FixtureField<T> withDefault(String key, FieldType<T> type, T defaultValue) {
        return new FixtureField<>(key, type, Presence.DEFAULT, type.codec().optionalFieldOf(key, defaultValue),
                registries -> Optional.of(type.format(defaultValue, registries)));
    }

    public static <T> FixtureField<Optional<T>> optional(String key, FieldType<T> type) {
        return new FixtureField<>(key, type, Presence.OPTIONAL, type.codec().optionalFieldOf(key), _ -> Optional.empty());
    }

    public <R> RecordCodecBuilder<R, C> forGetter(Function<R, C> getter) {
        return codec.forGetter(getter);
    }

    public String key() {
        return key;
    }

    public FieldType<?> type() {
        return type;
    }

    public Presence presence() {
        return presence;
    }

    public MapCodec<C> codec() {
        return codec;
    }

    /// The default as the text that parses to it, for [Presence#DEFAULT] fields.
    public Optional<String> defaultText(HolderLookup.Provider registries) {
        return defaultText.apply(registries);
    }
}
