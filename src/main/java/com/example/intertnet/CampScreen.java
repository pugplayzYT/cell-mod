package com.example.intertnet;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

final class CampScreen extends Screen {
    private static final int WIDTH = 216;
    private static final int HEIGHT = 144;
    private final Screen phone;
    private int left;
    private int top;
    private Button order;
    private ModNetwork.CampQuotePayload quote;
    private String feedback = "";

    CampScreen(Screen phone) {
        super(Component.literal("Rescue Camp"));
        this.phone = phone;
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        addRenderableWidget(Button.builder(Component.literal("Find clearing"), button -> findSite())
                .bounds(left + 12, top + 89, 84, 20).build());
        order = addRenderableWidget(Button.builder(Component.literal("Order · " + CampDispatch.PRICE + " emeralds"), button -> {
            order.active = false;
            PacketDistributor.sendToServer(new ModNetwork.CampCallPayload(true));
        }).bounds(left + 102, top + 89, WIDTH - 114, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), button -> onClose())
                .bounds(left + 12, top + 117, 64, 18).build());
        findSite();
    }

    private void findSite() {
        quote = null;
        order.active = false;
        feedback = "Checking the ground and nearby lookouts…";
        PacketDistributor.sendToServer(new ModNetwork.CampCallPayload(false));
    }

    void setQuote(ModNetwork.CampQuotePayload quote) {
        this.quote = quote;
        feedback = quote.message();
        order.active = quote.seconds() > 0;
    }

    void setFeedback(String message) {
        feedback = message;
    }

    @Override public void onClose() { minecraft.setScreen(phone); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics, int x, int y, float tick) {}

    @Override
    public void render(GuiGraphics graphics, int x, int y, float partialTick) {
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, 0xFF111E19);
        graphics.renderOutline(left, top, WIDTH, HEIGHT, 0xFF507766);
        graphics.drawCenteredString(font, title, left + WIDTH / 2, top + 9, 0xFFBAF1C0);
        var active = ClientPhoneState.getActiveOrders().stream()
                .filter(snapshot -> snapshot.orderId().equals(CampDispatch.ORDER_ID)).findFirst();
        if (active.isPresent()) {
            line(graphics, active.get().status(), 27);
            line(graphics, active.get().destCoords(), 39);
        } else if (quote != null && quote.seconds() > 0) {
            line(graphics, "Site " + quote.site(), 27);
            line(graphics, "Ranger crew · ETA ~" + quote.seconds() + "s", 39);
        }
        var lines = font.split(Component.literal(feedback), WIDTH - 24);
        for (int i = 0; i < Math.min(lines.size(), 3); i++) {
            graphics.drawCenteredString(font, lines.get(i), left + WIDTH / 2, top + 53 + 10 * i, 0xFFE2E8F0);
        }
        super.render(graphics, x, y, partialTick);
    }

    private void line(GuiGraphics graphics, String value, int y) {
        graphics.drawCenteredString(font, PhoneScreen.fit(font, value, WIDTH - 24), left + WIDTH / 2, top + y, 0xFFFFD78E);
    }
}
