package com.ametrin.structures.fixture;

import com.ametrin.structures.client.FixtureScreen;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.FrontAndTop;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

/// A marker an author places inside a template.
/// When the structure generates, the marker performs an action and removes itself.
@ApiStatus.Internal
public class FixtureBlock extends BaseEntityBlock implements GameMasterBlock, SimpleWaterloggedBlock {
    public static final MapCodec<FixtureBlock> CODEC = simpleCodec(FixtureBlock::new);

    public static final EnumProperty<FrontAndTop> ORIENTATION = BlockStateProperties.ORIENTATION;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public FixtureBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition
                .any()
                .setValue(ORIENTATION, FrontAndTop.NORTH_UP)
                .setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ORIENTATION, WATERLOGGED);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FixtureBlockEntity(pos, state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        boolean waterlogged = context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER;
        return defaultBlockState()
                .setValue(ORIENTATION, orientation(context))
                .setValue(WATERLOGGED, waterlogged);
    }

    // Faces the player. When facing up or down, the top points toward the player, so horizontal blocks placed from it do too.
    private static FrontAndTop orientation(BlockPlaceContext context) {
        var front = context.getNearestLookingDirection().getOpposite();
        var top = front.getAxis() == Direction.Axis.Y ? context.getHorizontalDirection().getOpposite() : Direction.UP;
        return FrontAndTop.fromFrontAndTop(front, top);
    }

    public static Direction front(BlockState state) {
        return state.getValue(ORIENTATION).front();
    }

    public static Direction horizontalFacing(BlockState state) {
        var orientation = state.getValue(ORIENTATION);
        return orientation.front().getAxis().isHorizontal() ? orientation.front() : orientation.top();
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(ORIENTATION, rotation.rotation().rotate(state.getValue(ORIENTATION)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(ORIENTATION, mirror.rotation().rotate(state.getValue(ORIENTATION)));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof FixtureBlockEntity marker) || !player.canUseGameMasterBlocks()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            FixtureScreen.open(marker);
        }
        return InteractionResult.SUCCESS;
    }

    // Fences, walls and panes connect to sturdy faces. Neighbors see the state the marker becomes, so a template saves them as they'll look once generated.
    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof FixtureBlockEntity marker) {
            var becomes = marker.becomes();
            // A marker that becomes a marker would ask itself again.
            if (!(becomes.getBlock() instanceof FixtureBlock)) {
                return becomes.getBlockSupportShape(level, pos);
            }
        }
        return Shapes.empty();
    }

    // A picked marker's data loads after its neighbors updated, so update them again for its becomes state.
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
        if (!level.isClientSide()
                && level.getBlockEntity(pos) instanceof FixtureBlockEntity marker
                && marker.declaredBecomes().isPresent()) {
            state.updateNeighbourShapes(level, pos, Block.UPDATE_ALL);
        }
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return !state.getValue(WATERLOGGED);
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }
}
