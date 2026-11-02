package com.ametrin.structures.structure.modular;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.heightproviders.ConstantHeight;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

/// The start height forms as a datapack author writes them, sampled against a fixed terrain.
class StartHeightTest {
    private static final int SURFACE = 70;
    private static final int OCEAN_FLOOR = 40;
    private static final int LOWEST_CORNER = 60;
    private static final HeightAnchor.Terrain TERRAIN = (type, mode) -> type == Heightmap.Types.OCEAN_FLOOR_WG
            ? OCEAN_FLOOR
            : mode == HeightMode.LOWEST_CORNER ? LOWEST_CORNER : SURFACE;

    @Test
    void readsAnAnchorARangeOrAVanillaHeightProvider() {
        assertEquals(StartHeight.at(HeightAnchor.surface(0)), parse("{\"heightmap\":\"WORLD_SURFACE_WG\"}"));
        assertEquals(StartHeight.at(HeightAnchor.oceanFloor(-3)), parse("{\"heightmap\":\"OCEAN_FLOOR_WG\",\"offset\":-3}"));
        assertEquals(StartHeight.at(HeightAnchor.absolute(10)), parse("{\"absolute\":10}"));
        assertEquals(StartHeight.at(HeightAnchor.surface(2, HeightMode.LOWEST_CORNER)),
                parse("{\"heightmap\":\"WORLD_SURFACE_WG\",\"offset\":2,\"mode\":\"lowest_corner\"}"));
        assertEquals(
                StartHeight.between(HeightAnchor.aboveBottom(6), HeightAnchor.surface(-12)),
                parse("{\"min\":{\"above_bottom\":6},\"max\":{\"heightmap\":\"WORLD_SURFACE_WG\",\"offset\":-12}}"));
        assertInstanceOf(StartHeight.Provider.class, parse("{\"type\":\"minecraft:uniform\",\"min_inclusive\":{\"absolute\":0},\"max_inclusive\":{\"absolute\":20}}"));

        var range = StartHeight.between(HeightAnchor.oceanFloor(0), HeightAnchor.surface(-8));
        assertEquals(range, StartHeight.CODEC.parse(JsonOps.INSTANCE, StartHeight.CODEC.encodeStart(JsonOps.INSTANCE, range).getOrThrow()).getOrThrow(), "ranges round trip");
    }

    @Test
    void samplesStayBetweenTheirAnchorsAndEachEndUsesItsOwnMode() {
        assertEquals(OptionalInt.of(SURFACE - 5), sample(StartHeight.at(HeightAnchor.surface(-5)), RandomSource.create(1)));
        var range = StartHeight.between(HeightAnchor.oceanFloor(0), HeightAnchor.surface(-8));
        RandomSource random = RandomSource.create(42);
        for (int i = 0; i < 200; i++) {
            int y = sample(range, random).orElseThrow();
            assertTrue(y >= OCEAN_FLOOR && y <= SURFACE - 8, "sampled " + y);
        }
        assertEquals(OptionalInt.empty(), sample(StartHeight.between(HeightAnchor.surface(0), HeightAnchor.oceanFloor(0)), RandomSource.create(1)), "no room");
        var modes = StartHeight.between(HeightAnchor.surface(0, HeightMode.LOWEST_CORNER), HeightAnchor.surface(LOWEST_CORNER - SURFACE));
        assertEquals(OptionalInt.of(LOWEST_CORNER), sample(modes, RandomSource.create(1)));
    }

    @Test
    void theStructureStandsOnTheLowerEndsHeightmap() {
        assertEquals(Optional.of(Heightmap.Types.OCEAN_FLOOR_WG),
                StartHeight.between(HeightAnchor.oceanFloor(0), HeightAnchor.surface(0)).groundHeightmap());
        assertEquals(Optional.of(Heightmap.Types.WORLD_SURFACE_WG),
                StartHeight.between(HeightAnchor.absolute(0), HeightAnchor.surface(0)).groundHeightmap());
        assertEquals(Optional.empty(), StartHeight.of(ConstantHeight.of(VerticalAnchor.absolute(0))).groundHeightmap());
    }

    private static StartHeight parse(String json) {
        return StartHeight.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    /// Only absolute and heightmap anchors here, which never read the world.
    @SuppressWarnings("DataFlowIssue")
    private static OptionalInt sample(StartHeight height, RandomSource random) {
        return height.sample(random, (WorldGenerationContext) null, TERRAIN);
    }
}
