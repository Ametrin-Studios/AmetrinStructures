package com.ametrin.structures.fixture;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.server.MinecraftServer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class ExtendFixtureTest {
    @Test
    void theOldExtendDownTypeStillReads(MinecraftServer server) {
        var json = JsonParser.parseString("""
                {"type": "ametrin_structures:extend_down", "max_depth": 8}
                """);
        var fixture = Fixture.MAP_CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(new ExtendFixture(ExtendFixture.Vertical.DOWN, Optional.empty(), 8), fixture);
    }
}
