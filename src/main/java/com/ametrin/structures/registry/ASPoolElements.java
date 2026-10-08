package com.ametrin.structures.registry;

import com.ametrin.structures.AmetrinStructures;
import com.ametrin.structures.structure.jigsaw.ExtendedSinglePoolElement;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ASPoolElements {
    public static final DeferredRegister<StructurePoolElementType<?>> REGISTER = DeferredRegister.create(Registries.STRUCTURE_POOL_ELEMENT, AmetrinStructures.MOD_ID);

    public static final DeferredHolder<StructurePoolElementType<?>, StructurePoolElementType<ExtendedSinglePoolElement>>
            SINGLE = REGISTER.register("single_pool_element", () -> () -> ExtendedSinglePoolElement.CODEC);
}
