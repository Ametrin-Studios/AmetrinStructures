package com.ametrin.structures.client;

import com.ametrin.structures.network.ASPayloads;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

/// Registry keys the server has told us about.
///
/// The fixture screen asks the server instead of reading the client's registries, because the server can have datapacks the client doesn't know about.
public final class RegistryKeyCache {
    private static final Map<ResourceLocation, List<ResourceLocation>> KEYS = new HashMap<>();
    private static final Set<ResourceLocation> REQUESTED = new HashSet<>();
    private static int version;

    private RegistryKeyCache() {}

    /// Returns what is cached, asking the server the first time a registry is needed since the last
    /// [#refresh()]. The answer arrives a moment later and replaces the cached keys.
    public static List<ResourceLocation> get(ResourceLocation registry) {
        if (REQUESTED.add(registry)) {
            PacketDistributor.sendToServer(new ASPayloads.RequestRegistryKeys(registry));
        }
        return KEYS.getOrDefault(registry, List.of());
    }

    /// Asks the server again on next use, so changes from /reload show up. The old keys stay until then.
    public static void refresh() {
        REQUESTED.clear();
    }

    public static void accept(ResourceLocation registry, List<ResourceLocation> keys) {
        KEYS.put(registry, List.copyOf(keys));
        version++;
    }

    /// Changes whenever the cached keys do, so completions built from them can tell they're stale.
    public static int version() {
        return version;
    }

    /// Call on disconnect, since another server can have different datapacks.
    public static void clear() {
        KEYS.clear();
        REQUESTED.clear();
        version++;
    }
}
