package com.ametrin.structures.placement;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import java.util.function.Predicate;

public final class StructurePlacements {
    private StructurePlacements() {}

    public static boolean isTooCloseToCenter(int chunkX, int chunkZ, int minChunksFromCenter) {
        return (long) chunkX * chunkX + (long) chunkZ * chunkZ < (long) minChunksFromCenter * minChunksFromCenter;
    }

    public static boolean hasStructureChunkInRange(ChunkGeneratorStructureState state, Holder<Structure> structure, int x, int z, int range) {
        return hasStructureChunkInRange(state, _ -> true, holder -> holder.equals(structure), x, z, range);
    }

    public static boolean hasStructureChunkInRange(ChunkGeneratorStructureState state, HolderSet<Structure> structures, int x, int z, int range) {
        return hasStructureChunkInRange(state, _ -> true, structures::contains, x, z, range);
    }

    public static boolean hasStructureChunkInRange(ChunkGeneratorStructureState state, TagKey<Structure> structures, int x, int z, int range) {
        return hasStructureChunkInRange(state, _ -> true, holder -> holder.is(structures), x, z, range);
    }

    /// Checks the structure sets accepts that contain a structure `structures` accepts.
    public static boolean hasStructureChunkInRange(
            ChunkGeneratorStructureState state,
            Predicate<Holder<StructureSet>> sets,
            Predicate<Holder<Structure>> structures,
            int x,
            int z,
            int range) {
        for (var set : state.possibleStructureSets()) {
            var relevant = sets.test(set) && set.value().structures().stream().anyMatch(entry -> structures.test(entry.structure()));
            if (relevant && state.hasStructureChunkInRange(set, x, z, range)) {
                return true;
            }
        }
        return false;
    }
}
