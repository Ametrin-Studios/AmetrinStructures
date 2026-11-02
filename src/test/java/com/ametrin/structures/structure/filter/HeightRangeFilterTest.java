package com.ametrin.structures.structure.filter;

import com.ametrin.structures.structure.GenerationContexts;
import com.ametrin.structures.structure.modular.HeightAnchor;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class HeightRangeFilterTest {
    private static final BoundingBox BOX = new BoundingBox(0, 10, 0, 4, 20, 4);

    @Test
    void theOriginIsCheckedByDefault(MinecraftServer server) {
        assertTrue(new PlacementFilters.HeightRange(10, 10).test(context(server, BOX)));
        assertFalse(new PlacementFilters.HeightRange(11, 30).test(context(server, BOX)));
    }

    @Test
    void theBoxChecksItsLowestBlockAgainstTheMinAndItsHighestAgainstTheMax(MinecraftServer server) {
        assertTrue(box(10, 20).test(context(server, BOX)));
        assertFalse(box(11, 30).test(context(server, BOX)));
        assertFalse(box(0, 19).test(context(server, BOX)));
    }

    @Test
    void aHeightCanBeRelativeToTheTerrain(MinecraftServer server) {
        var context = context(server, BOX);
        int surface = context.terrain().surfaceHeight(Heightmap.Types.WORLD_SURFACE_WG, 0, 0);
        assertTrue(new PlacementFilters.HeightRange(Optional.empty(), Optional.of(HeightAnchor.surface(20 - surface)), PlacementFilters.HeightRange.Check.BOX).test(context));
        assertFalse(new PlacementFilters.HeightRange(Optional.empty(), Optional.of(HeightAnchor.surface(19 - surface)), PlacementFilters.HeightRange.Check.BOX).test(context));
    }

    @Test
    void readsPlainYsAndAnchors(MinecraftServer server) {
        var filter = PlacementFilter.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"type": "ametrin_structures:height_range", "min": -20, "max": {"heightmap": "WORLD_SURFACE_WG", "offset": -1}, "check": "box"}
                """)).getOrThrow();
        assertEquals(new PlacementFilters.HeightRange(Optional.of(HeightAnchor.absolute(-20)), Optional.of(HeightAnchor.surface(-1)), PlacementFilters.HeightRange.Check.BOX), filter);
        assertEquals(JsonParser.parseString("""
                {"type": "ametrin_structures:height_range", "min": 0, "max": 100}
                """), PlacementFilter.CODEC.encodeStart(JsonOps.INSTANCE, new PlacementFilters.HeightRange(0, 100)).getOrThrow());
        // A min or a max is needed, and they must be in order.
        assertTrue(new PlacementFilters.HeightRange(Optional.empty(), Optional.empty(), PlacementFilters.HeightRange.Check.ORIGIN).validate().isError());
        assertTrue(new PlacementFilters.HeightRange(5, 4).validate().isError());
    }

    private static PlacementFilters.HeightRange box(int min, int max) {
        return new PlacementFilters.HeightRange(Optional.of(HeightAnchor.absolute(min)), Optional.of(HeightAnchor.absolute(max)), PlacementFilters.HeightRange.Check.BOX);
    }

    private static PlacementFilter.Context context(MinecraftServer server, BoundingBox box) {
        var generation = GenerationContexts.overPlains(server, 0, biome -> true);
        return new PlacementFilter.Context(generation, new BlockPos(box.minX(), box.minY(), box.minZ()), box, new TerrainSampler(generation, Heightmap.Types.WORLD_SURFACE_WG));
    }
}
