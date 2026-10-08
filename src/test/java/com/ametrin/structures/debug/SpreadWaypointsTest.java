package com.ametrin.structures.debug;

import com.ametrin.structures.network.ASPayloads;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.TeamColor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/// Waypoint colors picked by id, and the packet that carries the spots to the client.
class SpreadWaypointsTest {
    @Test
    void defaultColorsSkipBlackAndGray() {
        for (int i = 0; i < 500; i++) {
            var color = SpreadCommand.defaultColor(Identifier.fromNamespaceAndPath("examplemod", "structure_" + i));
            assertNotEquals(TeamColor.BLACK, color);
            assertNotEquals(TeamColor.GRAY, color);
            assertNotEquals(TeamColor.DARK_GRAY, color);
        }
    }

    @Test
    void aStructureKeepsItsColor() {
        var id = Identifier.fromNamespaceAndPath("examplemod", "ruined_tower");
        assertEquals(SpreadCommand.defaultColor(id), SpreadCommand.defaultColor(id));
    }

    @Test
    void waypointsSurviveThePacket() {
        var payload = new ASPayloads.SpreadWaypoints(
                Identifier.fromNamespaceAndPath("examplemod", "ruins"),
                Level.OVERWORLD,
                TeamColor.GOLD,
                true,
                List.of(new ASPayloads.SpreadWaypoints.FoundSpot(new BlockPos(10, 64, -20), Identifier.fromNamespaceAndPath("examplemod", "ruined_tower")),
                        new ASPayloads.SpreadWaypoints.FoundSpot(new BlockPos(-300, 71, 42), Identifier.fromNamespaceAndPath("examplemod", "ruined_hut"))),
                List.of(new ASPayloads.SpreadWaypoints.RejectedSpot(new BlockPos(5, 60, 5), "filter", "ametrin_structures:flatness")));
        var buffer = Unpooled.buffer();
        ASPayloads.SpreadWaypoints.STREAM_CODEC.encode(buffer, payload);
        assertEquals(payload, ASPayloads.SpreadWaypoints.STREAM_CODEC.decode(buffer));
    }
}
