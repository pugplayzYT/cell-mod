package com.example.intertnet;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

final class RangerScreen extends Screen {
    private static final int WIDTH = 216;
    private static final int HEIGHT = 146;
    private final Screen phone;
    private final int rangerId;
    private int left;
    private int top;
    private Button call;
    private ModNetwork.RangerQuotePayload quote;
    private String feedback = "";

    RangerScreen(Screen phone) {
        this(phone, -1);
    }

    RangerScreen(int rangerId) {
        this(null, rangerId);
    }

    private RangerScreen(Screen phone, int rangerId) {
        super(Component.literal("Forest Ranger"));
        this.phone = phone;
        this.rangerId = rangerId;
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        if (rangerId >= 0) {
            addRenderableWidget(Button.builder(Component.literal("Escort me to the lookout"), button -> {
                PacketDistributor.sendToServer(new ModNetwork.RangerEscortPayload(rangerId));
                onClose();
            }).bounds(left + 12, top + 70, WIDTH - 24, 20).build());
        } else {
            addRenderableWidget(Button.builder(Component.literal("Find lookout"), button -> requestQuote())
                    .bounds(left + 12, top + 91, 80, 20).build());
            call = addRenderableWidget(Button.builder(Component.literal("Call · " + RangerDispatch.PRICE + " emeralds"), button -> {
                call.active = false;
                feedback = "Contacting the ranger…";
                PacketDistributor.sendToServer(new ModNetwork.RangerCallPayload(true));
            }).bounds(left + 98, top + 91, WIDTH - 110, 20).build());
            requestQuote();
        }
        addRenderableWidget(Button.builder(Component.literal(phone == null ? "Close" : "Back"), button -> onClose())
                .bounds(left + 12, top + 119, 64, 18).build());
    }

    private void requestQuote() {
        quote = null;
        call.active = false;
        feedback = "Finding a nearby lookout…";
        PacketDistributor.sendToServer(new ModNetwork.RangerCallPayload(false));
    }

    void setQuote(ModNetwork.RangerQuotePayload value) {
        if (call == null) return;
        quote = value;
        feedback = value.message();
        call.active = value.seconds() > 0;
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
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, 0xFF111E19);
        graphics.renderOutline(left, top, WIDTH, HEIGHT, 0xFF507766);
        graphics.drawCenteredString(font, title, left + WIDTH / 2, top + 9, 0xFFBAF1C0);
        if (rangerId >= 0) {
            line(graphics, "I can get you back to the lookout.", 35, 0xFFE2E8F0);
            line(graphics, "Stay close and follow me upstairs.", 49, 0xFF9AAFA2);
        } else {
            var active = ClientPhoneState.getActiveOrders().stream()
                    .filter(order -> order.orderId().equals(RangerDispatch.ORDER_ID)).findFirst();
            if (active.isPresent()) {
                line(graphics, active.get().status(), 28, 0xFFFFD78E);
                line(graphics, active.get().destCoords(), 41, 0xFF9AAFA2);
            } else if (quote != null && quote.seconds() > 0) {
                line(graphics, "Lookout " + quote.distance() + "m away · ETA ~" + quote.seconds() + "s", 28, 0xFFFFD78E);
                line(graphics, "Pickup " + quote.pickup(), 41, 0xFF9AAFA2);
            } else {
                line(graphics, "Forest escorts to staffed lookouts", 28, 0xFF9AAFA2);
            }
            var lines = font.split(Component.literal(feedback), WIDTH - 24);
            for (int i = 0; i < Math.min(lines.size(), 3); i++) {
                graphics.drawCenteredString(font, lines.get(i), left + WIDTH / 2, top + 56 + i * 10, 0xFFE2E8F0);
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void line(GuiGraphics graphics, String text, int y, int color) {
        graphics.drawCenteredString(font, PhoneScreen.fit(font, text, WIDTH - 24), left + WIDTH / 2, top + y, color);
    }
}
