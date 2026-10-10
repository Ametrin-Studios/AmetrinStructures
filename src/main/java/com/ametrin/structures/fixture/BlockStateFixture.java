package com.ametrin.structures.fixture;

import com.ametrin.structures.registry.ASFixtures;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/// Any block state, with the marker's orientation merged in.
public record BlockStateFixture(BlockState state) implements Fixture {
    public static final FixtureField<BlockState> STATE = FixtureField.required("state", BlockStateMerging.mergedBlockState());
    public static final List<FixtureField<?>> FIELDS = List.of(STATE);
    public static final MapCodec<BlockStateFixture> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    STATE.forGetter(BlockStateFixture::state))
            .apply(instance, BlockStateFixture::new));

    @Override
    public void apply(FixtureContext context) {
        context.placeBlock(state);
    }

    @Override
    public FixtureType type() {
        return ASFixtures.BLOCK_STATE.get();
    }
}
