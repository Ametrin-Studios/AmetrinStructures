package com.ametrin.structures.mixin;

import com.ametrin.structures.placement.LakeProof;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.LakeFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/// Lakes don't carve into structures in the lake-proof tag.
// Vanilla deprecates the lake feature, but still places its lava lakes with it.
@SuppressWarnings("deprecation")
@Mixin(LakeFeature.class)
public class LakeFeatureMixin {
    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void ametrin$skipLakeProofStructures(FeaturePlaceContext<LakeFeature.Configuration> context, CallbackInfoReturnable<Boolean> callback) {
        if (LakeProof.isLakeProofed(context.level(), context.origin())) {
            callback.setReturnValue(false);
        }
    }
}
