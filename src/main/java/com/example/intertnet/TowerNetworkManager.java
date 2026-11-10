package com.example.intertnet;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public class TowerNetworkManager {
    public static final String PUBLIC_NETWORK = "Open World";

    public static List<CellTowerBlockEntity> getTowersInRadius(Level level, BlockPos sourceTowerPos) {
        List<CellTowerBlockEntity> all = SignalManager.getActiveTowerEntities(level);
        int radius = 256;
        BlockEntity srcBe = level.getBlockEntity(sourceTowerPos);
        if (srcBe instanceof CellTowerBlockEntity srcTower) {
            if (srcTower.isSatelliteConnector() || srcTower.isPublicTower()) {
                return List.of();
            }
            radius = Math.max(128, srcTower.getConfiguredRange());
        }
        double maxDistSqr = (double) radius * (double) radius;
        List<CellTowerBlockEntity> result = new ArrayList<>();
        for (CellTowerBlockEntity tower : all) {
            if (tower.isSatelliteConnector() || tower.isPublicTower()) continue;
            if (tower.getBlockPos().equals(sourceTowerPos)) continue;
            if (tower.getBlockPos().distSqr(sourceTowerPos) <= maxDistSqr) {
                result.add(tower);
            }
        }
        result.sort(Comparator.comparingDouble(t -> t.getBlockPos().distSqr(sourceTowerPos)));
        return result;
    }

    public static List<CellTowerBlockEntity> getConnectedNetworkTowers(Level level, BlockPos startTowerPos) {
        BlockEntity startBe = level.getBlockEntity(startTowerPos);
        if (startBe instanceof CellTowerBlockEntity startTower && (startTower.isSatelliteConnector() || startTower.isPublicTower())) {
            return List.of(startTower);
        }

        List<CellTowerBlockEntity> connectedTowers = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();

        BlockPos start = startTowerPos.immutable();
        visited.add(start);
        queue.add(start);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            if (!level.isLoaded(current)) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(current);
            if (be instanceof CellTowerBlockEntity tower && !tower.isRemoved() && !tower.isSatelliteConnector() && !tower.isPublicTower()) {
                connectedTowers.add(tower);
                for (BlockPos linkedPos : tower.getWirelessLinks()) {
                    if (!visited.contains(linkedPos) && level.isLoaded(linkedPos)) {
                        BlockEntity lbe = level.getBlockEntity(linkedPos);
                        if (lbe instanceof CellTowerBlockEntity peer && !peer.isSatelliteConnector() && !peer.isPublicTower()) {
                            visited.add(linkedPos.immutable());
                            queue.add(linkedPos.immutable());
                        }
                    }
                }
            }
        }

        if (connectedTowers.isEmpty() && startBe instanceof CellTowerBlockEntity tower) {
            connectedTowers.add(tower);
        }
        return connectedTowers;
    }

    public static String getNetworkId(Level level, BlockPos towerPos) {
        BlockEntity be = level.getBlockEntity(towerPos);
        if (be instanceof CellTowerBlockEntity tower && (tower.isSatelliteConnector() || tower.isPublicTower())) {
            return tower.getNetworkUid();
        }
        List<CellTowerBlockEntity> towers = getConnectedNetworkTowers(level, towerPos);
        BlockPos canonical = towers.stream()
                .map(BlockEntity::getBlockPos)
                .min(Comparator.comparingInt((BlockPos p) -> p.getX())
                        .thenComparingInt(p -> p.getZ())
                        .thenComparingInt(p -> p.getY()))
                .orElse(towerPos);
        int count = towers.size();
        return String.format("NET-%d_%d%s", canonical.getX(), canonical.getZ(), count > 1 ? " (" + count + " Linked)" : "");
    }

    public static String getCanonicalNetworkKey(Level level, BlockPos towerPos) {
        BlockEntity be = level.getBlockEntity(towerPos);
        if (be instanceof CellTowerBlockEntity tower && (tower.isSatelliteConnector() || tower.isPublicTower())) {
            return tower.getNetworkUid();
        }
        List<CellTowerBlockEntity> towers = getConnectedNetworkTowers(level, towerPos);
        BlockPos canonical = towers.stream()
                .map(BlockEntity::getBlockPos)
                .min(Comparator.comparingInt((BlockPos p) -> p.getX())
                        .thenComparingInt(p -> p.getZ())
                        .thenComparingInt(p -> p.getY()))
                .orElse(towerPos);
        return "NET-" + canonical.getX() + "_" + canonical.getZ();
    }

    public static void syncNetworkUidAcrossChain(Level level, CellTowerBlockEntity sourceTower) {
        if (sourceTower.isSatelliteConnector() || sourceTower.isPublicTower()) return;
        List<CellTowerBlockEntity> linked = getConnectedNetworkTowers(level, sourceTower.getBlockPos());
        String canonicalUid = getCanonicalNetworkKey(level, sourceTower.getBlockPos());
        for (CellTowerBlockEntity peer : linked) {
            peer.setNetworkUid(canonicalUid);
        }
    }

    public static boolean doesTowerMatchSim(Level level, CellTowerBlockEntity tower, @Nullable String simNetwork) {
        if (simNetwork == null || simNetwork.isBlank()) {
            return false;
        }
        String cleanSim = simNetwork.trim();
        if (tower.isPublicTower()) {
            return PUBLIC_NETWORK.equalsIgnoreCase(cleanSim);
        }
        if (cleanSim.equalsIgnoreCase(tower.getNetworkUid())) {
            return true;
        }
        if (tower.isSatelliteConnector()) {
            return false;
        }
        if (cleanSim.equalsIgnoreCase(getCanonicalNetworkKey(level, tower.getBlockPos()))) {
            return true;
        }
        for (CellTowerBlockEntity peer : getConnectedNetworkTowers(level, tower.getBlockPos())) {
            String peerPosKey = "NET-" + peer.getBlockPos().getX() + "_" + peer.getBlockPos().getZ();
            if (cleanSim.equalsIgnoreCase(peer.getNetworkUid()) || cleanSim.equalsIgnoreCase(peerPosKey)) {
                return true;
            }
        }
        return false;
    }
}
