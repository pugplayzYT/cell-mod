package com.example.intertnet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class CellTowerMenu extends AbstractContainerMenu {
    private final Container container;
    private final ContainerData data;
    private final BlockPos towerPos;
    private boolean fuelSlotVisible = true;

    public CellTowerMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory, new SimpleContainer(1), new SimpleContainerData(5), extraData);
    }

    private CellTowerMenu(int containerId, Inventory playerInventory, Container container, ContainerData data, FriendlyByteBuf extraData) {
        super(IntertnetMod.CELL_TOWER_MENU.get(), containerId);
        this.container = container;
        this.data = data;
        this.towerPos = extraData.readBlockPos();
        setupSlots(playerInventory);
        this.addDataSlots(data);
    }

    public CellTowerMenu(int containerId, Inventory playerInventory, CellTowerBlockEntity tower, ContainerData data) {
        super(IntertnetMod.CELL_TOWER_MENU.get(), containerId);
        this.container = tower;
        this.data = data;
        this.towerPos = tower.getBlockPos();
        setupSlots(playerInventory);
        this.addDataSlots(data);
    }

    private void setupSlots(Inventory playerInventory) {
        // Tower Fuel Slot at (26, 52)
        this.addSlot(new Slot(this.container, 0, 26, 52) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return CellTowerBlockEntity.getFuelSpec(stack, CellTowerMenu.this.isSatelliteConnector()) != null;
            }

            @Override
            public boolean isActive() {
                return CellTowerMenu.this.fuelSlotVisible;
            }
        });

        // Player Inventory (y = 128)
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 20 + col * 18, 128 + row * 18));
            }
        }

        // Player Hotbar (y = 186)
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 20 + col * 18, 186));
        }
    }

    public BlockPos getTowerPos() {
        return this.towerPos;
    }

    public void setFuelSlotVisible(boolean visible) {
        this.fuelSlotVisible = visible;
    }

    public int getFuelRemaining() {
        return this.data.get(0);
    }

    public int getFuelMax() {
        return this.data.get(1);
    }

    public int getActiveRange() {
        return this.data.get(2);
    }

    public boolean isFreeSimAvailable() {
        return this.data.get(3) != 0;
    }

    public boolean isSatelliteConnector() {
        return this.data.get(4) != 0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();
            if (index == 0) {
                if (!this.moveItemStackTo(itemstack1, 1, 37, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (CellTowerBlockEntity.getFuelSpec(itemstack1, this.isSatelliteConnector()) != null) {
                    if (!this.moveItemStackTo(itemstack1, 0, 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (index >= 1 && index < 28) {
                    if (!this.moveItemStackTo(itemstack1, 28, 37, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (index >= 28 && index < 37 && !this.moveItemStackTo(itemstack1, 1, 28, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (itemstack1.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (itemstack1.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, itemstack1);
        }
        return itemstack;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }
}
