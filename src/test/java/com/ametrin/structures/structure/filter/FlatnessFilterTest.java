package com.ametrin.structures.structure.filter;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Where the flatness filter samples, and how a datapack author writes it.
class FlatnessFilterTest {
    private static final BoundingBox FOOTPRINT = new BoundingBox(0, 60, 0, 20, 70, 10);

    @Test
    void marginDefaultsToZero() {
        assertEquals(new PlacementFilters.Flatness(3, 0), parse("{\"max_variance\":3}"));
        assertEquals(new PlacementFilters.Flatness(3, -4), parse("{\"max_variance\":3,\"margin\":-4}"));
    }

    @Test
    void marginOutsideTheRangeIsRejected() {
        var result = PlacementFilters.Flatness.CODEC.codec()
                .parse(JsonOps.INSTANCE, JsonParser.parseString("{\"max_variance\":3,\"margin\":-65}"));
        assertTrue(result.isError());
    }

    @Test
    void aPositiveMarginWidensTheFootprint() {
        assertEquals(new BoundingBox(-2, 60, -2, 22, 70, 12), new PlacementFilters.Flatness(3, 2).sampledArea(FOOTPRINT));
    }

    @Test
    void aNegativeMarginNarrowsItDownToTheCenter() {
        assertEquals(new BoundingBox(3, 60, 3, 17, 70, 7), new PlacementFilters.Flatness(3, -3).sampledArea(FOOTPRINT));
        // The center is (10, 5): past it, each axis stops there.
        assertEquals(new BoundingBox(7, 60, 5, 13, 70, 5), new PlacementFilters.Flatness(3, -7).sampledArea(FOOTPRINT));
    }

    private static PlacementFilters.Flatness parse(String json) {
        return PlacementFilters.Flatness.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }
}
