package com.ametrin.structures.foam;

import com.ametrin.structures.registry.ASBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

/// Foam that's still filling the space around it, see [FoamSpreaderBlockEntity].
/// It turns into plain foam once the fill is done. Otherwise it behaves like foam, including dissolving.
@ApiStatus.Internal
public class FoamSpreaderBlock extends FoamBlock implements EntityBlock {
    /// The axis the builder looked along, which planar spreads fill across.
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;

    public FoamSpreaderBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.Y));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FoamSpreaderBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ASBlockEntities.FOAM_SPREADER.get()) {
            return null;
        }
        return (BlockEntityTicker<T>) (BlockEntityTicker<FoamSpreaderBlockEntity>) (tickLevel, pos, tickState, spreader) -> spreader.tick((ServerLevel) tickLevel, tickState);
    }
}
