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
        if (payload.mode() == ModNetwork.EMERGENCY_FEEDBACK && Minecraft.getInstance().screen instanceof EmergencyScreen screen) {
            screen.setFeedback(payload.message());
        } else if (payload.mode() == ModNetwork.RANGER_FEEDBACK && Minecraft.getInstance().screen instanceof RangerScreen screen) {
            screen.setFeedback(payload.message());
        } else if (Minecraft.getInstance().screen instanceof TownServiceScreen screen) {
            screen.setFeedback(payload);
        }
    }

    static void receiveRangerQuote(ModNetwork.RangerQuotePayload payload) {
        if (Minecraft.getInstance().screen instanceof RangerScreen screen) screen.setQuote(payload);
    }

    static void openRangerMeeting(int entityId) {
        Minecraft.getInstance().setScreen(new RangerScreen(entityId));
    }
}
