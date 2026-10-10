package com.ametrin.structures.fixture;

import com.ametrin.structures.registry.ASFixtures;
import com.ametrin.structures.structure.Foundation;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;

/// Grows a pillar from the action position down to the ground, like a leg or a support beam. Uses
/// `state` if set, otherwise it repeats the block directly above, so place the marker right below the
/// bottom block of the leg. A block that [can't be repeated][Foundation#canRepeat(BlockState)] grows
/// nothing. Like a [Foundation] column, it stops at the first block that isn't air, fluid or
/// replaceable, or after `max_depth` blocks.
public record ExtendDownFixture(Optional<BlockState> state, int maxDepth) implements Fixture {
    public static final FixtureField<Optional<BlockState>> STATE = FixtureField.optional("state", FieldType.blockState());
    public static final FixtureField<Integer> MAX_DEPTH = FixtureField.withDefault("max_depth", FieldType.integer(1, 512), Foundation.DEFAULT_MAX_DEPTH);
    public static final List<FixtureField<?>> FIELDS = List.of(STATE, MAX_DEPTH);
    public static final MapCodec<ExtendDownFixture> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    STATE.forGetter(ExtendDownFixture::state),
                    MAX_DEPTH.forGetter(ExtendDownFixture::maxDepth))
            .apply(instance, ExtendDownFixture::new));

    @Override
    public void apply(FixtureContext context) {
        var top = context.actionBlockPos();
        var fill = state.orElseGet(() -> context.level().getBlockState(top.above()));
        if (!Foundation.isFillable(fill) && (state.isPresent() || Foundation.canRepeat(fill))) {
            Foundation.fillDown(context.level(), top, fill, maxDepth, context.pieceBounds());
        }
    }

    @Override
    public FixtureType type() {
        return ASFixtures.EXTEND_DOWN.get();
    }
}
