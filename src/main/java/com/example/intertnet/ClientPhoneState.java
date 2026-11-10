package com.example.intertnet;

import java.util.Collections;
import java.util.List;

public class ClientPhoneState {
    private static volatile String currentNetworkLabel = "Offline";
    private static volatile List<ModNetwork.OrderSnapshot> activeOrders = Collections.emptyList();
    private static volatile List<ModNetwork.MessageSnapshot> messages = Collections.emptyList();
    private static volatile List<String> peersOnNetwork = Collections.emptyList();

    public static void reset() {
        currentNetworkLabel = "Offline";
        activeOrders = Collections.emptyList();
        messages = Collections.emptyList();
        peersOnNetwork = Collections.emptyList();
    }

    public static void update(
            String networkLabel,
            List<ModNetwork.OrderSnapshot> orders,
            List<ModNetwork.MessageSnapshot> msgs,
            List<String> peers
    ) {
        currentNetworkLabel = networkLabel != null ? networkLabel : "Offline";
        activeOrders = orders != null ? List.copyOf(orders) : Collections.emptyList();
        messages = msgs != null ? List.copyOf(msgs) : Collections.emptyList();
        peersOnNetwork = peers != null ? List.copyOf(peers) : Collections.emptyList();
    }

    public static String getCurrentNetworkLabel() {
        return currentNetworkLabel;
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
