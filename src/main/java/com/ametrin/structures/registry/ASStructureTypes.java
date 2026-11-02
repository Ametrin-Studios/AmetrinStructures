package com.ametrin.structures.registry;

import com.ametrin.structures.AmetrinStructures;
import com.ametrin.structures.structure.modular.ModularStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ASStructureTypes {
    public static final DeferredRegister<StructureType<?>> REGISTER = DeferredRegister.create(Registries.STRUCTURE_TYPE, AmetrinStructures.MOD_ID);

    public static final DeferredHolder<StructureType<?>, StructureType<ModularStructure>> MODULAR =
            REGISTER.register("modular", () -> () -> ModularStructure.CODEC);
}
