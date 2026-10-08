package com.ametrin.structures.structure.simple;

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
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.*;

/// The start height forms as a datapack author writes them, sampled against a fixed terrain.
class StartHeightTest {
    private static final int SURFACE = 70;
    private static final int OCEAN_FLOOR = 40;
    private static final ToIntFunction<Heightmap.Types> TERRAIN =
            type -> type == Heightmap.Types.OCEAN_FLOOR_WG ? OCEAN_FLOOR : SURFACE;

    @Test
    void oneAnchorIsEitherVanillasOrAHeightmap() {
        assertEquals(StartHeight.at(HeightAnchor.surface(0)), parse("{\"heightmap\":\"WORLD_SURFACE_WG\"}"));
        assertEquals(StartHeight.at(HeightAnchor.oceanFloor(-3)), parse("{\"heightmap\":\"OCEAN_FLOOR_WG\",\"offset\":-3}"));
        assertEquals(StartHeight.at(HeightAnchor.absolute(10)), parse("{\"absolute\":10}"));
    }

    @Test
    void aRangeMixesReferences() {
        assertEquals(
                StartHeight.between(HeightAnchor.aboveBottom(6), HeightAnchor.surface(-12)),
                parse("{\"min\":{\"above_bottom\":6},\"max\":{\"heightmap\":\"WORLD_SURFACE_WG\",\"offset\":-12}}"));
    }

    @Test
    void vanillaHeightProvidersStillWork() {
        var parsed = parse("{\"type\":\"minecraft:uniform\",\"min_inclusive\":{\"absolute\":0},\"max_inclusive\":{\"absolute\":20}}");
        assertInstanceOf(StartHeight.Provider.class, parsed);
    }

    @Test
    void rangesRoundTrip() {
        var range = StartHeight.between(HeightAnchor.oceanFloor(0), HeightAnchor.surface(-8));
        var json = StartHeight.CODEC.encodeStart(JsonOps.INSTANCE, range).getOrThrow();
        assertEquals(range, StartHeight.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test
    void aRangeBetweenTwoHeightmapsStaysBetweenThem() {
        var range = StartHeight.between(HeightAnchor.oceanFloor(0), HeightAnchor.surface(-8));
        RandomSource random = RandomSource.create(42);
        for (int i = 0; i < 200; i++) {
            int y = sample(range, random).orElseThrow();
            assertTrue(y >= OCEAN_FLOOR && y <= SURFACE - 8, "sampled " + y);
        }
    }

    @Test
    void aRangeWithNoRoomIsEmpty() {
        var range = StartHeight.between(HeightAnchor.surface(0), HeightAnchor.oceanFloor(0));
        assertEquals(OptionalInt.empty(), sample(range, RandomSource.create(1)));
    }

    @Test
    void anAnchorOffsetsItsHeightmap() {
        assertEquals(OptionalInt.of(SURFACE - 5), sample(StartHeight.at(HeightAnchor.surface(-5)), RandomSource.create(1)));
    }

    @Test
    void theStructureStandsOnTheLowerEndsHeightmap() {
        assertEquals(Optional.of(Heightmap.Types.OCEAN_FLOOR_WG),
                StartHeight.between(HeightAnchor.oceanFloor(0), HeightAnchor.surface(0)).groundHeightmap());
        assertEquals(Optional.of(Heightmap.Types.WORLD_SURFACE_WG),
                StartHeight.between(HeightAnchor.absolute(0), HeightAnchor.surface(0)).groundHeightmap());
        assertEquals(Optional.empty(), StartHeight.of(ConstantHeight.of(VerticalAnchor.absolute(0))).groundHeightmap());
    }

    @Test
    void theDefaultIsTheSurface() {
        assertEquals(StartHeight.at(HeightAnchor.surface(0)), SimpleStructure.ON_SURFACE_START_HEIGHT);
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
