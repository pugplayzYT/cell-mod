package com.example.intertnet;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

final class EmergencyScreen extends Screen {
    private static final int PANEL_WIDTH = 216;
    private static final int PANEL_HEIGHT = 165;
    private final Screen phone;
    private int left;
    private int top;
    private String feedback = "";

    EmergencyScreen(Screen phone) {
        super(Component.literal("Emergency Services"));
        this.phone = phone;
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        for (EmergencyService service : EmergencyService.values()) {
            addRenderableWidget(Button.builder(Component.literal("Call " + service.label + " · Free"), button -> {
                feedback = "Contacting dispatch…";
                PacketDistributor.sendToServer(new ModNetwork.EmergencyCallPayload(service.ordinal()));
            }).bounds(left + 12, top + 33 + service.ordinal() * 47, PANEL_WIDTH - 24, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Back"), button -> onClose())
                .bounds(left + 12, top + 139, 70, 18).build());
    }

    void setFeedback(String message) {
        feedback = message;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(phone);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF111827);
        graphics.renderOutline(left, top, PANEL_WIDTH, PANEL_HEIGHT, 0xFF64748B);
        graphics.drawCenteredString(font, title, left + PANEL_WIDTH / 2, top + 8, 0xFFFCA5A5);
        graphics.drawCenteredString(font, "Cell and satellite calls", left + PANEL_WIDTH / 2, top + 21, 0xFFCBD5E1);
        for (EmergencyService service : EmergencyService.values()) {
            int y = top + 56 + service.ordinal() * 47;
            String orderId = "EMERGENCY_" + service.name();
            var tracking = ClientPhoneState.getActiveOrders().stream().filter(order -> order.orderId().equals(orderId)).findFirst();
            if (tracking.isPresent()) {
                var order = tracking.get();
                drawLine(graphics, order.status(), y, 0xFFFBBF24);
                drawLine(graphics, order.destCoords(), y + 10, 0xFF94A3B8);
            } else {
                drawLine(graphics, service == EmergencyService.MEDIC ? "Ambulance ride to the clinic"
                        : "Four officers and two ravagers", y, 0xFF94A3B8);
            }
        }
        var lines = font.split(Component.literal(feedback), PANEL_WIDTH - 24);
        for (int i = 0; i < Math.min(lines.size(), 2); i++) {
            graphics.drawCenteredString(font, lines.get(i), left + PANEL_WIDTH / 2, top + 120 + i * 9, 0xFF7DD3FC);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawLine(GuiGraphics graphics, String text, int y, int color) {
        graphics.drawCenteredString(font, PhoneScreen.fit(font, text, PANEL_WIDTH - 24), left + PANEL_WIDTH / 2, y, color);
    }
}
