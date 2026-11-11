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
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;

public class SatelliteConnectorBlock extends CellTowerBlock {
    static final int MAX_SKY_OBSTRUCTIONS = 6;

    static int countSkyObstructions(Level level, BlockPos pos) {
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
        BlockPos.MutableBlockPos above = pos.mutable();
        int obstructions = 0;
        for (int y = pos.getY() + 1; y < surface; y++) {
            above.setY(y);
            BlockState state = level.getBlockState(above);
            if (!state.is(IntertnetMod.SATELLITE_RELAY.get())
                    && (!state.getFluidState().isEmpty() || !state.getCollisionShape(level, above).isEmpty())
                    && ++obstructions > MAX_SKY_OBSTRUCTIONS) {
                break;
            }
        }
        return obstructions;
    }

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
        tooltipComponents.add(Component.literal("• Supports phone apps and SOS calls").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.literal("• Sky access: up to " + MAX_SKY_OBSTRUCTIONS + " overhead blocks").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.literal("• Fuel: Copper Ingots / Copper Blocks only").withStyle(ChatFormatting.GOLD));
        tooltipComponents.add(Component.literal("• Includes 1 Free SIM Card (extra SIMs cost Paper + Gold)").withStyle(ChatFormatting.GREEN));
    }
}
