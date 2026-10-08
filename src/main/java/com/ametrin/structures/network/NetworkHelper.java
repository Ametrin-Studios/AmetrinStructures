package com.ametrin.structures.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class NetworkHelper {
    private NetworkHelper() {}

    /// skips fake players as they have no connection
    public static void sendTo(ServerPlayer player, CustomPacketPayload payload) {
        if (!(player instanceof FakePlayer)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
