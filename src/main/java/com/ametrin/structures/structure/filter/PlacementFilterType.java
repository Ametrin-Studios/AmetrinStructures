package com.ametrin.structures.structure.filter;

import com.mojang.serialization.MapCodec;

public record PlacementFilterType(MapCodec<? extends PlacementFilter> codec) {}
