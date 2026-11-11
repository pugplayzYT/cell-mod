package com.example.intertnet;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

final class HotelReceptionScreen extends Screen {
    private static final int WIDTH = 216;
    private static final int HEIGHT = 126;
    private final int receptionist;
    private int left;
    private int top;
    private String feedback = "Buy a pass, then use the entrance and room hoppers.";

    HotelReceptionScreen(int receptionist) {
        super(Component.literal("Hotel Reception"));
        this.receptionist = receptionist;
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        addRenderableWidget(Button.builder(Component.literal("Hotel Pass · " + HotelAccess.PRICE + " emeralds"), button ->
                        PacketDistributor.sendToServer(new ModNetwork.BuyHotelPassPayload(receptionist)))
                .bounds(left + 12, top + 63, WIDTH - 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                .bounds(left + 12, top + 94, 64, 18).build());
    }

    void setFeedback(String message) {
        feedback = message;
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics, int x, int y, float tick) {}

    @Override
    public void render(GuiGraphics graphics, int x, int y, float partialTick) {
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, 0xFF211C17);
        graphics.renderOutline(left, top, WIDTH, HEIGHT, 0xFF8D7655);
        graphics.drawCenteredString(font, title, left + WIDTH / 2, top + 9, 0xFFFFD78E);
        var lines = font.split(Component.literal(feedback), WIDTH - 24);
        for (int i = 0; i < Math.min(lines.size(), 3); i++) {
            graphics.drawCenteredString(font, lines.get(i), left + WIDTH / 2, top + 29 + 10 * i, 0xFFE2E8F0);
        }
        super.render(graphics, x, y, partialTick);
    }
}
