package com.example.intertnet;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

final class PersonalSatelliteAntennaItem extends Item {
    PersonalSatelliteAntennaItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack antenna = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            attach(serverPlayer, DeliveryManager.findPlayerPhoneStack(player), antenna);
        }
        return InteractionResultHolder.sidedSuccess(antenna, level.isClientSide());
    }

    static void toggle(ServerPlayer player) {
        ItemStack phone = DeliveryManager.findPlayerPhoneStack(player);
        if (phone.isEmpty()) return;
        if (PhoneItem.hasSatelliteAntenna(phone)) {
            phone.remove(IntertnetMod.PHONE_SATELLITE_ANTENNA.get());
            ItemStack antenna = new ItemStack(IntertnetMod.PERSONAL_SATELLITE_ANTENNA.get());
            if (!player.getInventory().add(antenna)) player.drop(antenna, false);
            player.displayClientMessage(Component.literal("Satellite antenna removed."), true);
            DeliveryManager.syncToPlayer(player);
            return;
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack antenna = player.getInventory().getItem(slot);
            if (antenna.is(IntertnetMod.PERSONAL_SATELLITE_ANTENNA.get())) {
                attach(player, phone, antenna);
                return;
            }
        }
        player.displayClientMessage(Component.literal("Buy a satellite antenna at an Open World tower stall."), true);
    }

    private static void attach(ServerPlayer player, ItemStack phone, ItemStack antenna) {
        if (phone.isEmpty() || PhoneItem.hasSatelliteAntenna(phone)) {
            player.displayClientMessage(Component.literal(phone.isEmpty() ? "You need a phone to install this antenna."
                    : "This phone already has a satellite antenna."), true);
            return;
        }
        antenna.shrink(1);
        phone.set(IntertnetMod.PHONE_SATELLITE_ANTENNA.get(), true);
        player.displayClientMessage(Component.literal("Satellite antenna installed. Global Satellite is ready."), true);
        DeliveryManager.syncToPlayer(player);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Global Satellite SIM included").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("Right-click to attach to your phone").withStyle(ChatFormatting.GRAY));
    }
}
