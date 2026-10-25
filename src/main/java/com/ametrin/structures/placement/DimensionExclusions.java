package com.ametrin.structures.placement;

import com.ametrin.structures.mixin.ChunkGeneratorStructureStateAccessor;
import com.ametrin.structures.registry.ASDataMaps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class DimensionExclusions {
    private DimensionExclusions() {}

    /// Drops the sets [ASDataMaps#EXCLUDED_DIMENSIONS] excludes from `dimension`. Must run before the state places anything.
    public static void apply(ChunkGeneratorStructureState state, ResourceKey<Level> dimension) {
        var sets = state.possibleStructureSets();
        var kept = sets.stream()
                .filter(set -> {
                    var excluded = set.getData(ASDataMaps.EXCLUDED_DIMENSIONS);
                    return excluded == null || !excluded.contains(dimension);
                })
                .toList();
        if (kept.size() != sets.size()) {
            ((ChunkGeneratorStructureStateAccessor) state).ametrin$setPossibleStructureSets(kept);
        }
    }
}
