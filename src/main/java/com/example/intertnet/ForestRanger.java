package com.example.intertnet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

final class ForestRanger extends Pillager {
    private static final int[] ROUTE_TURNS = {0, 35, -35, 70, -70, 105, -105};
    private static final BlockPos[] PATROL = {new BlockPos(5, 1, 5), new BlockPos(11, 1, 5),
            new BlockPos(11, 1, 11), new BlockPos(5, 1, 11)};
    @Nullable
    private BlockPos lookout;
    @Nullable
    private BlockPos route;
    private boolean assigned;
    private boolean restoreOnLoad;
    private int restTicks = 160;
    private int patrolPoint;
    private int shotCooldown;

    ForestRanger(EntityType<? extends Pillager> type, Level level) {
        super(type, level);
        setCustomName(Component.literal("Forest Ranger"));
        setPersistenceRequired();
        setCanJoinRaid(false);
        setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CROSSBOW));
        setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    void setLookout(BlockPos pos) {
        lookout = pos.immutable();
    }

    boolean serves(BlockPos pos) {
        return pos.equals(lookout) && !assigned;
    }

    void setAssigned(boolean value) {
        assigned = value;
        stopWalking();
        if (!value) restTicks = 240;
    }

    @Override
    public void die(DamageSource source) {
        if (lookout != null && level() instanceof ServerLevel level) {
            RangerDispatch.recordLoss(level, lookout);
        }
        super.die(source);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (lookout != null) tag.putLong("Lookout", lookout.asLong());
        tag.putBoolean("ReturnToLookout", assigned || restoreOnLoad);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        lookout = tag.contains("Lookout") ? BlockPos.of(tag.getLong("Lookout")) : null;
        restoreOnLoad = tag.getBoolean("ReturnToLookout");
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level) || !isAlive() || lookout == null) return;
        if (restoreOnLoad) {
            restoreOnLoad = false;
            returnToPost();
            return;
        }
        defend();
        if (!assigned) patrol(level);
    }

    void returnToPost() {
        if (!(level() instanceof ServerLevel level) || lookout == null || !isAlive()) return;
        BlockPos shelter = lookout.offset(RangerLookoutPiece.SHELTER);
        level.getChunkAt(shelter);
        BlockPos stand = canStand(level, shelter) ? shelter : lookout.offset(PATROL[0]);
        if (canStand(level, stand)) {
            teleportTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
        }
        setAssigned(false);
    }

    private void patrol(ServerLevel level) {
        if (restTicks > 0) {
            restTicks--;
            return;
        }
        BlockPos foot = lookout.offset(RangerLookoutPiece.FOOT);
        if (getY() > foot.getY() + 1.5) {
            if (hasLadder(level, lookout)) climb(lookout, false);
            return;
        }
        BlockPos patrol = lookout.offset(PATROL[patrolPoint]);
        if (distanceToSqr(Vec3.atBottomCenterOf(patrol)) < 1.5) {
            patrolPoint = (patrolPoint + 1) % PATROL.length;
            restTicks = 45;
            stopWalking();
        } else {
            walkTo(patrol, 0.75);
        }
    }

    static boolean hasLadder(ServerLevel level, BlockPos lookout) {
        BlockPos foot = lookout.offset(RangerLookoutPiece.FOOT);
        int height = RangerLookoutPiece.LANDING.getY() - RangerLookoutPiece.FOOT.getY();
        for (int y = 0; y <= height; y++) {
            if (!level.getBlockState(foot.above(y)).is(Blocks.LADDER)) return false;
        }
        return true;
    }

    boolean climb(BlockPos lookout, boolean up) {
        BlockPos foot = lookout.offset(RangerLookoutPiece.FOOT);
        BlockPos landing = lookout.offset(RangerLookoutPiece.LANDING);
        BlockPos target = up ? landing : foot;
        Vec3 center = Vec3.atBottomCenterOf(target);
        double dx = center.x - getX();
        double dz = center.z - getZ();
        double horizontal = Math.hypot(dx, dz);
        if (horizontal > 1.15) {
            walkTo(up ? foot : landing, 1.05);
            return false;
        }
        stopWalking();
        if (horizontal > 0.09) {
            move(MoverType.SELF, new Vec3(dx, 0, dz).scale(Math.min(1, 0.15 / horizontal)));
            return false;
        }
        double dy = center.y - getY();
        if (up ? dy <= 0.01 : dy >= -0.10) return true;
        move(MoverType.SELF, new Vec3(0, Math.copySign(Math.min(0.26, Math.abs(dy)), dy), 0));
        setDeltaMovement(Vec3.ZERO);
        resetFallDistance();
        return false;
    }

    boolean enterShelter(BlockPos lookout) {
        Vec3 destination = Vec3.atBottomCenterOf(lookout.offset(RangerLookoutPiece.SHELTER));
        if (getY() < destination.y) {
            move(MoverType.SELF, new Vec3(0, Math.min(0.26, destination.y - getY()), 0));
        }
        Vec3 delta = destination.subtract(position()).multiply(1, 0, 1);
        if (delta.lengthSqr() < 0.20) return true;
        move(MoverType.SELF, delta.scale(Math.min(1, 0.15 / delta.length())));
        setDeltaMovement(Vec3.ZERO);
        return false;
    }

    void stopWalking() {
        getNavigation().stop();
        route = null;
    }

    void walkTo(BlockPos target, double speed) {
        if (!(level() instanceof ServerLevel level)) return;
        if (target.equals(route) && (!getNavigation().isDone() || tickCount % 10 != 0)) return;
        route = target;
        double dx = target.getX() + 0.5 - getX();
        double dz = target.getZ() + 0.5 - getZ();
        double distance = Math.hypot(dx, dz);
        if (distance < 24 && level.isLoaded(target) && followPath(target, speed)) return;

        double bearing = Math.atan2(dz, dx);
        for (int turn : ROUTE_TURNS) {
            double angle = bearing + Math.toRadians(turn);
            int step = distance < 10 ? 5 : 12;
            BlockPos column = BlockPos.containing(getX() + Math.cos(angle) * step, getY(),
                    getZ() + Math.sin(angle) * step);
            if (!level.isLoaded(column)) continue;
            for (int offset = 0; offset <= 6; offset++) {
                BlockPos above = column.above(offset);
                BlockPos below = column.below(offset);
                if ((canStand(level, above) && followPath(above, speed))
                        || (offset != 0 && canStand(level, below) && followPath(below, speed))) return;
            }
        }
    }

    private boolean followPath(BlockPos target, double speed) {
        Path path = getNavigation().createPath(target, 0);
        return path != null && path.canReach() && getNavigation().moveTo(path, speed);
    }

    static boolean canStand(ServerLevel level, BlockPos pos) {
        return level.getWorldBorder().isWithinBounds(pos) && level.isLoaded(pos)
                && level.getFluidState(pos).isEmpty()
                && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    private void defend() {
        if (shotCooldown > 0) shotCooldown--;
        if (tickCount % 8 != 0) return;
        LivingEntity target = null;
        double closest = 12 * 12;
        for (LivingEntity candidate : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(12),
                mob -> mob.isAlive() && EmergencyDispatch.isThreat(mob))) {
            double distance = distanceToSqr(candidate);
            if (distance < closest && hasLineOfSight(candidate)) {
                target = candidate;
                closest = distance;
            }
        }
        setChargingCrossbow(target != null && shotCooldown <= 24);
        if (target == null) return;
        getLookControl().setLookAt(target, 30, 30);
        if (shotCooldown == 0) {
            RangerArrow arrow = new RangerArrow(this);
            double dx = target.getX() - arrow.getX();
            double dz = target.getZ() - arrow.getZ();
            double dy = target.getY(0.45) - arrow.getY() + Math.hypot(dx, dz) * 0.14;
            arrow.shoot(dx, dy, dz, 1.6f, 2.0f);
            if (level().addFreshEntity(arrow)) playSound(SoundEvents.CROSSBOW_SHOOT, 1, 1);
            shotCooldown = 48;
        }
    }

    private static final class RangerArrow extends Arrow {
        RangerArrow(ForestRanger ranger) {
            super(ranger.level(), ranger, poisonTip(), null);
            pickup = AbstractArrow.Pickup.DISALLOWED;
        }

        private static ItemStack poisonTip() {
            ItemStack tip = new ItemStack(Items.TIPPED_ARROW);
            tip.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.POISON));
            return tip;
        }

        @Override
        protected boolean canHitEntity(Entity entity) {
            return entity instanceof LivingEntity mob && EmergencyDispatch.isThreat(mob) && super.canHitEntity(entity);
        }

        @Override
        protected void onHitBlock(BlockHitResult hit) {
            discard();
        }

        @Override
        public boolean shouldBeSaved() {
            return false;
        }
    }
}
