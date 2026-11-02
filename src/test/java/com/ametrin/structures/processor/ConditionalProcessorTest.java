package com.ametrin.structures.processor;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.server.MinecraftServer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class ConditionalProcessorTest {
    private static final String REPLACE_STONE = """
            [{"processor_type": "ametrin_structures:replace_block", "condition": "minecraft:stone", "chance": 1, "change_to": "%s"}]""";

    @Test
    void processorsThatArentReadDoNothing(MinecraftServer server) {
        var json = json("missing_mod", REPLACE_STONE.formatted("missing_mod:cracked_stone"));
        var processor = ConditionalProcessor.CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow();

        assertTrue(processor.processors(server.registryAccess()).isEmpty());
        assertEquals(json, ConditionalProcessor.CODEC.codec().encodeStart(JsonOps.INSTANCE, processor).getOrThrow(), "kept as written");
        var unreadable = parse(json("minecraft", REPLACE_STONE.formatted("missing_mod:cracked_stone")));
        assertTrue(unreadable.processors(server.registryAccess()).isEmpty(), "a passing condition with processors that don't read");
    }

    @Test
    void aPassingConditionRunsItsProcessorsOrProcessorList(MinecraftServer server) {
        var processor = parse(json("minecraft", REPLACE_STONE.formatted("minecraft:cobblestone")));
        assertInstanceOf(ReplaceBlockProcessor.class, processor.processors(server.registryAccess()).getFirst());
        var list = parse(json("minecraft", "\"minecraft:mossify_10_percent\""));
        assertFalse(list.processors(server.registryAccess()).isEmpty());
    }

    private static ConditionalProcessor parse(JsonElement json) {
        return ConditionalProcessor.CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    private static JsonElement json(String modId, String processors) {
        return JsonParser.parseString("""
                {"conditions": [{"type": "neoforge:mod_loaded", "modid": "%s"}], "processors": %s}""".formatted(modId, processors));
    }
}
