package com.example.intertnet;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.List;

public class PhoneHudOverlay {

    public static void onRenderGui(final RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) {
            return;
        }

        // Check if the player has a Phone anywhere in their inventory or hands
        var phone = DeliveryManager.findPlayerPhoneStack(player);
        if (phone.isEmpty()) {
            return;
        }

        String simNet = PhoneItem.getInsertedSimNetwork(phone);
        boolean hasAntenna = PhoneItem.hasSatelliteAntenna(phone);
        GuiGraphics g = event.getGuiGraphics();
        SignalManager.SignalStatus sig = SignalManager.getBestSignal(player, simNet);
        int bars = Mth.clamp(sig.bars(), 0, 4);

        int screenW = mc.getWindow().getGuiScaledWidth();
        List<ModNetwork.OrderSnapshot> orders = ClientPhoneState.getActiveOrders();
        boolean hasOrders = !orders.isEmpty();

        int boxW = 118;
        int boxH = hasOrders ? 34 : 22;
        int boxX = screenW - boxW - 6;
        int boxY = 6;

        // Sleek translucent dark HUD card in top-right corner
        g.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xCC090D16);
        g.renderOutline(boxX, boxY, boxW, boxH, bars > 0 ? 0xFF10B981 : 0xFF475569);

        // Render Python-generated signal bars (40x15)
        g.blit(PhoneScreen.SIGNAL_TEXTURES[bars], boxX + 3, boxY + 3, 0, 0, 40, 15, 40, 15);

        String topText;
        if (bars > 0) {
            topText = sig.connectedViaGlobalSatellite() ? "Sat " + bars + "/4 Bars" : bars + "/4 Bars";
        } else if (sig.satelliteSkyBlockedNearby()) {
            topText = "Sky Blocked";
        } else if (simNet == null && !hasAntenna) {
            topText = "No SIM";
        } else if (simNet != null && sig.missingOrWrongSim()) {
            topText = "Wrong SIM";
        } else {
            topText = "No Signal";
        }

        int topCol = switch (bars) {
            case 4 -> 0xFF34D399;
            case 3 -> 0xFFA3E635;
            case 2 -> 0xFFFACC15;
            case 1 -> 0xFFFB923C;
            default -> (simNet == null ? 0xFFFBBF24 : 0xFFEF4444);
        };
        g.drawString(mc.font, topText, boxX + 47, boxY + 3, topCol, false);

        String subText = bars > 0
                ? ((int) sig.rawDistance() + "m • " + sig.getSignalDbm() + "dBm")
                : (simNet != null ? HotelAccess.simLabel(simNet) : hasAntenna ? "Satellite" : "Insert SIM");
        g.drawString(mc.font, mc.font.plainSubstrByWidth(subText, 68), boxX + 47, boxY + 12, 0xFF94A3B8, false);

        if (hasOrders) {
            ModNetwork.OrderSnapshot first = orders.get(0);
            String ordLine = first.orderId().equals(RangerDispatch.ORDER_ID) || first.orderId().equals(CampDispatch.ORDER_ID)
                    ? first.status() : "🚕 " + first.status();
            g.drawString(mc.font, mc.font.plainSubstrByWidth(ordLine, boxW - 8), boxX + 4, boxY + 23, 0xFFFBBF24, false);
        }
    }
}
