package com.ametrin.structures.foam;

import com.ametrin.structures.registry.ASBlocks;
import com.ametrin.structures.registry.ASDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/// Placing a stack with a [FoamSpread] component while sneaking places a [FoamSpreaderBlock], which fills the space around it as the component says.
public class FoamBlockItem extends BlockItem {
    public FoamBlockItem(FoamBlock block, Properties properties) {
        super(block, properties);
    }

    @Override
    @Nullable
    protected BlockState getPlacementState(BlockPlaceContext context) {
        var player = context.getPlayer();
        if (player == null || !player.isSecondaryUseActive() || !context.getItemInHand().has(ASDataComponents.FOAM_SPREAD)) {
            return super.getPlacementState(context);
        }
        // The axis comes from where the player is looking, which is how planar foam gets its plane.
        var spreader = ASBlocks.FOAM_SPREADER.get()
                .defaultBlockState()
                .setValue(FoamSpreaderBlock.AXIS, context.getNearestLookingDirection().getAxis());
        return canPlace(context, spreader) ? spreader : null;
    }

    // Vanilla doesn't show custom components. Hover text goes right under the item name, ahead of vanilla's component lines.
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        var spread = stack.get(ASDataComponents.FOAM_SPREAD);
        if (spread != null) {
            spread.addToTooltip(context, tooltip::add, flag);
        }
        super.appendHoverText(stack, context, tooltip, flag);
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

    @Override
    public @Nullable BlockPlaceContext updatePlacementContext(BlockPlaceContext context) {
        if (context.getLevel().isEmptyBlock(context.getClickedPos())) {
            return context;
        }
        if (!context.replacingClickedOnBlock()) {
            return null;
        }

        var next = BlockPlaceContext.at(context, context.getClickedPos().relative(context.getClickedFace()), context.getClickedFace());
        return next.getLevel().isEmptyBlock(next.getClickedPos()) ? next : null;
    }
}
