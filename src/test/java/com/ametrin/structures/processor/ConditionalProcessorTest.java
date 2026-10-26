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
    void aFailingConditionNeverReadsTheProcessors(MinecraftServer server) {
        var json = json("missing_mod", REPLACE_STONE.formatted("missing_mod:cracked_stone"));
        var processor = ConditionalProcessor.CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow();

        assertTrue(processor.processors(server.registryAccess()).isEmpty());
        assertEquals(json, ConditionalProcessor.CODEC.codec().encodeStart(JsonOps.INSTANCE, processor).getOrThrow(), "kept as written");
    }

    @Test
    void aPassingConditionRunsTheProcessors(MinecraftServer server) {
        var processor = parse(json("minecraft", REPLACE_STONE.formatted("minecraft:cobblestone")));
        assertInstanceOf(ReplaceBlockProcessor.class, processor.processors(server.registryAccess()).getFirst());
    }

    @Test
    void theProcessorsCanBeAProcessorList(MinecraftServer server) {
        var processor = parse(json("minecraft", "\"minecraft:mossify_10_percent\""));
        assertFalse(processor.processors(server.registryAccess()).isEmpty());
    }

    @Test
    void processorsThatDontReadDoNothing(MinecraftServer server) {
        var processor = parse(json("minecraft", REPLACE_STONE.formatted("missing_mod:cracked_stone")));
        assertTrue(processor.processors(server.registryAccess()).isEmpty());
    }

    private static ConditionalProcessor parse(JsonElement json) {
        return ConditionalProcessor.CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    private static JsonElement json(String modId, String processors) {
        return JsonParser.parseString("""
                {"conditions": [{"type": "neoforge:mod_loaded", "modid": "%s"}], "processors": %s}""".formatted(modId, processors));
    }
}
