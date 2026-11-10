package com.example.intertnet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

final class TowerGuards {
    static final String GUARD = "intertnet_tower_guard";
    private static final String HOME = "GuardTower";
    private static final String INTRUDER = "Intruder";
    private static final String ALARM_UNTIL = "AlarmUntil";

    static void register() {
        NeoForge.EVENT_BUS.addListener(TowerGuards::patrol);
        NeoForge.EVENT_BUS.addListener(TowerGuards::preventSunDamage);
        NeoForge.EVENT_BUS.addListener(TowerGuards::onMining);
        NeoForge.EVENT_BUS.addListener(TowerGuards::onBreak);
    }

    static boolean deploy(ServerLevel level, BlockPos tower) {
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            return false;
        }
        boolean deployed = false;
        for (int offset : new int[]{-4, 4}) {
            Phantom guard = EntityType.PHANTOM.create(level);
            if (guard != null) {
                guard.moveTo(tower.getX() + 0.5 + offset, tower.getY() + 3.5, tower.getZ() + 0.5, 0, 0);
                guard.setNoAi(true);
                guard.setNoGravity(true);
                guard.setPersistenceRequired();
                guard.setCustomName(Component.literal("Open World Patrol"));
                guard.addTag(GUARD);
                guard.getPersistentData().putLong(HOME, tower.asLong());
                deployed |= level.addFreshEntity(guard);
            }
        }
        return deployed;
    }

    private static void preventSunDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof Phantom phantom && phantom.getTags().contains(GUARD)
                && event.getSource().is(DamageTypeTags.IS_FIRE)) {
            event.setCanceled(true);
        }
    }

    private static void patrol(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Phantom guard) || !(guard.level() instanceof ServerLevel level)
                || !guard.getTags().contains(GUARD) || !guard.isAlive()) {
            return;
        }
        guard.clearFire();
        var data = guard.getPersistentData();
        BlockPos home = BlockPos.of(data.getLong(HOME));
        if (level.isLoaded(home) && !(level.getBlockEntity(home) instanceof CellTowerBlockEntity tower && tower.isPublicTower())) {
            guard.discard();
            return;
        }
        Vec3 center = Vec3.atCenterOf(home);
        double angle = level.getGameTime() * 0.05 + guard.getId();
        Vec3 target = center.add(Math.cos(angle) * 7, 3.5 + Math.sin(angle) * 1.4, Math.sin(angle) * 7);
        double speed = 0.26;
        if (data.hasUUID(INTRUDER)) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(data.getUUID(INTRUDER));
            if (player != null && player.serverLevel() == level && player.isAlive() && !player.isCreative() && !player.isSpectator()
                    && player.distanceToSqr(center) < 28 * 28 && level.getGameTime() < data.getLong(ALARM_UNTIL)) {
                Vec3 approach = player.getEyePosition().add(0, 0.5, 0);
                if (guard.distanceToSqr(approach) > 2.4 * 2.4 || guard.hasLineOfSight(player)) {
                    target = approach;
                    speed = 0.5;
                }
                if (guard.distanceToSqr(approach) < 2.4 * 2.4 && guard.hasLineOfSight(player)
                        && guard.tickCount % 20 == 0) {
                    if (player.hurt(level.damageSources().mobAttack(guard), 4)) {
                        player.knockback(0.65, guard.getX() - player.getX(), guard.getZ() - player.getZ());
                    }
                }
            } else {
                data.remove(INTRUDER);
            }
        }
        Vec3 step = target.subtract(guard.position());
        Vec3 movement = step.lengthSqr() > speed * speed ? step.normalize().scale(speed) : step;
        guard.move(MoverType.SELF, movement);
        guard.setDeltaMovement(Vec3.ZERO);
        if (movement.horizontalDistanceSqr() > 0.0001) {
            float yaw = (float) (Math.atan2(-movement.x, movement.z) * 180 / Math.PI);
            guard.setYRot(yaw);
            guard.yBodyRot = yaw;
        }
    }

    private static void onMining(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START && event.getEntity() instanceof ServerPlayer player) {
            alert(player, event.getPos());
        }
    }

    private static void onBreak(BlockEvent.BreakEvent event) {
        if (!event.isCanceled() && event.getPlayer() instanceof ServerPlayer player && alert(player, event.getPos())) {
            event.setCanceled(true);
        }
    }

    private static boolean alert(ServerPlayer player, BlockPos damaged) {
        if (player.isCreative() || player.isSpectator()) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        // Restrict protection to the mast itself so nearby terrain and shop fixtures can still be edited.
        for (BlockPos home : CellTowerBlockEntity.getTrackedTowers(level)) {
            int height = home.getY() - damaged.getY();
            if (Math.abs(home.getX() - damaged.getX()) > 2 || Math.abs(home.getZ() - damaged.getZ()) > 2
                    || height < -2 || height > 8 || !level.isLoaded(home)
                    || !(level.getBlockEntity(home) instanceof CellTowerBlockEntity tower && tower.isPublicTower())) {
                continue;
            }
            var state = level.getBlockState(damaged);
            if (!damaged.equals(home) && !state.is(Blocks.POLISHED_DEEPSLATE)
                    && !state.is(Blocks.WAXED_CUT_COPPER)
                    && !state.is(Blocks.CHISELED_STONE_BRICKS)
                    && !state.is(Blocks.LIGHTNING_ROD)
                    && !state.is(Blocks.END_ROD)
                    && !state.is(Blocks.IRON_BARS)) {
                continue;
            }
            var guards = level.getEntities(EntityType.PHANTOM, new AABB(home).inflate(29), guard -> guard.isAlive()
                    && guard.getTags().contains(GUARD) && guard.getPersistentData().getLong(HOME) == home.asLong());
            if (guards.isEmpty()) {
                continue;
            }
            boolean newAlarm = false;
            for (Phantom guard : guards) {
                var data = guard.getPersistentData();
                if (!data.hasUUID(INTRUDER) || !data.getUUID(INTRUDER).equals(player.getUUID())
                        || data.getLong(ALARM_UNTIL) <= level.getGameTime()) {
                    newAlarm = true;
                }
                data.putUUID(INTRUDER, player.getUUID());
                data.putLong(ALARM_UNTIL, level.getGameTime() + 160);
            }
            if (newAlarm) {
                player.displayClientMessage(Component.literal("Open World patrol alerted. Step away from the tower."), true);
            }
            return true;
        }
        return false;
    }
}
