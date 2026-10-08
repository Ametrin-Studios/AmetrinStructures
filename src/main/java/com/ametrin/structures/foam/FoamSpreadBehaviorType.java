package com.ametrin.structures.foam;

import com.mojang.serialization.MapCodec;

public record FoamSpreadBehaviorType(MapCodec<? extends FoamSpreadBehavior> codec) {}
