package com.ametrin.structures.mixin;

import com.ametrin.structures.fixture.FixtureBlockEntity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// NeoForge 21.1 has no hook that turns block entity data with the template.
@Mixin(StructureTemplate.class)
public abstract class StructureTemplateMixin {
    @WrapOperation(method = "placeInWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/BlockEntity;loadWithComponents(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V"))
    private void ametrin$applyStructureRotation(BlockEntity blockEntity, CompoundTag tag, HolderLookup.Provider registries, Operation<Void> original, @Local(argsOnly = true) StructurePlaceSettings settings) {
        original.call(blockEntity, tag, registries);
        if (blockEntity instanceof FixtureBlockEntity fixture) {
            fixture.applyStructureRotation(settings.getMirror(), settings.getRotation());
        }
    }
}
