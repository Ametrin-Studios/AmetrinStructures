package com.ametrin.structures.foam;

import com.ametrin.structures.registry.ASBlocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/// Placing it while sneaking places a [FoamSpreaderBlock], which fills the space around it as the stack's [FoamSpread] component says.
public class FoamBlockItem extends BlockItem {
    public FoamBlockItem(FoamBlock block, Properties properties) {
        super(block, properties);
    }

    @Override
    @Nullable
    protected BlockState getPlacementState(BlockPlaceContext context) {
        var player = context.getPlayer();
        if (player == null || !player.isCrouching()) {
            return super.getPlacementState(context);
        }
        // The axis comes from where the player is looking, which is how planar foam gets its plane.
        var spreader = ASBlocks.FOAM_SPREADER.get()
                .defaultBlockState()
                .setValue(FoamSpreaderBlock.AXIS, context.getNearestLookingDirection().getAxis());
        return canPlace(context, spreader) ? spreader : null;
    }

    @Override
    protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        boolean placed = super.placeBlock(context, state);
        if (placed && context.getLevel().getBlockEntity(context.getClickedPos())
                instanceof FoamSpreaderBlockEntity spreader) {
            spreader.configure(context.getItemInHand());
        }
        return placed;
    }
}
