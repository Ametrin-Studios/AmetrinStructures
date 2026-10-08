package com.ametrin.structures.registry;

import com.ametrin.structures.AmetrinStructures;
import com.ametrin.structures.structure.simple.PieceSourceType;
import com.ametrin.structures.structure.simple.PieceSources;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ASPieceSources {
    public static final DeferredRegister<PieceSourceType> REGISTER = DeferredRegister.create(ASRegistries.PIECE_SOURCE_TYPE, AmetrinStructures.MOD_ID);

    public static final DeferredHolder<PieceSourceType, PieceSourceType> SINGLE = REGISTER.register("single", () -> new PieceSourceType(PieceSources.SingleSource.CODEC));
    public static final DeferredHolder<PieceSourceType, PieceSourceType> WEIGHTED = REGISTER.register("weighted", () -> new PieceSourceType(PieceSources.WeightedSource.CODEC));
    public static final DeferredHolder<PieceSourceType, PieceSourceType> COMPOUND = REGISTER.register("compound", () -> new PieceSourceType(PieceSources.CompoundSource.CODEC));
}
