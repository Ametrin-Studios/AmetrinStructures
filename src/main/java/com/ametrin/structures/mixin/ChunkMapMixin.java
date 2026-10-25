package com.ametrin.structures.mixin;

import com.ametrin.structures.placement.DimensionExclusions;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Filters the created state rather than the sets passed in, since a superflat world with structure overrides ignores those.
@Mixin(ChunkMap.class)
public class ChunkMapMixin {
    @Shadow
    @Final
    private ServerLevel level;

    @Shadow
    @Final
    private ChunkGeneratorStructureState chunkGeneratorState;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void ametrin$excludeStructures(CallbackInfo callback) {
        DimensionExclusions.apply(chunkGeneratorState, level.dimension());
    }
}
