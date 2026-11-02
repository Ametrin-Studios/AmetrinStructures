package com.ametrin.structures.structure.modular;

import com.mojang.serialization.MapCodec;

public record PieceSourceType(MapCodec<? extends PieceSource> codec) {}
