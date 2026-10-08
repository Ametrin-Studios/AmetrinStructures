package com.ametrin.structures.client.compat;

import com.ametrin.structures.AmetrinStructures;
import com.ametrin.structures.network.ASPayloads;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.hud.minimap.waypoint.thirdparty.ThirdPartyWaypointManager;
import xaero.hud.minimap.world.container.MinimapWorldRootContainer;

import java.util.ArrayList;

/// only load this class when `xaerominimap` is present.
final class XaeroSpreadWaypoints {
    // Spread report spots as Xaero's Minimap third-party waypoints: shown on the minimap and world map,
    // never saved with the player's own. Each report gets its own origin, so a new one replaces the
    // spots of the same structure, tag or set and leaves the others for comparison.
    //
    // Xaero's has no published API; this goes through the same classes its own Waystones support uses.
    private static final String FOUND = "spread/";
    private static final String REJECTED = "spread_rejected/";

    private XaeroSpreadWaypoints() {}

    static void show(ASPayloads.SpreadWaypoints payload) {
        var root = root();
        if (root == null) {
            return;
        }
        if (payload.replaceAll()) {
            clear(root);
        }
        var manager = manager(root, payload.dimension());
        var foundOrigin = origin(FOUND, payload.report());
        var rejectedOrigin = origin(REJECTED, payload.report());
        manager.clearOrigin(foundOrigin);
        manager.clearOrigin(rejectedOrigin);

        // Xaero's first 16 colors are the team colors, in the same order.
        var color = WaypointColor.fromIndex(payload.color().ordinal());
        var found = manager.get(foundOrigin);
        for (int i = 0; i < payload.found().size(); i++) {
            var spot = payload.found().get(i);
            var pos = spot.position();
            // Numbered across the report, as `visit <number>` counts them.
            var structure = spot.structure().getPath();
            found.add(Integer.toString(i), new Waypoint(pos.getX(), pos.getY(), pos.getZ(), structure + " " + (i + 1), initials(structure), color, WaypointPurpose.NORMAL));
        }

        var name = payload.report().getPath();

        var rejected = manager.get(rejectedOrigin);
        for (int i = 0; i < payload.rejected().size(); i++) {
            var spot = payload.rejected().get(i);
            var pos = spot.position();
            // Ids lose their namespace: the label is short, and the full id is in the chat report.
            var argument = spot.argument().substring(spot.argument().indexOf(':') + 1);
            var label = I18n.get("waypoint.ametrin_structures.rejected." + spot.reason(), argument);
            rejected.add(Integer.toString(i), new Waypoint(pos.getX(), pos.getY(), pos.getZ(), name + ": " + label, "x", WaypointColor.GRAY, WaypointPurpose.NORMAL));
        }
    }

    /// Removes every spread waypoint, in every dimension.
    static void clear() {
        var root = root();
        if (root != null) {
            clear(root);
        }
    }

    private static void clear(MinimapWorldRootContainer root) {
        for (var container : root.getSubContainers()) {
            var manager = container.getThirdPartyWaypointManager();
            var ours = new ArrayList<Identifier>();
            for (var waypoints : manager.getAll()) {
                if (waypoints.getOriginId().getNamespace().equals(AmetrinStructures.MOD_ID)) {
                    ours.add(waypoints.getOriginId());
                }
            }
            ours.forEach(manager::clearOrigin);
        }
    }

    private static @Nullable MinimapWorldRootContainer root() {
        var session = BuiltInHudModules.MINIMAP.getCurrentSession();
        return session == null ? null : session.getWorldManager().getAutoRootContainer();
    }

    private static ThirdPartyWaypointManager manager(MinimapWorldRootContainer root, ResourceKey<Level> dimension) {
        var directory = root.getSession().getDimensionHelper().getDimensionDirectoryName(dimension);
        return root.addSubContainer(root.getPath().resolve(directory)).getThirdPartyWaypointManager();
    }

    private static Identifier origin(String prefix, Identifier report) {
        return AmetrinStructures.locate(prefix + report.getNamespace() + "/" + report.getPath());
    }

    /// The waypoint icon's letters: the first letter of each of the name's first two words, so
    /// `examplemod:ruins/ruined_tower` shows `RT`.
    static String initials(String path) {
        var name = path.substring(path.lastIndexOf('/') + 1);
        var initials = new StringBuilder();
        for (var word : name.split("_")) {
            if (!word.isEmpty() && initials.length() < 2) {
                initials.append(Character.toUpperCase(word.charAt(0)));
            }
        }
        return initials.isEmpty() ? "?" : initials.toString();
    }
}
