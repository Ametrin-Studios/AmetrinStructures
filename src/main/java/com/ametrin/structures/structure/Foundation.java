package com.ametrin.structures.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.Fluids;

import java.util.Optional;

/// Extends a structure down to the ground where it would otherwise float.
/// Each block of a piece's bottom layer is extended down through replaceable blocks, for at most `maxDepth` blocks.
///
/// @param state what to fill with. If empty, each column repeats the block above it, unless it [can't be repeated][#canRepeat(BlockState)].
public record Foundation(Optional<BlockState> state, int maxDepth) {
    public static final int DEFAULT_MAX_DEPTH = 64;

    public static final Codec<Foundation> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    BlockState.CODEC.optionalFieldOf("state").forGetter(Foundation::state),
                    Codec.intRange(1, 512).optionalFieldOf("max_depth", DEFAULT_MAX_DEPTH).forGetter(Foundation::maxDepth))
            .apply(instance, Foundation::new));

    public static Foundation repeating() {
        return new Foundation(Optional.empty(), DEFAULT_MAX_DEPTH);
    }

    public static Foundation of(BlockState state, int maxDepth) {
        return new Foundation(Optional.of(state), maxDepth);
    }

    public void placeUnder(WorldGenLevel level, BoundingBox pieceBox, BoundingBox chunkBox) {
        int minX = Math.max(pieceBox.minX(), chunkBox.minX());
        int maxX = Math.min(pieceBox.maxX(), chunkBox.maxX());
        int minZ = Math.max(pieceBox.minZ(), chunkBox.minZ());
        int maxZ = Math.min(pieceBox.maxZ(), chunkBox.maxZ());
        var bottom = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                bottom.set(x, pieceBox.minY(), z);
                BlockState hanging = level.getBlockState(bottom);
                if (!isFillable(hanging) && (state.isPresent() || canRepeat(hanging))) {
                    fillDown(level, bottom.below(), state.orElse(hanging), maxDepth, chunkBox);
                }
            }
        }
    }

    /// Fills from `top` downward with `fill` while the blocks are [fillable][#isFillable], within `bounds`.
    public static void fillDown(WorldGenLevel level, BlockPos top, BlockState fill, int maxDepth, BoundingBox bounds) {
        var pos = top.mutable();
        int floor = Math.max(level.getMinY(), top.getY() - maxDepth + 1);
        for (; pos.getY() >= floor && bounds.isInside(pos); pos.move(0, -1, 0)) {
            BlockState replaced = level.getBlockState(pos);
            if (!isFillable(replaced)) {
                return;
            }
            level.setBlock(pos, waterloggedLike(fill, replaced), Block.UPDATE_CLIENTS);
        }
    }

    /// Whether a column can repeat `state`. Not if it has a block entity, whose data wouldn't be copied,
    /// or is part of a multi-block, like a door or a bed.
    public static boolean canRepeat(BlockState state) {
        return !state.hasBlockEntity()
                && !state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && !state.hasProperty(BlockStateProperties.BED_PART);
    }

    public static boolean isFillable(BlockState state) {
        return state.isAir() || !state.getFluidState().isEmpty() || state.canBeReplaced();
    }

    private static BlockState waterloggedLike(BlockState fill, BlockState replaced) {
        return fill.hasProperty(BlockStateProperties.WATERLOGGED)
                ? fill.setValue(BlockStateProperties.WATERLOGGED, replaced.getFluidState().is(Fluids.WATER))
                : fill;
    }
}
