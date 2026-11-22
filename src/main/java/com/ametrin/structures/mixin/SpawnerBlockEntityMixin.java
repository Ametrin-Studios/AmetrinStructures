package com.ametrin.structures.mixin;

import com.ametrin.structures.spawner.SpawnerProfiles;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpawnerBlockEntity.class)
public abstract class SpawnerBlockEntityMixin {
    @Shadow
    @Final
    private BaseSpawner spawner;

    // The lookup the load was handed is the only registry access available: no level is attached yet.
    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void ametrin$applySpawnerProfile(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo callback) {
        SpawnerProfiles.applyAttached((SpawnerBlockEntity) (Object) this, spawner, registries);
    }
}
