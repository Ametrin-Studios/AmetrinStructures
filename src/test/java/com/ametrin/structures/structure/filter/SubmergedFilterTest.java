package com.ametrin.structures.structure.filter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Water above a structure, from the first free Y of the world surface and the ocean floor.
class SubmergedFilterTest {
    private static final int SEA_LEVEL_SURFACE = 63;

    @Test
    void countsTheWaterBetweenTheRoofAndTheSurface() {
        // Roof at 50, so water fills 51..62.
        assertEquals(12, PlacementFilters.Submerged.waterAbove(SEA_LEVEL_SURFACE, 40, 50));
    }

    @Test
    void aStructureUnderTheSeaFloorOnlyCountsTheWaterAboveTheFloor() {
        assertEquals(23, PlacementFilters.Submerged.waterAbove(SEA_LEVEL_SURFACE, 40, 30));
    }

    @Test
    void onLandThereIsNoWater() {
        // Both heightmaps agree where the surface is dry ground.
        assertEquals(0, PlacementFilters.Submerged.waterAbove(80, 80, 60));
    }

    @Test
    void aStructureReachingOutOfTheWaterHasNone() {
        assertEquals(0, PlacementFilters.Submerged.waterAbove(SEA_LEVEL_SURFACE, 40, 70));
    }
}
