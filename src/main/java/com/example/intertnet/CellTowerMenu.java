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

import static com.example.intertnet.CellTowerBlockEntity.ANTENNA_SLOT;
import static com.example.intertnet.CellTowerBlockEntity.FUEL_SLOT;
import static com.example.intertnet.CellTowerBlockEntity.SLOT_COUNT;

public class CellTowerMenu extends AbstractContainerMenu {
    private static final int HOTBAR_START = SLOT_COUNT + 27;
    private static final int INVENTORY_END = HOTBAR_START + 9;
    static final int ANTENNA_X = 26;
    static final int ANTENNA_Y = 69;
    private final Container container;
    private final ContainerData data;
    private final BlockPos towerPos;
    private boolean equipmentSlotsVisible = true;

    public CellTowerMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory, new SimpleContainer(SLOT_COUNT), new SimpleContainerData(5), extraData);
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
        this.addSlot(new Slot(this.container, FUEL_SLOT, 26, 52) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return CellTowerBlockEntity.getFuelSpec(stack, CellTowerMenu.this.isSatelliteConnector()) != null;
            }

            @Override
            public boolean isActive() {
                return CellTowerMenu.this.equipmentSlotsVisible;
            }
        });
        this.addSlot(new Slot(this.container, ANTENNA_SLOT, ANTENNA_X, ANTENNA_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return !CellTowerMenu.this.isSatelliteConnector() && stack.getItem() instanceof AntennaItem;
            }

            @Override
            public boolean isActive() {
                return CellTowerMenu.this.equipmentSlotsVisible && !CellTowerMenu.this.isSatelliteConnector();
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

    public void setEquipmentSlotsVisible(boolean visible) {
        this.equipmentSlotsVisible = visible;
    }

    int getAntennaBonus() {
        return AntennaItem.rangeBonus(this.getSlot(ANTENNA_SLOT).getItem());
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
            if (index < SLOT_COUNT) {
                if (!slot.isActive() || !this.moveItemStackTo(itemstack1, SLOT_COUNT, INVENTORY_END, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                int equipmentSlot = itemstack1.getItem() instanceof AntennaItem ? ANTENNA_SLOT : FUEL_SLOT;
                Slot target = this.getSlot(equipmentSlot);
                if (target.isActive() && target.mayPlace(itemstack1)) {
                    if (!this.moveItemStackTo(itemstack1, equipmentSlot, equipmentSlot + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (index < HOTBAR_START) {
                    if (!this.moveItemStackTo(itemstack1, HOTBAR_START, INVENTORY_END, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!this.moveItemStackTo(itemstack1, SLOT_COUNT, HOTBAR_START, false)) {
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
