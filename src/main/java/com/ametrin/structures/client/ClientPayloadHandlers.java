package com.ametrin.structures.client;

import com.ametrin.structures.client.compat.SpreadWaypointsClient;
import com.ametrin.structures.network.ASPayloads;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.ApiStatus;

// Registered from common code, so this class must not touch client classes outside of its method bodies.
@ApiStatus.Internal
public final class ClientPayloadHandlers {
    private ClientPayloadHandlers() {}

    public static void sendRegistryKeys(ASPayloads.SendRegistryKeys payload, IPayloadContext context) {
        RegistryKeyCache.accept(payload.registry(), payload.keys());
    }

    public static void spreadWaypoints(ASPayloads.SpreadWaypoints payload, IPayloadContext context) {
        SpreadWaypointsClient.show(payload);
    }

    public static void clearSpreadWaypoints(ASPayloads.ClearSpreadWaypoints payload, IPayloadContext context) {
        SpreadWaypointsClient.clear();
    }
}
