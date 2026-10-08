package com.ametrin.structures.fixture;

import com.ametrin.structures.registry.ASBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Proxy;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(EphemeralTestServerProvider.class)
class FixtureSupportShapeTest {
    @Test
    void neighborsSeeTheBecomesState() {
        assertTrue(sturdy(Optional.of(Blocks.STONE.defaultBlockState())));
    }

    @Test
    void aMarkerBecomingAirSupportsNothing() {
        assertFalse(sturdy(Optional.empty()));
    }

    @Test
    void aMarkerBecomingAMarkerSupportsNothing() {
        assertFalse(sturdy(Optional.of(ASBlocks.FIXTURE.get().defaultBlockState())));
    }

    private static boolean sturdy(Optional<BlockState> becomes) {
        var state = ASBlocks.FIXTURE.get().defaultBlockState();
        var marker = new FixtureBlockEntity(BlockPos.ZERO, state);
        marker.setBecomes(becomes);
        return state.isFaceSturdy(level(state, marker), BlockPos.ZERO, Direction.NORTH);
    }

    // The shape only reads the marker's block entity and state.
    private static BlockGetter level(BlockState state, FixtureBlockEntity marker) {
        return (BlockGetter) Proxy.newProxyInstance(BlockGetter.class.getClassLoader(), new Class<?>[]{BlockGetter.class}, (_, method, _) -> switch (method.getName()) {
            case "getBlockEntity" -> marker;
            case "getBlockState" -> state;
            case "getFluidState" -> state.getFluidState();
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }
}
