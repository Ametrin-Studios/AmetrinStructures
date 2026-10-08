package com.ametrin.structures.foam;

import com.ametrin.structures.registry.ASTags;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/// Scaffolding that fills the space a structure should keep empty, removed when the structure generates.
/// How a fill spreads travels with the item as a [FoamSpread] component.
///
/// replacement state overwrites what the [RemoveFoamProcessor] specifies.
public class FoamBlock extends Block {
    public static final MapCodec<FoamBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    BlockState.CODEC.optionalFieldOf("replacement_state").forGetter(FoamBlock::replacementState),
                    propertiesCodec())
            .apply(instance, FoamBlock::new));

    private final Optional<BlockState> replacementState;

    public FoamBlock(Optional<BlockState> replacementState, Properties properties) {
        super(properties);
        this.replacementState = replacementState;
    }

    public FoamBlock(Properties properties) {
        this(Optional.empty(), properties);
    }

    public Optional<BlockState> replacementState() {
        return replacementState;
    }

    public static boolean isFoam(BlockState state) {
        return state.getBlock() instanceof FoamBlock || state.is(ASTags.Blocks.FOAM);
    }

    public static void dissolve(Level level, BlockPos pos) {
        // Removes the foam at `pos` next tick, and from there all foam connected to it, one layer a tick.
        var block = level.getBlockState(pos).getBlock();
        if (block instanceof FoamBlock && !level.getBlockTicks().hasScheduledTick(pos, block)) {
            level.scheduleTick(pos.immutable(), block, 1);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // The only scheduled tick foam gets is its dissolution. A placed foam doesn't know how its fill
        // spread, so it passes on to all 26 neighbors, and touching blobs dissolve together.
        level.removeBlock(pos, false);
        for (var neighbor : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
            dissolve(level, neighbor);
        }
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!stack.is(ASTags.Items.FOAM_DISSOLVER) || !player.getAbilities().instabuild) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            dissolve(level, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && !player.getAbilities().instabuild) {
            dissolve(level, pos);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    public static void fillOpening(BreakBlockEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !event.getPlayer().getAbilities().instabuild
                || isFoam(event.getState())) {
            return;
        }
        var pos = event.getPos().immutable();
        var foam = adjacentFoam(level, pos);
        if (foam == null) {
            return;
        }
        var server = level.getServer();
        // The block is still there while the event runs, so this waits for the break to finish.
        server.schedule(server.wrapRunnable(() -> {
            if (level.isEmptyBlock(pos)) {
                level.setBlock(pos, foam.defaultBlockState(), Block.UPDATE_ALL);
            }
        }));
    }

    // Spreaders don't count
    private static @Nullable FoamBlock adjacentFoam(Level level, BlockPos pos) {
        for (var direction : Direction.values()) {
            if (level.getBlockState(pos.relative(direction)).getBlock() instanceof FoamBlock foam
                    && !(foam instanceof FoamSpreaderBlock)) {
                return foam;
            }
        }
        return null;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!(context instanceof EntityCollisionContext entityContext) || entityContext.getEntity() == null) {
            return Shapes.block();
        }
        return entityContext.getEntity() instanceof Player player && !reachesThrough(player) ? Shapes.block() : Shapes.empty();
    }

    private static boolean reachesThrough(Player player) {
        return player.getAbilities().instabuild
                && !player.isCrouching()
                && !player.getMainHandItem().is(ASTags.Items.FOAM_INTERACTABLE);
    }

    // Foam becomes empty space, so nothing connects to or hangs on it, e.g. a fence or a pane inside a foam-filled room.
    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        // Placing a block into foam replaces it, unless the player aims at the foam itself.
        var player = context.getPlayer();
        return player == null || !player.isCrouching() && !context.getItemInHand().is(ASTags.Items.FOAM);
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacentState, Direction direction) {
        // Regions that become different blocks keep a visible border.
        return adjacentState.getBlock() instanceof FoamBlock other
                ? other.replacementState.equals(replacementState)
                : adjacentState.is(ASTags.Blocks.FOAM);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return true;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    public MapCodec<? extends FoamBlock> codec() {
        return CODEC;
    }
}
