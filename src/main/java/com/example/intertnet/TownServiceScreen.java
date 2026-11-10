package com.example.intertnet;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class TownServiceScreen extends Screen {
    private static final int MAX_PANEL_WIDTH = 232;
    private static final int PANEL_HEIGHT = 188;
    private final TownServices.Mode mode;
    private final Screen parent;
    private final List<Button> orderButtons = new ArrayList<>();
    private List<TownServices.Choice> choices = List.of();
    private int reference;
    private String message = "Finding nearby services…";
    private String feedback = "";
    private boolean requestedQuotes;
    private boolean wasOnline;
    private int panelWidth;
    private int panelX;
    private int panelY;

    TownServiceScreen(TownServices.Mode mode, Screen parent, int reference) {
        super(Component.literal(mode.label));
        this.mode = mode;
        this.parent = parent;
        this.reference = reference;
        if (mode == TownServices.Mode.DINING) {
            choices = Arrays.stream(Meal.values())
                    .map(meal -> new TownServices.Choice(meal.ordinal(), meal.label, 0, meal.price)).toList();
            message = "Three portions per meal";
        }
    }

    @Override
    protected void init() {
        panelWidth = Math.min(width - 12, MAX_PANEL_WIDTH);
        panelX = (width - panelWidth) / 2;
        panelY = (height - PANEL_HEIGHT) / 2;
        orderButtons.clear();
        for (int i = 0; i < 3; i++) {
            int index = i;
            orderButtons.add(addRenderableWidget(Button.builder(Component.empty(), button -> placeOrder(index))
                    .bounds(panelX + 10, panelY + 42 + i * 22, panelWidth - 20, 20).build()));
        }
        int navWidth = Math.min(84, (panelWidth - 26) / 2);
        addRenderableWidget(Button.builder(Component.literal(parent == null ? "Close" : "Back"), button -> onClose())
                .bounds(panelX + 10, panelY + 163, navWidth, 19).build());
        if (mode != TownServices.Mode.DINING) {
            addRenderableWidget(Button.builder(Component.literal("Refresh"), button -> refreshQuotes())
                    .bounds(panelX + panelWidth - 10 - navWidth, panelY + 163, navWidth, 19).build());
            if (!requestedQuotes) {
                refreshQuotes();
            }
        }
        wasOnline = isOnline();
        updateButtons();
    }

    private void refreshQuotes() {
        requestedQuotes = true;
        message = "Finding nearby services…";
        feedback = "";
        PacketDistributor.sendToServer(new ModNetwork.ServiceRequestPayload(mode.ordinal(), true, 0, 0));
    }

    private void placeOrder(int index) {
        if (index >= choices.size()) {
            return;
        }
        var choice = choices.get(index);
        feedback = "Sending request…";
        PacketDistributor.sendToServer(new ModNetwork.ServiceRequestPayload(mode.ordinal(), false, choice.id(), reference));
    }

    void setQuotes(ModNetwork.ServiceQuotesPayload payload) {
        if (payload.mode() == mode.ordinal()) {
            reference = payload.reference();
            message = payload.message();
            choices = payload.choices();
            updateButtons();
        }
    }

    void setFeedback(ModNetwork.ServiceFeedbackPayload payload) {
        if (payload.mode() == mode.ordinal()) {
            feedback = payload.message();
        }
    }

    private void updateButtons() {
        for (int i = 0; i < orderButtons.size(); i++) {
            Button button = orderButtons.get(i);
            button.visible = i < choices.size();
            if (button.visible) {
                var choice = choices.get(i);
                String distance = mode == TownServices.Mode.RIDESHARE ? " · " + choice.distance() + "m" : "";
                String label = choice.name() + distance + " · " + choice.price() + " em.";
                button.setMessage(Component.literal(PhoneScreen.fit(font, label, button.getWidth() - 12)));
                button.setTooltip(Tooltip.create(Component.literal(choice.name() + distance + " · " + choice.price() + " emeralds")));
            }
        }
    }

    @Override
    public void tick() {
        if (mode != TownServices.Mode.DINING) {
            boolean online = isOnline();
            if (online && !wasOnline) {
                refreshQuotes();
            }
            wasOnline = online;
        }
    }

    private static boolean isOnline() {
        return !ClientPhoneState.getCurrentNetworkLabel().equals("Offline");
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Leave the world and approaching carts visible around the panel.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(panelX, panelY, panelX + panelWidth, panelY + PANEL_HEIGHT, 0xFF111827);
        g.renderOutline(panelX, panelY, panelWidth, PANEL_HEIGHT, 0xFF64748B);
        g.drawCenteredString(font, title, panelX + panelWidth / 2, panelY + 9, 0xFF7DD3FC);
        drawStatus(g, message, 22, 0xFFE2E8F0);

        ClientPhoneState.getActiveOrders().stream().filter(order -> order.orderId().equals(mode.name())).findFirst()
                .ifPresent(order -> {
                    String remaining = order.remainingMeters() > 0 ? " · " + order.remainingMeters() + "m" : "";
                    g.drawCenteredString(font, PhoneScreen.fit(font, order.status() + remaining, panelWidth - 20),
                            panelX + panelWidth / 2, panelY + 111, 0xFFFBBF24);
                    g.drawCenteredString(font, PhoneScreen.fit(font, order.destCoords(), panelWidth - 20),
                            panelX + panelWidth / 2, panelY + 123, 0xFF94A3B8);
                    int barWidth = panelWidth - 20;
                    g.fill(panelX + 10, panelY + 134, panelX + 10 + barWidth, panelY + 137, 0xFF334155);
                    g.fill(panelX + 10, panelY + 134, panelX + 10 + barWidth * order.progressPercent() / 100,
                            panelY + 137, 0xFF10B981);
                });
        drawStatus(g, feedback, 141, 0xFF7DD3FC);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawStatus(GuiGraphics graphics, String text, int y, int color) {
        var lines = font.split(Component.literal(text), panelWidth - 20);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            graphics.drawCenteredString(font, lines.get(i), panelX + panelWidth / 2, panelY + y + i * 9, color);
        }
    }
}
