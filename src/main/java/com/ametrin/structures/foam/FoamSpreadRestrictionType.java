package com.ametrin.structures.foam;

import com.mojang.serialization.MapCodec;

public record FoamSpreadRestrictionType(MapCodec<? extends FoamSpreadRestriction> codec) {}
