package com.ametrin.structures.client;

import com.ametrin.structures.network.ASPayloads;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.*;

/// Registry keys the server has told us about.
///
/// The fixture screen asks the server instead of reading the client's registries, because the server can have datapacks the client doesn't know about.
public final class RegistryKeyCache {
    private static final Map<Identifier, List<Identifier>> KEYS = new HashMap<>();
    private static final Set<Identifier> REQUESTED = new HashSet<>();
    private static int version;

    private RegistryKeyCache() {}

    /// Returns what is cached, asking the server the first time a registry is needed since the last
    /// [#refresh()]. The answer arrives a moment later and replaces the cached keys.
    public static List<Identifier> get(Identifier registry) {
        if (REQUESTED.add(registry)) {
            ClientPacketDistributor.sendToServer(new ASPayloads.RequestRegistryKeys(registry));
        }
        return KEYS.getOrDefault(registry, List.of());
    }

    /// Asks the server again on next use, so changes from /reload show up. The old keys stay until then.
    public static void refresh() {
        REQUESTED.clear();
    }

    public static void accept(Identifier registry, List<Identifier> keys) {
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
