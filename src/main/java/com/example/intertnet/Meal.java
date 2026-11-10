package com.example.intertnet;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

enum Meal {
    STEAK("Steak", Items.BEEF, Items.COOKED_BEEF, 3),
    PORK("Porkchop", Items.PORKCHOP, Items.COOKED_PORKCHOP, 2),
    CHICKEN("Chicken", Items.CHICKEN, Items.COOKED_CHICKEN, 2);

    final String label;
    final Item raw;
    final Item cooked;
    final int price;

    Meal(String label, Item raw, Item cooked, int price) {
        this.label = label;
        this.raw = raw;
        this.cooked = cooked;
        this.price = price;
    }
}
