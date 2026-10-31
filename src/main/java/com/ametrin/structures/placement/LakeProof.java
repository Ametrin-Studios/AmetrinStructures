package com.ametrin.structures.placement;

import com.ametrin.structures.registry.ASTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.ApiStatus;

/// Lakes don't form where they would reach into the pieces of structures tagged `#ametrin_structures:lake_proof`.
@ApiStatus.Internal
public final class LakeProof {
    private LakeProof() {}

    /// Whether a lake centered on `origin` would reach into a lake-proof structure.
    public static boolean isLakeProofed(WorldGenLevel level, BlockPos origin) {
        // The box LakeFeature carves: 16 x 8 x 16 blocks around its origin.
        var lake = new BoundingBox(
                origin.getX() - 8, origin.getY() - 4, origin.getZ() - 8,
                origin.getX() + 7, origin.getY() + 3, origin.getZ() + 7);
        return StructureLookup.intersectsTaggedStructure(level, lake, ASTags.Structures.LAKE_PROOF);
    }
}
