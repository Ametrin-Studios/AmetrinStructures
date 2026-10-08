package com.ametrin.structures.registry;

import com.ametrin.structures.AmetrinStructures;
import com.ametrin.structures.placement.ScatteredGridPlacement;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ASPlacementTypes {
    public static final DeferredRegister<MapCodec<? extends StructurePlacement>> REGISTER = DeferredRegister.create(Registries.STRUCTURE_PLACEMENT, AmetrinStructures.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends StructurePlacement>, MapCodec<ScatteredGridPlacement>> GRID = REGISTER.register("scattered_grid", () -> ScatteredGridPlacement.CODEC);
}
