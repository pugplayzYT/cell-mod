package com.example.intertnet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class TowerChunkLoading extends SavedData {
    private static final int PLAYER_RANGE = 1000;
    private static final TicketType<ChunkPos> TICKET =
            TicketType.create("intertnet_cell_tower", Comparator.comparingLong(ChunkPos::toLong));

    private final Map<ChunkPos, Set<BlockPos>> towers = new HashMap<>();
    private final Set<ChunkPos> ticketed = new HashSet<>();

    private static TowerChunkLoading get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(TowerChunkLoading::new, TowerChunkLoading::load),
                "intertnet_tower_chunks");
    }

    private static TowerChunkLoading load(CompoundTag tag, HolderLookup.Provider registries) {
        TowerChunkLoading data = new TowerChunkLoading();
        for (long packed : tag.getLongArray("Towers")) {
            BlockPos pos = BlockPos.of(packed);
            data.towers.computeIfAbsent(new ChunkPos(pos), ignored -> new HashSet<>()).add(pos);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLongArray("Towers", towers.values().stream().flatMap(Set::stream).mapToLong(BlockPos::asLong).toArray());
        return tag;
    }

    static void remember(ServerLevel level, BlockPos pos) {
        TowerChunkLoading data = get(level);
        if (data.towers.computeIfAbsent(new ChunkPos(pos), ignored -> new HashSet<>()).add(pos.immutable())) {
            data.setDirty();
        }
    }

    static void forget(ServerLevel level, BlockPos pos) {
        TowerChunkLoading data = get(level);
        ChunkPos chunk = new ChunkPos(pos);
        Set<BlockPos> positions = data.towers.get(chunk);
        if (positions != null && positions.remove(pos)) {
            data.setDirty();
            if (positions.isEmpty()) {
                data.towers.remove(chunk);
                if (data.ticketed.remove(chunk)) {
                    level.getChunkSource().removeRegionTicket(TICKET, chunk, 0, chunk);
                }
            }
        }
    }

    static void tick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            TowerChunkLoading data = get(level);
            if (level.getGameTime() % 20 == 0) {
                data.updateTickets(level);
            }
            data.tickRemoteTowers(level);
        }
    }

    private void updateTickets(ServerLevel level) {
        for (ChunkPos pos : ticketed) {
            var loaded = level.getChunkSource().getChunkNow(pos.x, pos.z);
            if (loaded == null) continue;
            Set<BlockPos> positions = towers.get(pos);
            if (positions.removeIf(towerPos -> !loaded.getBlockState(towerPos).is(IntertnetMod.CELL_TOWER_BLOCK.get()))) {
                setDirty();
                if (positions.isEmpty()) towers.remove(pos);
            }
        }

        Set<ChunkPos> wanted = new HashSet<>();
        for (var player : level.players()) {
            if (player.isSpectator() || !player.isAlive()) continue;
            double x = player.getX();
            double z = player.getZ();
            for (var entry : towers.entrySet()) {
                ChunkPos chunk = entry.getKey();
                if (Math.abs(chunk.getMiddleBlockX() - x) > PLAYER_RANGE + 8
                        || Math.abs(chunk.getMiddleBlockZ() - z) > PLAYER_RANGE + 8 || wanted.contains(chunk)) {
                    continue;
                }
                for (BlockPos pos : entry.getValue()) {
                    double dx = pos.getX() + 0.5 - x;
                    double dz = pos.getZ() + 0.5 - z;
                    if (dx * dx + dz * dz <= PLAYER_RANGE * PLAYER_RANGE) {
                        wanted.add(chunk);
                        break;
                    }
                }
            }
        }
        for (ChunkPos chunk : wanted) {
            if (ticketed.add(chunk)) {
                level.getChunkSource().addRegionTicket(TICKET, chunk, 0, chunk);
            }
        }
        ticketed.removeIf(chunk -> {
            if (wanted.contains(chunk)) return false;
            level.getChunkSource().removeRegionTicket(TICKET, chunk, 0, chunk);
            return true;
        });
    }

    private void tickRemoteTowers(ServerLevel level) {
        for (ChunkPos pos : ticketed) {
            var chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
            if (chunk == null || (chunk.getFullStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING)
                    && level.areEntitiesLoaded(pos.toLong()))) {
                continue;
            }
            // A radius-zero ticket loads the block entity without ticking the surrounding world.
            for (BlockPos towerPos : towers.get(pos)) {
                if (chunk.getBlockEntity(towerPos) instanceof CellTowerBlockEntity tower
                        && !tower.isRemoved() && !tower.isPublicTower() && !tower.isSatelliteConnector()
                        && level.getWorldBorder().isWithinBounds(towerPos)) {
                    CellTowerBlockEntity.tick(level, towerPos, tower.getBlockState(), tower);
                }
            }
        }
    }
}
