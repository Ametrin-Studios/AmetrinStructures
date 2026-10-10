package com.ametrin.structures.fixture;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
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

    /// Places `state` at the action position, merged with the marker's orientation and waterlogging.
    public void placeBlock(BlockState state) {
        var placed = BlockStateMerging.merge(markerState, state);
        level.setBlock(actionBlockPos, placed, Block.UPDATE_CLIENTS);
        updateNeighborShapes(placed);
    }

    // Worldgen doesn't update neighbors, yet a template's edge update may have split a double chest whose other half was still a marker.
    private void updateNeighborShapes(BlockState placed) {
        var neighborPos = new BlockPos.MutableBlockPos();
        for (var direction : Direction.values()) {
            neighborPos.setWithOffset(actionBlockPos, direction);
            var neighbor = level.getBlockState(neighborPos);
            var updated = neighbor.updateShape(level, level, neighborPos, direction.getOpposite(), actionBlockPos, placed, random);
            if (updated != neighbor) {
                level.setBlock(neighborPos, updated, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
    }
}
