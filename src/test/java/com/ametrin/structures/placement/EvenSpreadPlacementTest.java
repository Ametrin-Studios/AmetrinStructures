package com.ametrin.structures.placement;

import net.minecraft.resources.Identifier;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EvenSpreadPlacementTest {
    private static final long SEED = 12345L;
    private static final int CELLS = 40;

    private static EvenSpreadPlacement.Builder evenSpread(int minDistance) {
        return EvenSpreadPlacement.builder().salt("test", "structure").minDistance(minDistance);
    }

    private static List<ChunkPos> candidates(EvenSpreadPlacement placement, long seed) {
        var candidates = new ArrayList<ChunkPos>();
        for (int cellX = -CELLS; cellX < CELLS; cellX++) {
            for (int cellZ = -CELLS; cellZ < CELLS; cellZ++) {
                candidates.add(placement.getPotentialStructureChunk(seed, cellX * placement.spacing(), cellZ * placement.spacing()));
            }
        }
        return candidates;
    }

    private static List<ChunkPos> spots(EvenSpreadPlacement placement, long seed) {
        return candidates(placement, seed).stream().filter(chunk -> placement.applyAdditionalChunkRestrictions(chunk.x(), chunk.z(), seed)).toList();
    }

    private static double distance(ChunkPos a, ChunkPos b) {
        return Math.hypot(a.x() - b.x(), a.z() - b.z());
    }

    @Test
    void noTwoSpotsAreCloserThanTheMinDistance() {
        for (int minDistance : new int[]{2, 3, 13, 24}) {
            var spots = spots(evenSpread(minDistance).build(), SEED);
            for (var spot : spots) {
                for (var other : spots) {
                    assertTrue(spot == other || distance(spot, other) >= minDistance, minDistance + ": " + spot + " and " + other);
                }
            }
        }
    }

    /// The thinning leaves no holes.
    @Test
    void onlyANearbySpotDropsACandidate() {
        var placement = evenSpread(24).minChunksFromCenter(100).build();
        var spots = spots(placement, SEED);
        for (var candidate : candidates(placement, SEED)) {
            if (placement.isTooCloseToCenter(candidate.x(), candidate.z()) || spots.contains(candidate)
                    || Math.abs(candidate.x()) > (CELLS - 3) * placement.spacing() || Math.abs(candidate.z()) > (CELLS - 3) * placement.spacing()) {
                continue;
            }
            assertTrue(spots.stream().anyMatch(spot -> distance(spot, candidate) < 24), "nothing dropped " + candidate);
        }
    }

    @Test
    void spotsAreAboutOneAndAThirdMinDistancesApart() {
        var placement = evenSpread(24).build();
        int side = 2 * CELLS * placement.spacing();
        double averageSpacing = Math.sqrt((double) side * side / spots(placement, SEED).size());
        assertEquals(24 * 1.3, averageSpacing, 24 * 0.1);
    }

    @Test
    void spotsDependOnTheSeedAndSalt() {
        var placement = evenSpread(24).build();
        assertEquals(spots(placement, SEED), spots(evenSpread(24).build(), SEED));
        assertNotEquals(spots(placement, SEED), spots(placement, SEED + 1));
        assertNotEquals(spots(placement, SEED), spots(EvenSpreadPlacement.builder().salt("test", "other").minDistance(24).build(), SEED));
    }

    @Test
    void aSharedBuilderTakesEachSetsDefaultSalt() {
        var shared = EvenSpreadPlacement.builder().minDistance(24);
        var first = shared.build("test", "first");
        assertNotEquals(spots(first, SEED), spots(shared.build("test", "second"), SEED));
        assertEquals(spots(first, SEED), spots(shared.build(Identifier.fromNamespaceAndPath("test", "first")), SEED));
        assertEquals(spots(evenSpread(24).salt(7).build(), SEED), spots(shared.salt(7).build("test", "third"), SEED), "an explicit salt wins");
    }

    @Test
    void theProbabilityOnlyRemovesSpots() {
        var all = spots(evenSpread(24).build(), SEED);
        var some = spots(evenSpread(24).probability(0.5F).build(), SEED);
        assertTrue(all.containsAll(some));
        assertTrue(some.size() < all.size() * 0.6 && some.size() > all.size() * 0.4, some.size() + " of " + all.size());
    }

    @Test
    void locateWalksTheCandidateCells() {
        var placement = evenSpread(24).build();
        assertInstanceOf(RandomSpreadStructurePlacement.class, placement);
        assertEquals(12, placement.spacing());
        assertEquals(1, evenSpread(1).build().spacing());
    }

    @Test
    void nothingGeneratesNearTheCenter() {
        var placement = evenSpread(8).minChunksFromCenter(50).build();
        assertTrue(spots(placement, SEED).stream().noneMatch(spot -> placement.isTooCloseToCenter(spot.x(), spot.z())));
        assertFalse(placement.applyAdditionalChunkRestrictions(0, 0, SEED));
    }

    @Test
    void outOfRangeValuesAreRejected() {
        assertThrows(IllegalStateException.class, () -> evenSpread(0).build());
        assertThrows(IllegalStateException.class, () -> evenSpread(EvenSpreadPlacement.MAX_DISTANCE + 1).build());
        assertThrows(IllegalStateException.class, () -> evenSpread(8).probability(1.5F).build());
        assertThrows(IllegalStateException.class, () -> evenSpread(8).minChunksFromCenter(-1).build());
        assertThrows(IllegalStateException.class, () -> evenSpread(8).exclusionZone(StructureTags.VILLAGE, TagExclusionZone.MAX_CHUNK_COUNT + 1).build());
    }
}
