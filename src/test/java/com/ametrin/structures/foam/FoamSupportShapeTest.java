package com.ametrin.structures.foam;

import com.ametrin.structures.registry.ASBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertFalse;

@ExtendWith(EphemeralTestServerProvider.class)
class FoamSupportShapeTest {
    @Test
    void foamSupportsNothing() {
        for (var block : new FoamBlock[]{ASBlocks.FOAM.get(), ASBlocks.FOAM_SPREADER.get()}) {
            assertFalse(block.defaultBlockState().isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, Direction.NORTH));
        }
    }

    @Test
    void fencesDoNotConnectToFoam() {
        var fence = (FenceBlock) Blocks.OAK_FENCE;
        var foam = ASBlocks.FOAM.get().defaultBlockState();
        assertFalse(fence.connectsTo(foam, foam.isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, Direction.SOUTH), Direction.SOUTH));
    }
}
