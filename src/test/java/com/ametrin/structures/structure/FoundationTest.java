package com.ametrin.structures.structure;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/// Which blocks a foundation column grows through, and the foundation as a datapack author writes it.
class FoundationTest {
    @Test
    void columnsGrowThroughAirFluidsAndReplaceables() {
        assertTrue(Foundation.isFillable(Blocks.AIR.defaultBlockState()));
        assertTrue(Foundation.isFillable(Blocks.WATER.defaultBlockState()));
        assertTrue(Foundation.isFillable(Blocks.SHORT_GRASS.defaultBlockState()));
        assertTrue(Foundation.isFillable(Blocks.SNOW.defaultBlockState()));
    }

    @Test
    void columnsStopAtGround() {
        assertFalse(Foundation.isFillable(Blocks.STONE.defaultBlockState()));
        assertFalse(Foundation.isFillable(Blocks.DIRT.defaultBlockState()));
        assertFalse(Foundation.isFillable(Blocks.OAK_LEAVES.defaultBlockState()));
    }

    @Test
    void columnsOnlyRepeatBlocksThatStandAlone() {
        assertTrue(Foundation.canRepeat(Blocks.STONE_BRICKS.defaultBlockState()));
        assertTrue(Foundation.canRepeat(Blocks.OAK_STAIRS.defaultBlockState()));
        assertFalse(Foundation.canRepeat(Blocks.CHEST.defaultBlockState()), "a block entity's data doesn't carry over");
        assertFalse(Foundation.canRepeat(Blocks.OAK_DOOR.defaultBlockState()));
        assertFalse(Foundation.canRepeat(Blocks.TALL_GRASS.defaultBlockState()));
    }

    @Test
    void anEmptyFoundationRepeatsTheBottomBlock() {
        var parsed = Foundation.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow();
        assertEquals(new Foundation(Optional.empty(), Foundation.DEFAULT_MAX_DEPTH), parsed);
    }

    @Test
    void aFoundationWithAStateRoundTrips() {
        var foundation = Foundation.of(Blocks.COBBLESTONE.defaultBlockState(), 20);
        var json = Foundation.CODEC.encodeStart(JsonOps.INSTANCE, foundation).getOrThrow();
        assertEquals(foundation, Foundation.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }
}
