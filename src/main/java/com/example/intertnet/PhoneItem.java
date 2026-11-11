package com.example.intertnet;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PhoneItem extends Item {
    public PhoneItem(Properties properties) {
        super(properties);
    }

    @Nullable
    public static String getInsertedSimNetwork(ItemStack stack) {
        if (stack.isEmpty()) return null;
        String net = stack.get(IntertnetMod.SIM_NETWORK.get());
        return (net != null && !net.isBlank()) ? net : null;
    }

    public static void setInsertedSimNetwork(ItemStack stack, @Nullable String simNetwork) {
        if (stack.isEmpty()) return;
        if (simNetwork == null || simNetwork.isBlank()) {
            stack.remove(IntertnetMod.SIM_NETWORK.get());
        } else {
            stack.set(IntertnetMod.SIM_NETWORK.get(), simNetwork);
        }
    }

    static boolean hasSatelliteAntenna(ItemStack stack) {
        return stack.getOrDefault(IntertnetMod.PHONE_SATELLITE_ANTENNA.get(), false);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (level.isClientSide) {
            ClientHooks.openPhoneScreen();
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        String simNet = getInsertedSimNetwork(stack);
        if (simNet != null) {
            tooltipComponents.add(Component.literal("SIM Card: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("Inserted (" + HotelAccess.simLabel(simNet) + ")").withStyle(ChatFormatting.GREEN)));
        } else {
            tooltipComponents.add(Component.literal("SIM Card: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("None (Insert a Tower SIM)").withStyle(ChatFormatting.RED)));
        }
        if (hasSatelliteAntenna(stack)) {
            tooltipComponents.add(Component.literal("Global Satellite antenna installed").withStyle(ChatFormatting.AQUA));
        }
        tooltipComponents.add(Component.literal("Right-click to open Intertnet OS").withStyle(ChatFormatting.AQUA));
    }
}
