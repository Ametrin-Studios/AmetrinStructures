package com.ametrin.structures.fixture;

import com.mojang.serialization.JavaOps;
import com.mojang.serialization.MapCodec;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/// @param fields what the authoring screen offers, the same keys as `codec`
/// @throws IllegalArgumentException a key is one of [#RESERVED_KEYS], is declared twice, or `codec` and `fields` disagree
public record FixtureType(MapCodec<? extends Fixture> codec, List<FixtureField<?>> fields) {
    /// Keys a stored alternative already uses next to the fixture's own.
    public static final Set<String> RESERVED_KEYS = Set.of(
            Fixture.TYPE_KEY, WeightedFixture.WEIGHT_KEY, WeightedFixture.GENERATION_CHANCE_KEY, WeightedFixture.CONDITIONS_KEY);

    public FixtureType {
        fields = List.copyOf(fields);
        var fieldKeys = new HashSet<String>();
        for (var field : fields) {
            if (!fieldKeys.add(field.key())) {
                throw new IllegalArgumentException("fixture field " + field.key() + " is declared twice");
            }
        }
        var codecKeys = new HashSet<String>();
        codec.keys(JavaOps.INSTANCE).forEach(key -> codecKeys.add(JavaOps.INSTANCE.getStringValue(key).getOrThrow()));
        for (var key : codecKeys) {
            if (RESERVED_KEYS.contains(key)) {
                throw new IllegalArgumentException("fixture field " + key + " clashes with a key every alternative has: " + RESERVED_KEYS);
            }
        }
        if (!codecKeys.equals(fieldKeys)) {
            throw new IllegalArgumentException("the codec's keys " + codecKeys + " don't match the fields " + fieldKeys);
        }
    }
}
