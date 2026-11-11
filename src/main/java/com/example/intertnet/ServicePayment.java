package com.example.intertnet;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class ServicePayment {
    static boolean takeEmeralds(ServerPlayer player, int price) {
        var inventory = player.getInventory();
        if (inventory.countItem(Items.EMERALD) < price) return false;
        for (int slot = 0; slot < inventory.getContainerSize() && price > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.is(Items.EMERALD)) continue;
            int paid = Math.min(price, stack.getCount());
            stack.shrink(paid);
            price -= paid;
        }
        inventory.setChanged();
        return true;
    }

    static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }
}
