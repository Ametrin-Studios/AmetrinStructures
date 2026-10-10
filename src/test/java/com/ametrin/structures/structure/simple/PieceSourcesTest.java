package com.ametrin.structures.structure.simple;

import com.ametrin.structures.structure.GenerationContexts;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/// Weighted and compound sources hold other sources, and a bare template entry stands for a single one.
@ExtendWith(EphemeralTestServerProvider.class)
class PieceSourcesTest {
    private static final String NESTED = """
            {
              "type": "ametrin_structures:compound",
              "sources": [
                { "template": "minecraft:igloo/top" },
                {
                  "type": "ametrin_structures:weighted",
                  "sources": [
                    { "data": { "template": "minecraft:igloo/middle" }, "weight": 1 },
                    { "data": { "type": "ametrin_structures:compound", "sources": [
                        { "template": "minecraft:igloo/middle" },
                        { "template": "minecraft:igloo/bottom", "offset": [0, -3, 0] }
                    ] }, "weight": 1 }
                  ]
                }
              ]
            }
            """;

    @Test
    void nestedSourcesPlaceEveryPieceTheyReach(MinecraftServer server) {
        var source = parse(server, NESTED).getOrThrow();
        var compound = assertInstanceOf(PieceSources.CompoundSource.class, source);
        assertInstanceOf(PieceSources.SingleSource.class, compound.sources().getFirst());
        assertInstanceOf(PieceSources.WeightedSource.class, compound.sources().get(1));

        // The top, then either one middle or a middle and a bottom.
        for (long seed = 0; seed < 8; seed++) {
            int count = appendPieces(server, source, seed).size();
            assertTrue(count == 2 || count == 3, "pieces: " + count);
        }
    }

    @Test
    void singleSourcesAreWrittenAsBareTemplateEntries(MinecraftServer server) {
        var source = parse(server, NESTED).getOrThrow();
        var json = PieceSource.CODEC.encodeStart(ops(server), source).getOrThrow().getAsJsonObject();
        var top = json.getAsJsonArray("sources").get(0).getAsJsonObject();
        assertFalse(top.has("type"));
        assertEquals("minecraft:igloo/top", top.get("template").getAsString());
        assertEquals(source, PieceSource.CODEC.parse(ops(server), json).getOrThrow());
    }

    @Test
    void theTypedFormOfASingleSourceStillParses(MinecraftServer server) {
        var source = parse(server, "{\"type\": \"ametrin_structures:single\", \"template\": \"minecraft:igloo/top\"}").getOrThrow();
        assertInstanceOf(PieceSources.SingleSource.class, source);
    }

    @Test
    void emptySourcesAreRejected(MinecraftServer server) {
        assertTrue(parse(server, "{\"type\": \"ametrin_structures:compound\", \"sources\": []}").isError());
        assertTrue(parse(server, "{\"type\": \"ametrin_structures:weighted\", \"sources\": []}").isError());
    }

    private static DataResult<PieceSource> parse(MinecraftServer server, String json) {
        return PieceSource.CODEC.parse(ops(server), JsonParser.parseString(json));
    }

    private static RegistryOps<JsonElement> ops(MinecraftServer server) {
        return RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
    }

    private static List<StructurePiece> appendPieces(MinecraftServer server, PieceSource source, long seed) {
        var generation = GenerationContexts.overPlains(server, seed, biome -> biome.is(Biomes.PLAINS));
        var pieces = new ArrayList<StructurePiece>();
        source.appendPieces(pieces, new PieceSource.Context(generation, BlockPos.ZERO, Rotation.NONE, Optional.empty(), TerrainAdjustment.NONE));
        return pieces;
    }
}
