package com.example.intertnet;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PhoneScreen extends Screen {
    public static final ResourceLocation[] SIGNAL_TEXTURES = new ResourceLocation[]{
            ResourceLocation.fromNamespaceAndPath(IntertnetMod.MODID, "textures/gui/signal_0.png"),
            ResourceLocation.fromNamespaceAndPath(IntertnetMod.MODID, "textures/gui/signal_1.png"),
            ResourceLocation.fromNamespaceAndPath(IntertnetMod.MODID, "textures/gui/signal_2.png"),
            ResourceLocation.fromNamespaceAndPath(IntertnetMod.MODID, "textures/gui/signal_3.png"),
            ResourceLocation.fromNamespaceAndPath(IntertnetMod.MODID, "textures/gui/signal_4.png")
    };
    private static final ResourceLocation SIM_ICON = ResourceLocation.fromNamespaceAndPath(IntertnetMod.MODID, "textures/item/sim_card.png");

    private static final int PHONE_W = 218;
    private static final int PHONE_H = 207;
    private static final String[] APPS = {"Messages", "SOS", "Delivery", "Rideshare"};
    private static final int TAB_GAP = 2;
    private static final int TAB_W = (PHONE_W - 10 - TAB_GAP * (APPS.length - 1)) / APPS.length;

    private int phoneX;
    private int phoneY;

    private EditBox messageInputBox;
    private Button satelliteAntennaButton;
    private int scrollOffset = 0;

    private String statusToast = "";
    private int statusToastColor = 0xFF34D399;
    private int statusToastTicks = 0;

    public PhoneScreen() {
        super(Component.translatable("gui.intertnet.phone.title"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Keep game world visible behind the phone
    }

    @Override
    protected void init() {
        super.init();
        this.phoneX = (this.width - PHONE_W) / 2;
        this.phoneY = (this.height - PHONE_H) / 2;

        this.messageInputBox = new EditBox(this.font, this.phoneX + 10, this.phoneY + 150, PHONE_W - 54, 14, Component.literal("Message..."));
        this.messageInputBox.setMaxLength(110);
        this.messageInputBox.setHint(Component.literal("Message network..."));
        this.addRenderableWidget(this.messageInputBox);
        this.satelliteAntennaButton = this.addRenderableWidget(Button.builder(Component.empty(), button ->
                        PacketDistributor.sendToServer(new ModNetwork.TogglePhoneAntennaPayload()))
                .bounds(this.phoneX + 27, this.phoneY + 188, PHONE_W - 34, 15).build());

        this.messageInputBox.setFocused(true);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.statusToastTicks > 0) this.statusToastTicks--;
    }

    private void showToast(String msg, int color) {
        this.statusToast = msg;
        this.statusToastColor = color;
        this.statusToastTicks = 65;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        String simNet = DeliveryManager.findPlayerSimNetwork(player);
        boolean hasAntenna = PhoneItem.hasSatelliteAntenna(DeliveryManager.findPlayerPhoneStack(player));
        SignalManager.SignalStatus sig = SignalManager.getBestSignal(player, simNet);
        int bars = Mth.clamp(sig.bars(), 0, 4);

        int x = this.phoneX;
        int y = this.phoneY;

        // 1. Compact Rugged Phone Bezel & Antenna
        g.fill(x + PHONE_W - 28, y - 10, x + PHONE_W - 16, y, 0xFF334155);
        g.fill(x + PHONE_W - 28, y - 14, x + PHONE_W - 16, y - 10, bars > 0 ? 0xFF10B981 : 0xFFF59E0B);
        g.renderOutline(x + PHONE_W - 28, y - 14, 12, 14, 0xFF0F172A);

        g.fill(x - 3, y - 3, x + PHONE_W + 3, y + PHONE_H + 3, 0xFF1E293B);
        g.renderOutline(x - 3, y - 3, PHONE_W + 6, PHONE_H + 6, 0xFF64748B);
        g.fill(x, y, x + PHONE_W, y + PHONE_H, 0xFF0F172A);
        g.renderOutline(x, y, PHONE_W, PHONE_H, 0xFF334155);

        // 2. Top Status Bar (y+5 to y+29)
        g.fill(x + 5, y + 5, x + PHONE_W - 5, y + 29, 0xFF090D16);
        g.renderOutline(x + 5, y + 5, PHONE_W - 10, 24, 0xFF1E293B);

        g.blit(SIGNAL_TEXTURES[bars], x + 8, y + 8, 0, 0, 48, 18, 48, 18);

        String sigLabel;
        int sigColor;
        if (bars > 0) {
            String netId = ClientPhoneState.getSignal(player, simNet) != null
                    ? ClientPhoneState.getCurrentNetworkLabel()
                    : TowerNetworkManager.getNetworkId(player.level(), sig.connectedTowerPos());
            sigLabel = sig.connectedViaGlobalSatellite() ? "SATELLITE • " + bars + "/4" : bars + "/4 • " + netId;
            sigColor = switch (bars) {
                case 4 -> 0xFF34D399;
                case 3 -> 0xFFA3E635;
                case 2 -> 0xFFFACC15;
                default -> 0xFFFB923C;
            };
        } else if (sig.satelliteSkyBlockedNearby()) {
            sigLabel = "SATELLITE SKY BLOCKED";
            sigColor = 0xFFF97316;
        } else if (simNet == null && !hasAntenna) {
            sigLabel = "NO SIM CARD";
            sigColor = 0xFFFBBF24;
        } else if (simNet != null && sig.missingOrWrongSim()) {
            sigLabel = "WRONG SIM NET";
            sigColor = 0xFFEF4444;
        } else if (simNet != null && sig.unpoweredTowerNearby()) {
            sigLabel = "TOWER NO FUEL";
            sigColor = 0xFFF97316;
        } else {
            sigLabel = hasAntenna ? "NO SATELLITE SIGNAL" : "NO CELL SIGNAL";
            sigColor = 0xFFEF4444;
        }
        int statusWidth = PHONE_W - 84;
        g.drawString(this.font, fit(this.font, sigLabel, statusWidth), x + 60, y + 7, sigColor, false);

        String subLine;
        if (bars > 0) {
            subLine = String.format(Locale.ROOT, "%dm · %ddBm", (int) sig.rawDistance(), sig.getSignalDbm());
        } else if (sig.satelliteSkyBlockedNearby()) {
            subLine = "Max " + SatelliteConnectorBlock.MAX_SKY_OBSTRUCTIONS + " blocks above";
        } else if (hasAntenna) {
            subLine = "Global Satellite covers the Overworld";
        } else if (simNet == null) {
            subLine = "Buy or print a SIM";
        } else {
            subLine = "Find a matching tower";
        }
        g.drawString(this.font, fit(this.font, subLine, statusWidth), x + 60, y + 18, 0xFF94A3B8, false);

        // Close [X] Button at top-right
        int closeX = x + PHONE_W - 19;
        int closeY = y + 7;
        boolean closeHov = mouseX >= closeX && mouseX <= closeX + 12 && mouseY >= closeY && mouseY <= closeY + 12;
        g.fill(closeX, closeY, closeX + 12, closeY + 12, closeHov ? 0xFFEF4444 : 0xFF991B1B);
        g.renderOutline(closeX, closeY, 12, 12, 0xFFFCA5A5);
        g.drawString(this.font, "X", closeX + 3, closeY + 2, 0xFFFFFFFF, false);

        // 3. SIM Card Slot Bar (y+31 to y+45)
        g.fill(x + 5, y + 31, x + PHONE_W - 5, y + 45, 0xFF111827);
        g.renderOutline(x + 5, y + 31, PHONE_W - 10, 14, 0xFF1E293B);

        // SIM Slot Icon Recess
        g.fill(x + 7, y + 32, x + 19, y + 44, 0xFF090D16);
        g.renderOutline(x + 7, y + 32, 12, 12, simNet != null ? 0xFF10B981 : 0xFF475569);
        if (simNet != null) {
            g.blit(SIM_ICON, x + 8, y + 33, 0, 0, 10, 10, 10, 10);
        }

        if (simNet == null) {
            g.drawString(this.font, "SIM Slot: Empty", x + 22, y + 34, 0xFFFBBF24, false);
            int insX = x + PHONE_W - 60;
            int insY = y + 32;
            boolean insHov = mouseX >= insX && mouseX <= insX + 52 && mouseY >= insY && mouseY <= insY + 12;
            g.fill(insX, insY, insX + 52, insY + 12, insHov ? 0xFF10B981 : 0xFF059669);
            g.renderOutline(insX, insY, 52, 12, 0xFF34D399);
            g.drawCenteredString(this.font, "Insert SIM", insX + 26, insY + 2, 0xFFFFFFFF);
        } else {
            String simLabel = fit(this.font, "SIM: " + simNet, PHONE_W - 100);
            g.drawString(this.font, simLabel, x + 22, y + 34, 0xFF34D399, false);

            int swapX = x + PHONE_W - 74;
            int swapY = y + 32;
            boolean swapHov = mouseX >= swapX && mouseX <= swapX + 32 && mouseY >= swapY && mouseY <= swapY + 12;
            g.fill(swapX, swapY, swapX + 32, swapY + 12, swapHov ? 0xFF0284C7 : 0xFF0369A1);
            g.renderOutline(swapX, swapY, 32, 12, 0xFF38BDF8);
            g.drawCenteredString(this.font, "Swap", swapX + 16, swapY + 2, 0xFFFFFFFF);

            int ejX = x + PHONE_W - 40;
            int ejY = y + 32;
            boolean ejHov = mouseX >= ejX && mouseX <= ejX + 32 && mouseY >= ejY && mouseY <= ejY + 12;
            g.fill(ejX, ejY, ejX + 32, ejY + 12, ejHov ? 0xFFEF4444 : 0xFF991B1B);
            g.renderOutline(ejX, ejY, 32, 12, 0xFFFCA5A5);
            g.drawCenteredString(this.font, "Eject", ejX + 16, ejY + 2, 0xFFFFFFFF);
        }

        int tabY = y + 47;
        for (int i = 0; i < APPS.length; i++) {
            int tabX = x + 5 + i * (TAB_W + TAB_GAP);
            boolean active = i == 0;
            g.fill(tabX, tabY, tabX + TAB_W, tabY + 14, active ? 0xFF0284C7 : 0xFF1E293B);
            g.renderOutline(tabX, tabY, TAB_W, 14, active ? 0xFF38BDF8 : 0xFF475569);
            g.drawCenteredString(this.font, fit(this.font, APPS[i], TAB_W - 4), tabX + TAB_W / 2, tabY + 3, 0xFFFFFFFF);
        }

        // 5. App Body Area
        renderMessagesApp(g, bars, mouseX, mouseY);

        // 6. Bottom Status Bar (y+170 to y+185)
        int footX = x + 6;
        int footY = y + 170;
        int footW = PHONE_W - 12;
        int footH = 15;
        g.fill(footX, footY, footX + footW, footY + footH, 0xFF090D16);
        g.renderOutline(footX, footY, footW, footH, 0xFF334155);

        if (this.statusToastTicks > 0) {
            g.drawCenteredString(this.font, this.font.plainSubstrByWidth(this.statusToast, footW - 6), footX + footW / 2, footY + 4, this.statusToastColor);
        } else if (!ClientPhoneState.getActiveOrders().isEmpty()) {
            ModNetwork.OrderSnapshot first = ClientPhoneState.getActiveOrders().get(0);
            String ordText = "🚕 " + first.status();
            g.drawCenteredString(this.font, this.font.plainSubstrByWidth(ordText, footW - 6), footX + footW / 2, footY + 4, 0xFFFBBF24);
        } else {
            String hint = sig.connectedViaGlobalSatellite() ? "Connected via Global Satellite"
                    : bars > 0 ? "Connected via SIM [" + simNet + "]" : "Find reception to use phone apps";
            g.drawCenteredString(this.font, this.font.plainSubstrByWidth(hint, footW - 6), footX + footW / 2, footY + 4, 0xFF64748B);
        }

        g.renderItem(new ItemStack(IntertnetMod.PERSONAL_SATELLITE_ANTENNA.get()), x + 7, y + 187);
        satelliteAntennaButton.setMessage(Component.literal(hasAntenna ? "Satellite SIM ready • Remove antenna" : "Attach satellite antenna"));
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderMessagesApp(GuiGraphics g, int bars, int mouseX, int mouseY) {
        int boxX = this.phoneX + 8;
        int boxY = this.phoneY + 64;
        int boxW = PHONE_W - 16;

        // Connected Peers Header (y+64 to y+78)
        g.fill(boxX, boxY, boxX + boxW, boxY + 14, 0xFF111827);
        List<String> peers = ClientPhoneState.getPeersOnNetwork();
        String peerLine = bars > 0
                ? "Online (" + peers.size() + "): " + String.join(", ", peers)
                : "No Signal — Connect to any Carrier";
        g.drawString(this.font, fit(this.font, peerLine, boxW - 8), boxX + 4, boxY + 3, 0xFF38BDF8, false);

        // Chat Log Area (y+80 to y+147)
        int chatY = this.phoneY + 80;
        int chatH = 67;
        g.fill(boxX, chatY, boxX + boxW, chatY + chatH, 0xFF090D16);
        g.renderOutline(boxX, chatY, boxW, chatH, 0xFF1E293B);

        List<ModNetwork.MessageSnapshot> msgs = ClientPhoneState.getMessages();
        if (msgs.isEmpty()) {
            g.drawCenteredString(this.font, "No messages on this network yet.", boxX + boxW / 2, chatY + 26, 0xFF64748B);
        } else {
            List<FormattedCharSequence> wrappedLines = new ArrayList<>();
            for (ModNetwork.MessageSnapshot m : msgs) {
                Component line = Component.literal("<" + m.senderName() + "> ").withStyle(s -> s.withColor(0x38BDF8))
                        .append(Component.literal(m.text()).withStyle(s -> s.withColor(0xE2E8F0)));
                wrappedLines.addAll(this.font.split(line, boxW - 8));
            }
            int maxLines = 6;
            int startIdx = Math.max(0, wrappedLines.size() - maxLines - this.scrollOffset);
            int endIdx = Math.min(wrappedLines.size(), startIdx + maxLines);
            for (int i = startIdx; i < endIdx; i++) {
                g.drawString(this.font, wrappedLines.get(i), boxX + 4, chatY + 4 + (i - startIdx) * 10, 0xFFFFFFFF, false);
            }
        }

        // [Send] Button next to messageInputBox (y+150)
        int sendX = this.phoneX + PHONE_W - 40;
        int sendY = this.phoneY + 150;
        boolean canSend = bars > 0;
        boolean sendHov = mouseX >= sendX && mouseX <= sendX + 32 && mouseY >= sendY && mouseY <= sendY + 14;
        g.fill(sendX, sendY, sendX + 32, sendY + 14, !canSend ? 0xFF334155 : (sendHov ? 0xFF0284C7 : 0xFF0369A1));
        g.renderOutline(sendX, sendY, 32, 14, canSend ? 0xFF38BDF8 : 0xFF475569);
        g.drawCenteredString(this.font, "Send", sendX + 16, sendY + 3, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int x = this.phoneX;
            int y = this.phoneY;

            // 1. Close [X] Button
            int closeX = x + PHONE_W - 19;
            int closeY = y + 7;
            if (mouseX >= closeX && mouseX <= closeX + 12 && mouseY >= closeY && mouseY <= closeY + 12) {
                this.onClose();
                return true;
            }

            Player player = Minecraft.getInstance().player;
            if (player == null) return super.mouseClicked(mouseX, mouseY, button);
            String simNet = DeliveryManager.findPlayerSimNetwork(player);

            // 2. SIM Card Slot Buttons
            if (simNet == null) {
                int insX = x + PHONE_W - 60;
                int insY = y + 32;
                if (mouseX >= insX && mouseX <= insX + 52 && mouseY >= insY && mouseY <= insY + 12) {
                    PacketDistributor.sendToServer(new ModNetwork.ManagePhoneSimPayload(false));
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.1f));
                    return true;
                }
            } else {
                int swapX = x + PHONE_W - 74;
                int swapY = y + 32;
                if (mouseX >= swapX && mouseX <= swapX + 32 && mouseY >= swapY && mouseY <= swapY + 12) {
                    PacketDistributor.sendToServer(new ModNetwork.ManagePhoneSimPayload(false));
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.1f));
                    return true;
                }
                int ejX = x + PHONE_W - 40;
                int ejY = y + 32;
                if (mouseX >= ejX && mouseX <= ejX + 32 && mouseY >= ejY && mouseY <= ejY + 12) {
                    PacketDistributor.sendToServer(new ModNetwork.ManagePhoneSimPayload(true));
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.9f));
                    return true;
                }
            }

            int tabY = y + 47;
            if (mouseY >= tabY && mouseY <= tabY + 14) {
                for (int i = 0; i < APPS.length; i++) {
                    int tabX = x + 5 + i * (TAB_W + TAB_GAP);
                    if (mouseX >= tabX && mouseX < tabX + TAB_W) {
                        if (i == 0) {
                            this.scrollOffset = 0;
                            this.messageInputBox.setFocused(true);
                        } else if (i == 1) {
                            Minecraft.getInstance().setScreen(new EmergencyScreen(this));
                        } else {
                            var mode = i == 2 ? TownServices.Mode.DELIVERY : TownServices.Mode.RIDESHARE;
                            Minecraft.getInstance().setScreen(new TownServiceScreen(mode, this, 0));
                        }
                        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                        return true;
                    }
                }
            }

            int sendX = x + PHONE_W - 40;
            int sendY = y + 150;
            if (mouseX >= sendX && mouseX <= sendX + 32 && mouseY >= sendY && mouseY <= sendY + 14) {
                sendCurrentMessage(SignalManager.getBestSignal(player, simNet).bars());
                return true;
            }

        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void sendCurrentMessage(int bars) {
        if (bars <= 0) {
            showToast("Need reception to message!", 0xFFEF4444);
            return;
        }
        if (this.messageInputBox == null) return;
        String txt = this.messageInputBox.getValue().trim();
        if (!txt.isEmpty()) {
            PacketDistributor.sendToServer(new ModNetwork.SendNetworkMessagePayload(txt));
            this.messageInputBox.setValue("");
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.15f));
        }
    }

    static String fit(Font font, String label, int width) {
        if (font.width(label) <= width) {
            return label;
        }
        return font.plainSubstrByWidth(label, Math.max(0, width - font.width("…"))) + "…";
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int total = ClientPhoneState.getMessages().size();
        int maxScroll = Math.max(0, total - 3);
        this.scrollOffset = Mth.clamp(this.scrollOffset - (int) Math.signum(scrollY) * 2, 0, maxScroll);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            this.onClose();
            return true;
        }
        if (this.messageInputBox != null && this.messageInputBox.isFocused()) {
            if (keyCode == 257 || keyCode == 335) {
                Player player = Minecraft.getInstance().player;
                String simNet = player != null ? DeliveryManager.findPlayerSimNetwork(player) : null;
                int bars = player != null ? SignalManager.getBestSignal(player, simNet).bars() : 0;
                sendCurrentMessage(bars);
                return true;
            }
            if (this.messageInputBox.keyPressed(keyCode, scanCode, modifiers) || this.messageInputBox.canConsumeInput()) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
