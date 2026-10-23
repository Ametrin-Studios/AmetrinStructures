package com.ametrin.structures.mixin;

import com.ametrin.structures.structure.jigsaw.JigsawStructureProcessors;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SinglePoolElement.class)
public class SinglePoolElementMixin {
    // The only Holder#value call in getSettings.
    @WrapOperation(method = "getSettings", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/Holder;value()Ljava/lang/Object;"))
    private Object ametrin$addStructureProcessors(Holder<StructureProcessorList> processors, Operation<Object> original) {
        return JigsawStructureProcessors.resolve((StructureProcessorList) original.call(processors));
    }
}
