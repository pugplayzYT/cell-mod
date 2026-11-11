package com.example.intertnet;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class CellTowerBlock extends BaseEntityBlock {
    public static final MapCodec<CellTowerBlock> CODEC = simpleCodec(CellTowerBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public CellTowerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CellTowerBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof CellTowerBlockEntity tower) {
                if (HotelAccess.isHotelTower(tower)) {
                    serverPlayer.displayClientMessage(Component.literal("Hotel Wi-Fi is available with a guest SIM."), true);
                } else if (tower.isPublicTower()) {
                    serverPlayer.displayClientMessage(Component.literal("Open World tower • High antenna • "
                            + tower.getConfiguredRange() + "m range. Get a SIM at the stall."), true);
                } else {
                    serverPlayer.openMenu(tower, buf -> buf.writeBlockPos(pos));
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level instanceof ServerLevel serverLevel && !(this instanceof SatelliteConnectorBlock)) {
                TowerChunkLoading.forget(serverLevel, pos);
            }
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof CellTowerBlockEntity tower) {
                Containers.dropContents(level, pos, tower);
                tower.setRemoved();
            }
            super.onRemove(state, level, pos, newState, movedByPiston);
        }
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return createTickerHelper(blockEntityType, IntertnetMod.CELL_TOWER_BE.get(), CellTowerBlockEntity::tick);
    }
}
