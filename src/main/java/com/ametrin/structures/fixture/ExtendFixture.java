package com.ametrin.structures.fixture;

import com.ametrin.structures.registry.ASFixtures;
import com.ametrin.structures.structure.Foundation;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;

/// Grows a column from the action position up or down, like a leg, a support beam or a chain. Uses `state` if set, otherwise it
/// repeats the block on the other side, so place the marker right after the last block to extend. A block that
/// [can't be repeated][Foundation#canRepeat(BlockState)] grows nothing. Like a [Foundation] column, it stops at the first block
/// that isn't air, fluid or replaceable, or after `max_depth` blocks.
public record ExtendFixture(Vertical direction, Optional<BlockState> state, int maxDepth) implements Fixture {
    public static final FixtureField<Vertical> DIRECTION = FixtureField.withDefault("direction", FieldType.choice(Vertical.class), Vertical.DOWN);
    public static final FixtureField<Optional<BlockState>> STATE = FixtureField.optional("state", FieldType.blockState());
    public static final FixtureField<Integer> MAX_DEPTH = FixtureField.withDefault("max_depth", FieldType.integer(1, 512), Foundation.DEFAULT_MAX_DEPTH);
    public static final List<FixtureField<?>> FIELDS = List.of(DIRECTION, STATE, MAX_DEPTH);
    public static final MapCodec<ExtendFixture> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    DIRECTION.forGetter(ExtendFixture::direction),
                    STATE.forGetter(ExtendFixture::state),
                    MAX_DEPTH.forGetter(ExtendFixture::maxDepth))
            .apply(instance, ExtendFixture::new));

    @Override
    public void apply(FixtureContext context) {
        var start = context.actionBlockPos();
        var toward = direction.direction();
        var fill = state.orElseGet(() -> context.level().getBlockState(start.relative(toward.getOpposite())));
        if (!Foundation.isFillable(fill) && (state.isPresent() || Foundation.canRepeat(fill))) {
            Foundation.extend(context.level(), start, toward, fill, maxDepth, context.pieceBounds());
        }
    }

    @Override
    public FixtureType type() {
        return ASFixtures.EXTEND.get();
    }

    // Sideways would cross into chunks that are placed separately, so only up and down.
    public enum Vertical implements StringRepresentable {
        UP(Direction.UP),
        DOWN(Direction.DOWN);

        private final Direction direction;

        Vertical(Direction direction) {
            this.direction = direction;
        }

        public Direction direction() {
            return direction;
        }

        @Override
        public String getSerializedName() {
            return direction.getSerializedName();
        }
    }
}
