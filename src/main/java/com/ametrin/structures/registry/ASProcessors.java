package com.ametrin.structures.registry;

import com.ametrin.structures.AmetrinStructures;
import com.ametrin.structures.foam.RemoveFoamProcessor;
import com.ametrin.structures.processor.ReplaceBlockProcessor;
import com.ametrin.structures.processor.RetainExistingProcessor;
import com.ametrin.structures.structure.simple.InlineFromStructureProcessor;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ASProcessors {
    public static final DeferredRegister<MapCodec<? extends StructureProcessor>> REGISTER = DeferredRegister.create(Registries.STRUCTURE_PROCESSOR, AmetrinStructures.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends StructureProcessor>, MapCodec<RemoveFoamProcessor>> REMOVE_FOAM =
            REGISTER.register("remove_foam", () -> RemoveFoamProcessor.CODEC);
    public static final DeferredHolder<MapCodec<? extends StructureProcessor>, MapCodec<ReplaceBlockProcessor>> REPLACE_BLOCK =
            REGISTER.register("replace_block", () -> ReplaceBlockProcessor.CODEC);
    public static final DeferredHolder<MapCodec<? extends StructureProcessor>, MapCodec<RetainExistingProcessor>> RETAIN_EXISTING =
            REGISTER.register("retain_existing", () -> RetainExistingProcessor.CODEC);
    public static final DeferredHolder<MapCodec<? extends StructureProcessor>, MapCodec<InlineFromStructureProcessor>> INLINE_FROM_STRUCTURE =
            REGISTER.register("inline_from_structure", () -> InlineFromStructureProcessor.CODEC);
}
