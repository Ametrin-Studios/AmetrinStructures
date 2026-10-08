package com.ametrin.structures.fixture;

import com.mojang.serialization.MapCodec;

public record FixtureConditionType(MapCodec<? extends FixtureCondition> codec) {}
