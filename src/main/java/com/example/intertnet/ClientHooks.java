package com.example.intertnet;

import net.minecraft.client.Minecraft;

public class ClientHooks {
    public static void openPhoneScreen() {
        Minecraft.getInstance().setScreen(new PhoneScreen());
    }

    static void openRestaurantScreen(int chefId) {
        Minecraft.getInstance().setScreen(new TownServiceScreen(TownServices.Mode.DINING, null, chefId));
    }

    static void receiveServiceQuotes(ModNetwork.ServiceQuotesPayload payload) {
        if (Minecraft.getInstance().screen instanceof TownServiceScreen screen) {
            screen.setQuotes(payload);
        }
    }

    static void receiveServiceFeedback(ModNetwork.ServiceFeedbackPayload payload) {
        if (Minecraft.getInstance().screen instanceof TownServiceScreen screen) {
            screen.setFeedback(payload);
        }
    }
}
