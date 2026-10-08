package com.ametrin.structures.foam;

import com.ametrin.structures.registry.ASBlockEntities;
import com.ametrin.structures.registry.ASBlocks;
import com.ametrin.structures.registry.ASDataComponents;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayDeque;

/// At most [#SOURCES_PER_TICK] foam spread per tick, so a large fill takes several ticks instead of stalling the server.
///
/// The fill stops at chunks that aren't loaded.
@ApiStatus.Internal
public class FoamSpreaderBlockEntity extends BlockEntity {
    public static final int SOURCES_PER_TICK = 512;

    private static final String STACK_KEY = "stack";
    private static final String FRONTIER_KEY = "frontier";
    // Foam is never solid to its neighbors, so placing it needs no neighbor updates.
    private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private ItemStack stack = ItemStack.EMPTY;
    private final ArrayDeque<BlockPos> frontier = new ArrayDeque<>();

    public FoamSpreaderBlockEntity(BlockPos pos, BlockState state) {
        super(ASBlockEntities.FOAM_SPREADER.get(), pos, state);
        frontier.add(pos);
    }

    /// the stack that holds the [FoamSpread] (restrictions may use its count).
    public void configure(ItemStack stack) {
        this.stack = stack.copy();
        setChanged();
    }

    public FoamSpread spread() {
        return stack.getOrDefault(ASDataComponents.FOAM_SPREAD, FoamPresets.BASIC);
    }

    public FoamBlock foam() {
        return stack.getItem() instanceof BlockItem item && item.getBlock() instanceof FoamBlock foam
                ? foam
                : ASBlocks.FOAM.get();
    }

    void tick(ServerLevel level, BlockState state) {
        var spread = spread();
        var axis = state.getValue(FoamSpreaderBlock.AXIS);
        var foam = foam().defaultBlockState();
        for (int i = 0; i < SOURCES_PER_TICK && !frontier.isEmpty(); i++) {
            var source = frontier.poll();
            for (var candidate : spread.behavior().offsets(FoamSpreadBehavior.Context.of(source, axis))) {
                if (level.isLoaded(candidate)
                        && level.isEmptyBlock(candidate)
                        && spread.permits(level, worldPosition, stack, candidate)
                        && level.setBlock(candidate, foam, PLACE_FLAGS)) {
                    frontier.add(candidate.immutable());
                }
            }
        }
        if (frontier.isEmpty()) {
            level.setBlock(worldPosition, foam, Block.UPDATE_ALL);
        } else {
            setChanged();
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        stack = input.read(STACK_KEY, ItemStack.CODEC).orElse(ItemStack.EMPTY);
        input.read(FRONTIER_KEY, Codec.LONG_STREAM).ifPresent(positions -> {
            frontier.clear();
            positions.mapToObj(BlockPos::of).forEach(frontier::add);
        });
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!stack.isEmpty()) {
            output.store(STACK_KEY, ItemStack.CODEC, stack);
        }
        output.store(FRONTIER_KEY, Codec.LONG_STREAM, frontier.stream().mapToLong(BlockPos::asLong));
    }
}
