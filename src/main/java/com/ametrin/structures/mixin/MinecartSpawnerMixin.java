package com.ametrin.structures.mixin;

import com.ametrin.structures.spawner.SpawnerProfiles;
import net.minecraft.world.entity.vehicle.minecart.MinecartSpawner;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.storage.ValueInput;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecartSpawner.class)
public abstract class MinecartSpawnerMixin {
    @Shadow
    @Final
    private BaseSpawner spawner;

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void ametrin$applySpawnerProfile(ValueInput input, CallbackInfo callback) {
        var self = (MinecartSpawner) (Object) this;
        SpawnerProfiles.applyAttached(self, spawner, self.level().registryAccess());
    }
}
