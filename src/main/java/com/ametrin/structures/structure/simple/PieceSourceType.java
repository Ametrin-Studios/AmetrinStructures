package com.ametrin.structures.structure.simple;

import com.mojang.serialization.MapCodec;

public record PieceSourceType(MapCodec<? extends PieceSource> codec) {}
