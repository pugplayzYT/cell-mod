package com.example.intertnet;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class SignalManager {
    public static final double OCCLUSION_PENALTY_BLOCKS = 4.0;

    public record TowerSignalEval(
            BlockPos towerPos,
            CellTowerBlockEntity tower,
            int bars,
            double rawDistance,
            double effectiveDistance,
            int maxRange,
            int occludingBlocks,
            List<BlockPos> occludedPositions,
            boolean powered,
            boolean simMatches
    ) {}

    public record SignalStatus(
            int bars,
            @Nullable BlockPos connectedTowerPos,
            double rawDistance,
            double effectiveDistance,
            int maxRange,
            int occludingBlocks,
            boolean missingOrWrongSim,
            boolean unpoweredTowerNearby,
            boolean satelliteSkyBlockedNearby,
            boolean connectedViaSatellite
    ) {
        public int getDeliveryTicks() {
            return switch (bars) {
                case 4 -> 20;   // 1.0s
                case 3 -> 55;   // 2.75s
                case 2 -> 105;  // 5.25s
                case 1 -> 180;  // 9.0s
                default -> -1;
            };
        }

        public int getSignalDbm() {
            if (bars <= 0) return -120;
            double ratio = maxRange > 0 ? Mth.clamp(1.0 - (effectiveDistance / maxRange), 0.0, 1.0) : 0.0;
            return (int) Math.round(-108 + (ratio * 58)); // -108 dBm to -50 dBm
        }
    }

    public static List<BlockPos> traceOccludingBlocks(Level level, Vec3 from, Vec3 to, BlockPos ignoreTowerPos) {
        List<BlockPos> occluded = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        double dist = from.distanceTo(to);
        if (dist < 0.5) {
            return occluded;
        }
        int steps = Math.min(1024, (int) Math.ceil(dist * 3.0));
        BlockPos playerHead = BlockPos.containing(from);
        for (int i = 1; i < steps; i++) {
            double t = (double) i / (double) steps;
            Vec3 sample = from.lerp(to, t);
            BlockPos bpos = BlockPos.containing(sample);
            if (bpos.equals(ignoreTowerPos) || bpos.equals(playerHead) || !visited.add(bpos)) {
                continue;
            }
            if (!level.isLoaded(bpos)) {
                continue;
            }
            BlockState state = level.getBlockState(bpos);
            if (!state.isAir() && (state.isSolidRender(level, bpos) || !state.getCollisionShape(level, bpos).isEmpty())) {
                occluded.add(bpos.immutable());
            }
        }
        return occluded;
    }

    public static TowerSignalEval evaluateTower(Level level, Vec3 playerEyePos, CellTowerBlockEntity tower, @Nullable String simNetwork) {
        BlockPos tPos = tower.getBlockPos();
        Vec3 towerCenter = Vec3.atCenterOf(tPos);
        double rawDist = playerEyePos.distanceTo(towerCenter);
        boolean powered = tower.isPowered();
        int maxRange = powered ? tower.getActiveRange() : tower.getConfiguredRange();
        boolean simMatches = TowerNetworkManager.doesTowerMatchSim(level, tower, simNetwork);

        if (rawDist > maxRange + 32) {
            return new TowerSignalEval(tPos, tower, 0, rawDist, rawDist, maxRange, 0, List.of(), powered, simMatches);
        }

        List<BlockPos> occluded = traceOccludingBlocks(level, playerEyePos, towerCenter, tPos);
        int occludingCount = occluded.size();
        double effectiveDist = rawDist + (occludingCount * OCCLUSION_PENALTY_BLOCKS);

        int bars = 0;
        if (powered && simMatches && effectiveDist <= maxRange && maxRange > 0) {
            double ratio = 1.0 - (effectiveDist / (double) maxRange);
            if (ratio >= 0.75) {
                bars = 4;
            } else if (ratio >= 0.50) {
                bars = 3;
            } else if (ratio >= 0.25) {
                bars = 2;
            } else if (ratio > 0.0) {
                bars = 1;
            }
        }

        return new TowerSignalEval(tPos, tower, bars, rawDist, effectiveDist, maxRange, occludingCount, occluded, powered, simMatches);
    }

    public static List<CellTowerBlockEntity> getActiveTowerEntities(Level level) {
        Set<BlockPos> tracked = CellTowerBlockEntity.getTrackedTowers(level);
        List<CellTowerBlockEntity> result = new ArrayList<>();
        Iterator<BlockPos> it = tracked.iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof CellTowerBlockEntity tower && !tower.isRemoved()) {
                result.add(tower);
            } else {
                it.remove();
            }
        }
        return result;
    }

    public static SignalStatus getBestSignal(Player player, @Nullable String simNetwork) {
        Level level = player.level();
        Vec3 eyePos = player.getEyePosition();
        List<CellTowerBlockEntity> towers = getActiveTowerEntities(level);

        TowerSignalEval best = null;
        boolean missingOrWrongSim = false;
        boolean unpoweredNearby = false;
        boolean skyBlockedNearby = false;

        for (CellTowerBlockEntity tower : towers) {
            TowerSignalEval eval = evaluateTower(level, eyePos, tower, simNetwork);
            if (!eval.powered() && eval.rawDistance() <= eval.maxRange()) {
                if (tower.isSatelliteConnector() && !tower.hasSatelliteSkyAccess()) {
                    skyBlockedNearby |= eval.simMatches();
                } else {
                    unpoweredNearby = true;
                }
            }
            if (eval.powered() && !eval.simMatches() && eval.effectiveDistance() <= eval.maxRange()) {
                missingOrWrongSim = true;
            }
            if (eval.bars() > 0) {
                if (best == null || eval.bars() > best.bars()
                        || (eval.bars() == best.bars() && eval.effectiveDistance() < best.effectiveDistance())) {
                    best = eval;
                }
            }
        }

        if (best != null) {
            return new SignalStatus(
                    best.bars(),
                    best.towerPos(),
                    best.rawDistance(),
                    best.effectiveDistance(),
                    best.maxRange(),
                    best.occludingBlocks(),
                    false,
                    false,
                    false,
                    best.tower().isSatelliteConnector()
            );
        }

        return new SignalStatus(0, null, 0.0, 0.0, 0, 0, missingOrWrongSim, unpoweredNearby, skyBlockedNearby, false);
    }
}
