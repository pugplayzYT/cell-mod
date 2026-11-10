package com.example.intertnet;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Set;

public class CellTowerScreen extends AbstractContainerScreen<CellTowerMenu> {
    private static final ResourceLocation SIG_0 = ResourceLocation.fromNamespaceAndPath(IntertnetMod.MODID, "textures/gui/signal_0.png");
    private static final ResourceLocation SIG_4 = ResourceLocation.fromNamespaceAndPath(IntertnetMod.MODID, "textures/gui/signal_4.png");
    private static final ResourceLocation SIM_ICON = ResourceLocation.fromNamespaceAndPath(IntertnetMod.MODID, "textures/item/sim_card.png");

    // 0 = Main Dashboard, 1 = Wireless Tower Linker
    private int viewMode = 0;
    private Button printSimBtn;
    private Button openTowersBtn;
    private int listScrollOffset = 0;

    public CellTowerScreen(CellTowerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 202;
        this.imageHeight = 210;
        this.inventoryLabelX = 20;
        this.inventoryLabelY = 116;
    }

    @Override
    protected void init() {
        super.init();

        this.printSimBtn = this.addRenderableWidget(Button.builder(getPrintSimButtonLabel(), btn -> {
            PacketDistributor.sendToServer(new ModNetwork.PrintSimCardPayload(this.menu.getTowerPos()));
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.1f));
        }).bounds(this.leftPos + 12, this.topPos + 92, 122, 18)
                .tooltip(Tooltip.create(Component.literal("Prints a SIM Card for this network.\nSatellite Connectors include 1 FREE SIM Card!\nExtra SIMs cost: 1 Paper + 1 Gold Nugget.")))
                .build());

        this.openTowersBtn = this.addRenderableWidget(Button.builder(getTowersButtonLabel(), btn -> {
            if (this.menu.isSatelliteConnector()) return;
            this.viewMode = (this.viewMode == 1) ? 0 : 1;
            this.listScrollOffset = 0;
            updateWidgetVisibility();
        }).bounds(this.leftPos + 138, this.topPos + 92, 52, 18).build());

        updateWidgetVisibility();
    }

    private Component getPrintSimButtonLabel() {
        if (this.menu.isFreeSimAvailable()) {
            return Component.literal("★ Print 1 FREE SIM Card ★");
        }
        return Component.literal("Print SIM (1 Paper + 1 Nugget)");
    }

    private void updateWidgetVisibility() {
        boolean isSat = this.menu.isSatelliteConnector();
        if (isSat) {
            this.viewMode = 0;
        }
        this.menu.setFuelSlotVisible(this.viewMode == 0);
        if (this.openTowersBtn != null) {
            this.openTowersBtn.visible = !isSat;
        }
        if (this.printSimBtn != null) {
            this.printSimBtn.setWidth(isSat ? 178 : 122);
            this.printSimBtn.setMessage(getPrintSimButtonLabel());
        }
    }

    private Component getTowersButtonLabel() {
        if (this.viewMode == 1) {
            return Component.literal("<- Back");
        }
        int linked = 1;
        if (Minecraft.getInstance().level != null) {
            linked = TowerNetworkManager.getConnectedNetworkTowers(Minecraft.getInstance().level, this.menu.getTowerPos()).size();
        }
        return Component.literal("Net (" + linked + ")");
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateWidgetVisibility();
        if (this.openTowersBtn != null) {
            this.openTowersBtn.setMessage(getTowersButtonLabel());
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        g.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xFF1A202C);
        g.renderOutline(x, y, this.imageWidth, this.imageHeight, 0xFF4A5568);
        g.renderOutline(x + 2, y + 2, this.imageWidth - 4, this.imageHeight - 4, 0xFF2D3748);

        g.fill(x + 4, y + 4, x + this.imageWidth - 4, y + 24, 0xFF0F172A);
        var level = Minecraft.getInstance().level;
        boolean skyBlocked = this.menu.isSatelliteConnector() && level != null
                && level.getBlockEntity(this.menu.getTowerPos()) instanceof CellTowerBlockEntity tower
                && !tower.hasSatelliteSkyAccess();
        boolean powered = this.menu.getFuelRemaining() > 0 && !skyBlocked;
        ResourceLocation sigTex = powered ? SIG_4 : SIG_0;
        g.blit(sigTex, x + this.imageWidth - 54, y + 5, 0, 0, 46, 17, 46, 17);

        if (this.viewMode == 0) {
            // Main Dashboard
            g.fill(x + 12, y + 28, x + this.imageWidth - 12, y + 88, 0xFF111827);
            g.renderOutline(x + 12, y + 28, this.imageWidth - 24, 60, 0xFF334155);

            int slotX = x + 25;
            int slotY = y + 51;
            g.fill(slotX - 2, slotY - 2, slotX + 20, slotY + 20, 0xFF090D16);
            g.renderOutline(slotX - 2, slotY - 2, 22, 22, powered ? 0xFF10B981 : 0xFFEF4444);

            int fuelRem = this.menu.getFuelRemaining();
            int fuelMax = Math.max(1, this.menu.getFuelMax());
            float ratio = Mth.clamp((float) fuelRem / (float) fuelMax, 0f, 1f);

            int barX = x + 54;
            int barY = y + 54;
            int barW = 126;
            int barH = 10;
            g.fill(barX, barY, barX + barW, barY + barH, 0xFF090D16);
            int fillW = (int) (barW * ratio);
            if (fillW > 0) {
                g.fill(barX + 1, barY + 1, barX + fillW - 1, barY + barH - 1, skyBlocked ? 0xFFF59E0B : 0xFF10B981);
            }
            g.renderOutline(barX, barY, barW, barH, 0xFF475569);

            boolean isSat = this.menu.isSatelliteConnector();
            String statusText = skyBlocked ? "OFFLINE (Sky Blocked)" : powered
                    ? "ONLINE (" + this.menu.getActiveRange() + "m Range)"
                    : "OFFLINE (" + (isSat ? "Insert Copper" : "No Fuel") + ")";
            int statusCol = powered ? 0xFF34D399 : 0xFFF87171;
            String netId = Minecraft.getInstance().level != null
                    ? TowerNetworkManager.getNetworkId(Minecraft.getInstance().level, this.menu.getTowerPos())
                    : "NET";

            g.drawString(this.font, isSat ? "Cu" : "Fuel", x + 23, y + 36, 0xFF94A3B8, false);
            g.drawString(this.font, statusText, barX, y + 32, statusCol, false);
            g.drawString(this.font, "Reserve: " + fuelRem + " / " + (fuelRem > 0 ? fuelMax : 0) + "u", barX, y + 43, 0xFFCBD5E1, false);
            String fuelHint = isSat
                    ? "Copper Only • Portable 64m"
                    : "Au:96m Em:128m Dia:192m Net:256m";
            g.drawString(this.font, fuelHint, barX, y + 67, 0xFF64748B, false);
            g.blit(SIM_ICON, barX, y + 76, 0, 0, 9, 9, 9, 9);
            g.drawString(this.font, "SIM Net: " + netId, barX + 12, y + 77, 0xFF38BDF8, false);
        } else {
            // Wireless Tower Linker Panel (viewMode == 1)
            g.fill(x + 12, y + 28, x + this.imageWidth - 12, y + 88, 0xFF090D16);
            g.renderOutline(x + 12, y + 28, this.imageWidth - 24, 60, 0xFF10B981);
            g.drawString(this.font, "Towers in Radius (Click to Link):", x + 16, y + 32, 0xFF34D399, false);

            List<CellTowerBlockEntity> nearby = level != null
                    ? TowerNetworkManager.getTowersInRadius(level, this.menu.getTowerPos())
                    : List.of();
            Set<BlockPos> directLinks = Set.of();
            if (level != null && level.getBlockEntity(this.menu.getTowerPos()) instanceof CellTowerBlockEntity selfTower) {
                directLinks = selfTower.getWirelessLinks();
            }

            int listY = y + 43;
            int maxVisible = 3;
            this.listScrollOffset = Mth.clamp(this.listScrollOffset, 0, Math.max(0, nearby.size() - maxVisible));

            if (nearby.isEmpty()) {
                g.drawString(this.font, "No other Cell Towers in radius.", x + 20, listY + 14, 0xFF64748B, false);
            } else {
                for (int i = 0; i < maxVisible && (i + this.listScrollOffset) < nearby.size(); i++) {
                    CellTowerBlockEntity peer = nearby.get(i + this.listScrollOffset);
                    BlockPos pPos = peer.getBlockPos();
                    int dist = (int) Math.round(Math.sqrt(pPos.distSqr(this.menu.getTowerPos())));
                    boolean isLinked = directLinks.contains(pPos);

                    int rowY = listY + i * 14;
                    boolean rowHov = mouseX >= x + 15 && mouseX <= x + this.imageWidth - 15 && mouseY >= rowY && mouseY < rowY + 13;
                    g.fill(x + 15, rowY, x + this.imageWidth - 15, rowY + 13, rowHov ? 0xFF1E293B : 0xFF111827);

                    String label = "[" + pPos.toShortString() + "] " + dist + "m";
                    g.drawString(this.font, this.font.plainSubstrByWidth(label, 114), x + 18, rowY + 3, 0xFFE2E8F0, false);

                    String status = isLinked ? "Linked ✓" : "Connect";
                    int stCol = isLinked ? 0xFF34D399 : 0xFF38BDF8;
                    g.drawString(this.font, status, x + this.imageWidth - 18 - this.font.width(status), rowY + 3, stCol, false);
                }
            }
        }

        // Player Inventory Slot Grid Backgrounds
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                int sx = x + 19 + col * 18;
                int sy = y + 127 + row * 18;
                g.fill(sx, sy, sx + 18, sy + 18, 0xFF0F172A);
                g.renderOutline(sx, sy, 18, 18, 0xFF334155);
            }
        }
        for (int col = 0; col < 9; ++col) {
            int sx = x + 19 + col * 18;
            int sy = y + 185;
            g.fill(sx, sy, sx + 18, sy + 18, 0xFF0F172A);
            g.renderOutline(sx, sy, 18, 18, 0xFF334155);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font, this.title, 10, 10, 0xFFE2E8F0, false);
        g.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0xFF94A3B8, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && this.viewMode == 1 && Minecraft.getInstance().level != null) {
            List<CellTowerBlockEntity> nearby = TowerNetworkManager.getTowersInRadius(Minecraft.getInstance().level, this.menu.getTowerPos());
            int listY = this.topPos + 43;
            int maxVisible = 3;
            for (int i = 0; i < maxVisible && (i + this.listScrollOffset) < nearby.size(); i++) {
                CellTowerBlockEntity peer = nearby.get(i + this.listScrollOffset);
                int rowY = listY + i * 14;
                if (mouseX >= this.leftPos + 15 && mouseX <= this.leftPos + this.imageWidth - 15 && mouseY >= rowY && mouseY < rowY + 13) {
                    PacketDistributor.sendToServer(new ModNetwork.ToggleWirelessLinkPayload(this.menu.getTowerPos(), peer.getBlockPos()));
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.15f));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.viewMode == 1 && Minecraft.getInstance().level != null) {
            List<CellTowerBlockEntity> nearby = TowerNetworkManager.getTowersInRadius(Minecraft.getInstance().level, this.menu.getTowerPos());
            int maxScroll = Math.max(0, nearby.size() - 3);
            this.listScrollOffset = Mth.clamp(this.listScrollOffset - (int) Math.signum(scrollY), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (this.viewMode == 0) {
            this.renderTooltip(g, mouseX, mouseY);
        }
    }
}
