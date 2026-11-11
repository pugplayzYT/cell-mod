package com.example.intertnet;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

final class IntertnetAdvancements {
    static final String CONNECTED = "connected";
    static final String PRIVATE_NETWORK = "private_network";
    static final String NETWORK_OPERATOR = "network_operator";
    static final String OFF_THE_GRID = "off_the_grid";
    static final String MESSAGE_SENT = "message_sent";
    static final String DINNER_DELIVERED = "dinner_delivered";
    static final String GOING_PLACES = "going_places";
    static final String EMERGENCY_RESPONSE = "emergency_response";

    static void award(ServerPlayer player, String name) {
        var advancement = player.server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath(IntertnetMod.MODID, name));
        if (advancement != null && !player.getAdvancements().getOrStartProgress(advancement).isDone()) {
            player.getAdvancements().award(advancement, "completed");
        }
    }

    static void checkConnection(ServerPlayer player, SignalManager.SignalStatus signal) {
        award(player, CONNECTED);
        if (signal.connectedViaSatellite()) {
            var advancement = player.server.getAdvancements().get(
                    ResourceLocation.fromNamespaceAndPath(IntertnetMod.MODID, OFF_THE_GRID));
            if (advancement != null && !player.getAdvancements().getOrStartProgress(advancement).isDone()
                    && !SignalManager.hasCellCoverage(player)) {
                player.getAdvancements().award(advancement, "completed");
            }
        } else {
            BlockEntity source = player.serverLevel().getBlockEntity(signal.connectedTowerPos());
            if (source instanceof CellTowerBlockEntity tower && !tower.isPublicTower() && !HotelAccess.isHotelTower(tower)) {
                award(player, PRIVATE_NETWORK);
            }
        }
    }
}
