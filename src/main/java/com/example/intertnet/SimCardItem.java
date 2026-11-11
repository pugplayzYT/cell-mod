package com.example.intertnet;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SimCardItem extends Item {
    public SimCardItem(Properties properties) {
        super(properties);
    }

    @Nullable
    public static String getSimNetwork(ItemStack stack) {
        if (stack.isEmpty()) return null;
        String net = stack.get(IntertnetMod.SIM_NETWORK.get());
        return (net != null && !net.isBlank()) ? net : null;
    }

    public static ItemStack createForNetwork(String networkKey) {
        ItemStack stack = new ItemStack(IntertnetMod.SIM_CARD_ITEM.get());
        stack.set(IntertnetMod.SIM_NETWORK.get(), networkKey);
        return stack;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack simStack = player.getItemInHand(usedHand);
        String simNet = getSimNetwork(simStack);
        if (simNet == null) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.literal("This SIM Card is unprogrammed. Buy one at an Open World stall or print one at your own tower.")
                        .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResultHolder.fail(simStack);
        }

        ItemStack phoneStack = DeliveryManager.findPlayerPhoneStack(player);
        if (phoneStack.isEmpty()) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.literal("You need a Phone in your inventory to insert this SIM Card!")
                        .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResultHolder.fail(simStack);
        }

        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            String previousSim = PhoneItem.getInsertedSimNetwork(phoneStack);
            simStack.shrink(1);
            if (previousSim != null) {
                ItemStack oldSim = createForNetwork(previousSim);
                if (!sp.getInventory().add(oldSim)) {
                    sp.drop(oldSim, false);
                }
            }
            PhoneItem.setInsertedSimNetwork(phoneStack, simNet);
            level.playSound(null, sp.blockPosition(), SoundEvents.ARMOR_EQUIP_GOLD.value(), SoundSource.PLAYERS, 0.9f, 1.3f);
            sp.displayClientMessage(Component.literal("💳 Inserted SIM Card [" + HotelAccess.simLabel(simNet) + "] into your Phone!")
                    .withStyle(ChatFormatting.GREEN), true);
            DeliveryManager.syncToPlayer(sp);
        }
        return InteractionResultHolder.sidedSuccess(simStack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        String net = getSimNetwork(stack);
        if (net != null) {
            tooltipComponents.add(Component.literal("Bound Network: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(HotelAccess.simLabel(net)).withStyle(ChatFormatting.GOLD)));
        } else {
            tooltipComponents.add(Component.literal("Unprogrammed SIM Card").withStyle(ChatFormatting.RED));
        }
        tooltipComponents.add(Component.literal("Insert into a Phone's SIM slot (or right-click) to connect").withStyle(ChatFormatting.DARK_GRAY));
    }
}
