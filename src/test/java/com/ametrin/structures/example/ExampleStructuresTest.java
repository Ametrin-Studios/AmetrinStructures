package com.ametrin.structures.example;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.RegistryPatchGenerator;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/// Runs the examples through the same registry bootstrap datagen uses, and encodes every result.
class ExampleStructuresTest {
    @Test
    void examplesBootstrapAndEncode() {
        var registries = RegistryPatchGenerator.createLookup(
                        CompletableFuture.completedFuture(VanillaRegistries.createLookup()), ExampleStructures.registries())
                .join()
                .full();
        var ops = registries.createSerializationContext(JsonOps.INSTANCE);

        for (var simple : new String[]{"ruined_tower", "crypt", "sunken_shrine", "graves/small", "graves/large", "castle/ruin"}) {
            assertType(registries, ops, Registries.STRUCTURE, Structure.DIRECT_CODEC, simple, "ametrin_structures:simple");
        }
        assertType(registries, ops, Registries.STRUCTURE, Structure.DIRECT_CODEC, "castle", "ametrin_structures:extended_jigsaw");
        for (var set : new String[]{"ruined_tower", "crypt", "sunken_shrine", "graves", "castle"}) {
            assertType(registries, ops, Registries.STRUCTURE_SET, StructureSet.DIRECT_CODEC, set, null);
        }
        // Sets mix structure types and take any placement.
        assertEquals(2, structureCount(registries, ops, "castle"));
        assertEquals("minecraft:random_spread", placementType(registries, ops, "graves"));
        assertEquals("ametrin_structures:even_spread", placementType(registries, ops, "crypt"));
        assertEquals("ametrin_structures:even_spread", placementType(registries, ops, "sunken_shrine"), "the default");
        assertEquals("ametrin_structures:scattered_grid", placementType(registries, ops, "castle"));
        for (var pool : new String[]{"castle/start", "castle/walls", "castle/wall_ends", "castle/moat"}) {
            encode(registries, ops, Registries.TEMPLATE_POOL, StructureTemplatePool.DIRECT_CODEC, pool);
        }

        // Every element is the library's; noFoamProcessing() only turns off its foam removal.
        var walls = firstElement(registries, ops, "castle/walls");
        var wallEnds = firstElement(registries, ops, "castle/wall_ends");
        assertEquals("ametrin_structures:single_pool_element", walls.get("element_type").getAsString());
        assertEquals("ametrin_structures:single_pool_element", wallEnds.get("element_type").getAsString());
        assertFalse(walls.has("process_foam"));
        assertFalse(wallEnds.get("process_foam").getAsBoolean());
    }

    private static String placementType(HolderLookup.Provider registries, RegistryOps<JsonElement> ops, String set) {
        return encode(registries, ops, Registries.STRUCTURE_SET, StructureSet.DIRECT_CODEC, set)
                .getAsJsonObject().getAsJsonObject("placement").get("type").getAsString();
    }

    private static int structureCount(HolderLookup.Provider registries, RegistryOps<JsonElement> ops, String set) {
        return encode(registries, ops, Registries.STRUCTURE_SET, StructureSet.DIRECT_CODEC, set)
                .getAsJsonObject().getAsJsonArray("structures").size();
    }

    private static JsonObject firstElement(HolderLookup.Provider registries, RegistryOps<JsonElement> ops, String pool) {
        JsonElement json = encode(registries, ops, Registries.TEMPLATE_POOL, StructureTemplatePool.DIRECT_CODEC, pool);
        return json.getAsJsonObject().getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonObject("element");
    }

    private static <T> void assertType(
            HolderLookup.Provider registries,
            RegistryOps<JsonElement> ops,
            ResourceKey<? extends Registry<T>> registry,
            Codec<T> codec,
            String name,
            @Nullable String type) {
        JsonElement json = encode(registries, ops, registry, codec, name);
        if (type != null) {
            assertEquals(type, json.getAsJsonObject().get("type").getAsString(), json::toString);
        }
    }

    private static <T> JsonElement encode(
            HolderLookup.Provider registries,
            RegistryOps<JsonElement> ops,
            ResourceKey<? extends Registry<T>> registry,
            Codec<T> codec,
            String name) {
        T value = registries.lookupOrThrow(registry).getOrThrow(ResourceKey.create(registry, ExampleStructures.id(name))).value();
        return codec.encodeStart(ops, value).getOrThrow();
    }
}
