package com.ametrin.structures.registry;

import com.ametrin.structures.AmetrinStructures;
import com.ametrin.structures.structure.jigsaw.ExtendedJigsawStructure;
import com.ametrin.structures.structure.simple.SimpleStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ASStructureTypes {
    public static final DeferredRegister<StructureType<?>> REGISTER = DeferredRegister.create(Registries.STRUCTURE_TYPE, AmetrinStructures.MOD_ID);

    public static final DeferredHolder<StructureType<?>, StructureType<ExtendedJigsawStructure>> EXTENDED_JIGSAW =
            REGISTER.register("extended_jigsaw", () -> () -> ExtendedJigsawStructure.CODEC);

    public static final DeferredHolder<StructureType<?>, StructureType<SimpleStructure>> SIMPLE =
            REGISTER.register("simple", () -> () -> SimpleStructure.CODEC);
}
