package com.ametrin.structures.fixture;

import com.ametrin.structures.registry.ASFixtures;
import com.mojang.serialization.MapCodec;

import java.util.List;

/// Places the becomes state and nothing else.
public record EmptyFixture() implements Fixture {
    public static final EmptyFixture INSTANCE = new EmptyFixture();
    public static final MapCodec<EmptyFixture> CODEC = MapCodec.unit(INSTANCE);
    public static final List<FixtureField<?>> FIELDS = List.of();

    @Override
    public void apply(FixtureContext context) {
    }

    @Override
    public FixtureType type() {
        return ASFixtures.EMPTY.get();
    }
}
