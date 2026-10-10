package com.ametrin.structures.placement;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/// Grid chunk selection: one structure per cell, moved a seeded random amount up to the offset.
class ScatteredGridPlacementTest {
    private static final Identifier STRUCTURE = Identifier.fromNamespaceAndPath("test", "structure");
    private static final long SEED = 12345L;

    private static ScatteredGridPlacement.Builder grid(int spacing) {
        return ScatteredGridPlacement.builder().salt(STRUCTURE.getNamespace(), STRUCTURE.getPath()).spacing(spacing);
    }

    @Test
    void offsetIsClampedIntoTheCell() {
        assertEquals(7, grid(8).randomOffset(99).build().randomOffset());
    }

    @Test
    void randomOffsetZeroIsAStrictGrid() {
        ScatteredGridPlacement placement = grid(16).gridOffset(0, 0).randomOffset(0).build();
        assertEquals(new ChunkPos(0, 0), placement.getPotentialStructureChunk(SEED, 3, 9));
        assertEquals(new ChunkPos(-16, 16), placement.getPotentialStructureChunk(SEED, -3, 20));
    }

    @Test
    void structuresStayWithinTheOffsetOfTheirCellCorner() {
        ScatteredGridPlacement placement = grid(16).gridOffset(0, 0).randomOffset(3).build();
        for (int cellX = -8; cellX < 8; cellX++) {
            for (int cellZ = -8; cellZ < 8; cellZ++) {
                ChunkPos chunk = placement.getPotentialStructureChunk(SEED, cellX * 16 + 5, cellZ * 16 + 5);
                int dx = chunk.x() - cellX * 16;
                int dz = chunk.z() - cellZ * 16;
                assertTrue(dx >= 0 && dx <= 3 && dz >= 0 && dz <= 3, "cell " + cellX + "," + cellZ + " picked " + chunk);
            }
        }
    }

    @Test
    void theOffsetBreaksUpThePatternDeterministically() {
        ScatteredGridPlacement placement = grid(16).gridOffset(0, 0).build();
        Set<ChunkPos> localPositions = new HashSet<>();
        for (int cell = 0; cell < 32; cell++) {
            ChunkPos chunk = placement.getPotentialStructureChunk(SEED, cell * 16, 0);
            assertEquals(chunk, placement.getPotentialStructureChunk(SEED, cell * 16 + 7, 9), "same cell, same chunk");
            localPositions.add(new ChunkPos(chunk.x() - cell * 16, chunk.z()));
        }
        assertTrue(localPositions.size() > 1, "every cell picked the same spot: " + localPositions);
    }

    @Test
    void theGridOffsetShiftsTheCells() {
        ScatteredGridPlacement placement = grid(16).gridOffset(4, 6).randomOffset(0).build();
        assertEquals(new ChunkPos(4, 6), placement.getPotentialStructureChunk(SEED, 10, 10));
        assertEquals(new ChunkPos(-12, 6), placement.getPotentialStructureChunk(SEED, 3, 21));
        assertEquals(new ChunkPos(20, -10), placement.getPotentialStructureChunk(SEED, 35, 5));
    }

    /// The reason for the grid offset: structures sharing a spacing must not stack on the same corners.
    @Test
    void structuresSharingAGridDoNotCluster() {
        ScatteredGridPlacement first = grid(16).randomOffset(0).build();
        ScatteredGridPlacement second = ScatteredGridPlacement.builder().salt("test", "other")
                .spacing(16).randomOffset(0).build();
        assertNotEquals(first.gridOffset(SEED), second.gridOffset(SEED));
        assertNotEquals(first.gridOffset(SEED), first.gridOffset(SEED + 1), "a new world, a new grid");
        assertEquals(first.gridOffset(SEED), first.gridOffset(SEED), "the same world, the same grid");
    }

    /// Vanilla's structure search only walks random spread placements, asking each for its chunk per cell.
    @Test
    void locateSeesTheGridThroughTheRandomSpreadContract() {
        assertInstanceOf(RandomSpreadStructurePlacement.class, grid(16).build());
    }

    @Test
    void saltIsDerivedFromTheIdUnlessGiven() {
        assertNotEquals(picks(grid(16).build()), picks(ScatteredGridPlacement.builder().salt("test", "other").spacing(16).build()));
        assertEquals(
                picks(grid(16).salt(7).build()),
                picks(ScatteredGridPlacement.builder().salt("test", "other").salt(7).spacing(16).build()),
                "an explicit salt wins over the derived one");
    }

    private static List<ChunkPos> picks(ScatteredGridPlacement placement) {
        return IntStream.range(0, 16).mapToObj(cell -> placement.getPotentialStructureChunk(SEED, cell * 16, 0)).toList();
    }

    @Test
    void minChunksFromCenterIsAStraightLineDistance() {
        ScatteredGridPlacement placement = grid(16).minChunksFromCenter(10).build();
        assertTrue(placement.isTooCloseToCenter(0, 0));
        assertTrue(placement.isTooCloseToCenter(9, 0));
        assertTrue(placement.isTooCloseToCenter(-7, 7), "the corner of the square is still inside the circle");
        assertFalse(placement.isTooCloseToCenter(0, -10));
        assertFalse(placement.isTooCloseToCenter(8, 8));
    }

    @Test
    void theChunkRestrictionsKeepAwayFromTheCenter() {
        ScatteredGridPlacement placement = grid(16).minChunksFromCenter(10).build();
        assertFalse(placement.applyAdditionalChunkRestrictions(9, 0, SEED));
        assertTrue(placement.applyAdditionalChunkRestrictions(0, -10, SEED));
    }

    @Test
    void zeroMinChunksFromCenterAllowsTheOrigin() {
        assertFalse(grid(16).build().isTooCloseToCenter(0, 0));
    }

    @Test
    void negativeMinChunksFromCenterIsRejected() {
        assertThrows(IllegalStateException.class, () -> grid(16).minChunksFromCenter(-1).build());
    }

    @Test
    void outOfRangeSpacingIsRejected() {
        assertThrows(IllegalStateException.class, () -> grid(0).build());
        assertThrows(IllegalStateException.class, () -> grid(ScatteredGridPlacement.MAX_SPACING + 1).build());
    }
}
