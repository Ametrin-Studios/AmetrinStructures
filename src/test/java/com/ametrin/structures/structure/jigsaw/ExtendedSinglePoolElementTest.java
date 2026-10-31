package com.ametrin.structures.structure.jigsaw;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.ProcessorLists;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class ExtendedSinglePoolElementTest {
    private static final Identifier TEMPLATE = Identifier.withDefaultNamespace("igloo/top");

    @Test
    void noProcessorsAreLeftOut(MinecraftServer server) {
        var empty = server.registryAccess().lookupOrThrow(Registries.PROCESSOR_LIST).getOrThrow(ResourceKey.create(Registries.PROCESSOR_LIST, Identifier.withDefaultNamespace("empty")));
        assertFalse(encode(server, element(empty)).has("processors"));
        assertFalse(encode(server, element(ExtendedSinglePoolElement.NO_PROCESSORS)).has("processors"));
    }

    @Test
    void processorsAreWritten(MinecraftServer server) {
        var mossify = server.registryAccess().lookupOrThrow(Registries.PROCESSOR_LIST).getOrThrow(ProcessorLists.MOSSIFY_10_PERCENT);
        assertEquals("minecraft:mossify_10_percent", encode(server, element(mossify)).get("processors").getAsString());
    }

    @Test
    void readsWithoutProcessors(MinecraftServer server) {
        var json = JsonParser.parseString("""
                {"element_type": "ametrin_structures:single_pool_element", "location": "minecraft:igloo/top", "projection": "rigid"}
                """);
        var element = assertInstanceOf(ExtendedSinglePoolElement.class, StructurePoolElement.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, server.registryAccess()), json).getOrThrow());
        assertFalse(encode(server, element).has("processors"));
    }

    private static ExtendedSinglePoolElement element(Holder<StructureProcessorList> processors) {
        return new ExtendedSinglePoolElement(Either.left(TEMPLATE), processors, StructureTemplatePool.Projection.RIGID, Optional.empty(), true, Optional.empty());
    }

    private static JsonObject encode(MinecraftServer server, StructurePoolElement element) {
        return StructurePoolElement.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, server.registryAccess()), element).getOrThrow().getAsJsonObject();
    }
}
