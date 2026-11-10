package com.example.intertnet;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class SatelliteConnectorBlock extends CellTowerBlock {

    public SatelliteConnectorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof CellTowerBlockEntity tower) {
            if (!level.isClientSide && !player.isCreative()) {
                ItemStack stack = new ItemStack(IntertnetMod.SATELLITE_CONNECTOR_ITEM.get());
                tower.saveToItem(stack, level.registryAccess());
                tower.clearContent();
                ItemEntity itemEntity = new ItemEntity(
                        level,
                        pos.getX() + 0.5,
                        pos.getY() + 0.5,
                        pos.getZ() + 0.5,
                        stack
                );
                itemEntity.setDefaultPickUpDelay();
                level.addFreshEntity(itemEntity);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.literal("Portable 64m Internet on the Go").withStyle(ChatFormatting.AQUA));
        tooltipComponents.add(Component.literal("• Supports: Global Messaging (No Emergency Rescue)").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.literal("• Fuel: Copper Ingots / Copper Blocks only").withStyle(ChatFormatting.GOLD));
        tooltipComponents.add(Component.literal("• Includes 1 Free SIM Card (extra SIMs cost Paper + Gold)").withStyle(ChatFormatting.GREEN));
    }
}
