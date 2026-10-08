package com.ametrin.structures.client.compat;

import com.ametrin.structures.network.ASPayloads;
import com.ametrin.structures.util.ASLog;
import net.neoforged.fml.ModList;

/// Hands spread report waypoints to whichever supported map mod is installed, and does nothing without one. A map mod update that breaks the integration disables it with a log message instead of crashing.
public final class SpreadWaypointsClient {
    private static final String XAEROS_MINIMAP = "xaerominimap";

    private static boolean failed;

    private SpreadWaypointsClient() {}

    public static void show(ASPayloads.SpreadWaypoints payload) {
        if (isAvailable()) {
            run(() -> XaeroSpreadWaypoints.show(payload));
        }
    }

    public static void clear() {
        if (isAvailable()) {
            run(XaeroSpreadWaypoints::clear);
        }
    }

    private static boolean isAvailable() {
        return !failed && ModList.get().isLoaded(XAEROS_MINIMAP);
    }

    private static void run(Runnable action) {
        try {
            action.run();
        } catch (LinkageError | RuntimeException exception) {
            failed = true;
            ASLog.error("spread waypoints for Xaero's Minimap stopped working, likely after a Xaero's update", exception);
        }
    }
}
