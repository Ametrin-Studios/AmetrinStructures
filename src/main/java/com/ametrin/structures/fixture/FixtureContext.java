package com.ametrin.structures.fixture;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public record FixtureContext(
        BlockState markerState,
        BlockPos markerPos,
        Vec3 actionPos,
        BlockPos actionBlockPos,
        WorldGenLevel level,
        RandomSource random,
        BoundingBox pieceBounds,
        @Nullable StructurePiece piece,
        ChunkGenerator generator) {

    public boolean inBounds() {
        return pieceBounds.isInside(actionBlockPos);
    }
}
