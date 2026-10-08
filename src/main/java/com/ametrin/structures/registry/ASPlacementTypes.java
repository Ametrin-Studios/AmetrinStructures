package com.ametrin.structures.registry;

import com.ametrin.structures.AmetrinStructures;
import com.ametrin.structures.placement.ScatteredGridPlacement;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ASPlacementTypes {
    public static final DeferredRegister<StructurePlacementType<?>> REGISTER = DeferredRegister.create(Registries.STRUCTURE_PLACEMENT, AmetrinStructures.MOD_ID);

    public static final DeferredHolder<StructurePlacementType<?>, StructurePlacementType<ScatteredGridPlacement>> GRID = REGISTER.register("scattered_grid", () -> () -> ScatteredGridPlacement.CODEC);
}
