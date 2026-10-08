package com.ametrin.structures.foam;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/// The offset sets of all four spread behaviors.
class FoamSpreadBehaviorTest {
    private static final BlockPos ORIGIN = BlockPos.ZERO;

    @Test
    void facesReachesTheSixFaceNeighbors() {
        List<BlockPos> offsets = FoamSpreadBehaviors.Faces.INSTANCE.offsets(FoamSpreadBehavior.Context.of(ORIGIN));
        assertEquals(6, offsets.size());
        assertEquals(6, Set.copyOf(offsets).size());
        for (Direction direction : Direction.values()) {
            assertTrue(offsets.contains(ORIGIN.relative(direction)), "missing " + direction);
        }
    }

    @Test
    void edgesReachesTwelveEdgeDiagonalsAndNoCorners() {
        List<BlockPos> offsets = FoamSpreadBehaviors.Edges.INSTANCE.offsets(FoamSpreadBehavior.Context.of(ORIGIN));
        assertEquals(12, offsets.size());
        assertEquals(12, Set.copyOf(offsets).size());
        for (BlockPos offset : offsets) {
            int nonZero = Math.abs(offset.getX()) + Math.abs(offset.getY()) + Math.abs(offset.getZ());
            assertEquals(2, nonZero, "edge diagonals move on exactly two axes: " + offset);
        }
        // Corner diagonals are deliberately excluded.
        assertFalse(offsets.contains(new BlockPos(1, 1, 1)));
    }

    @Test
    void facesAndEdgesIsTheUnionOfTheTwo() {
        List<BlockPos> offsets =
                FoamSpreadBehaviors.FacesAndEdges.INSTANCE.offsets(FoamSpreadBehavior.Context.of(ORIGIN));
        assertEquals(18, offsets.size());
        assertEquals(18, Set.copyOf(offsets).size());
    }

    @Test
    void planarDropsTheTwoNeighborsAlongTheRecordedAxis() {
        List<BlockPos> offsets = FoamSpreadBehaviors.Planar.INSTANCE.offsets(
                FoamSpreadBehavior.Context.of(ORIGIN, Direction.Axis.Y));
        assertEquals(4, offsets.size());
        assertFalse(offsets.contains(ORIGIN.above()));
        assertFalse(offsets.contains(ORIGIN.below()));
        assertTrue(offsets.contains(ORIGIN.north()));
        assertTrue(offsets.contains(ORIGIN.east()));
    }

    @Test
    void planarWithoutAnAxisFallsBackToFaces() {
        assertEquals(
                6, FoamSpreadBehaviors.Planar.INSTANCE.offsets(FoamSpreadBehavior.Context.of(ORIGIN)).size());
    }
}
