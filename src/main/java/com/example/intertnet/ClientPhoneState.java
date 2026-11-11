package com.example.intertnet;

import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ClientPhoneState {
    private static volatile String currentNetworkLabel = "Offline";
    private static volatile ModNetwork.PhoneSignal signal;
    private static volatile List<ModNetwork.OrderSnapshot> activeOrders = Collections.emptyList();
    private static volatile List<ModNetwork.MessageSnapshot> messages = Collections.emptyList();
    private static volatile List<String> peersOnNetwork = Collections.emptyList();

    public static void reset() {
        currentNetworkLabel = "Offline";
        signal = null;
        activeOrders = Collections.emptyList();
        messages = Collections.emptyList();
        peersOnNetwork = Collections.emptyList();
    }

    public static void update(
            String networkLabel,
            @Nullable ModNetwork.PhoneSignal phoneSignal,
            List<ModNetwork.OrderSnapshot> orders,
            List<ModNetwork.MessageSnapshot> msgs,
            List<String> peers
    ) {
        currentNetworkLabel = networkLabel != null ? networkLabel : "Offline";
        signal = phoneSignal;
        activeOrders = orders != null ? List.copyOf(orders) : Collections.emptyList();
        messages = msgs != null ? List.copyOf(msgs) : Collections.emptyList();
        peersOnNetwork = peers != null ? List.copyOf(peers) : Collections.emptyList();
    }

    public static String getCurrentNetworkLabel() {
        return currentNetworkLabel;
    }

    @Nullable
    static SignalManager.SignalStatus getSignal(Player player, @Nullable String sim) {
        var snapshot = signal;
        return snapshot != null && snapshot.dimension().equals(player.level().dimension().location())
                && Objects.equals(snapshot.sim(), sim)
                && snapshot.antenna() == PhoneItem.hasSatelliteAntenna(DeliveryManager.findPlayerPhoneStack(player))
                ? snapshot.status() : null;
    }

    public static List<ModNetwork.OrderSnapshot> getActiveOrders() {
        return activeOrders;
    }

    public static List<ModNetwork.MessageSnapshot> getMessages() {
        return messages;
    }

    public static List<String> getPeersOnNetwork() {
        return peersOnNetwork;
    }
}
