package com.ametrin.structures.structure.filter;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// The water depth filter as a datapack author writes it.
class WaterDepthFilterTest {
    @Test
    void minDefaultsToZeroAndMaxIsOptional() {
        assertEquals(new PlacementFilters.WaterDepth(0, Optional.of(2)), parse("{\"max\":2}"));
        assertEquals(new PlacementFilters.WaterDepth(3, Optional.empty()), parse("{\"min\":3}"));
    }

    @Test
    void minAboveMaxIsRejected() {
        var result = PlacementFilters.WaterDepth.CODEC.codec()
                .parse(JsonOps.INSTANCE, JsonParser.parseString("{\"min\":4,\"max\":2}"));
        assertTrue(result.isError());
    }

    private static PlacementFilters.WaterDepth parse(String json) {
        return PlacementFilters.WaterDepth.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }
}
