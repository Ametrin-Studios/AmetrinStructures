package com.ametrin.structures.registry;

import com.ametrin.structures.AmetrinStructures;
import com.ametrin.structures.structure.simple.SimpleStructurePiece;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ASPieceTypes {
    public static final DeferredRegister<StructurePieceType> REGISTER = DeferredRegister.create(Registries.STRUCTURE_PIECE, AmetrinStructures.MOD_ID);

    public static final DeferredHolder<StructurePieceType, StructurePieceType> SIMPLE = REGISTER.register("simple", () -> SimpleStructurePiece::new);
}
