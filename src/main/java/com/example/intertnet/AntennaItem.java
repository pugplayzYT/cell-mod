package com.example.intertnet;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

final class AntennaItem extends Item {
    private final int rangeBonus;

    AntennaItem(int rangeBonus) {
        super(new Properties().stacksTo(1));
        this.rangeBonus = rangeBonus;
    }

    int rangeBonus() {
        return rangeBonus;
    }

    static int rangeBonus(ItemStack stack) {
        return stack.getItem() instanceof AntennaItem antenna ? antenna.rangeBonus() : 0;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.intertnet.antenna.range", rangeBonus).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.intertnet.antenna.install").withStyle(ChatFormatting.GRAY));
    }
}
