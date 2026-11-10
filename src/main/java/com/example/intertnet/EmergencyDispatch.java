package com.example.intertnet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class EmergencyDispatch {
    private static final int STATION_RANGE = 2048;
    private static final int MAX_RESPONSES = 8;
    private static final int KILL_TARGET = 10;
    private static final int PATROL_RADIUS = 30;
    private static final double CART_GAP = 1.5;
    private static final double SPEED = 0.48;
    private static final String RESPONSE_TAG = "intertnet_emergency_response";
    private static final String RESPONSE_ID = "IntertnetResponse";
    private static final Map<Call, Response> responses = new HashMap<>();

    private record Call(UUID owner, EmergencyService service) {}
    private enum Phase { LOADING, LEAVING_STATION, EN_ROUTE, ON_SCENE, LEAVING }
    private record Unit(Raider responder, Minecart cart) {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(EmergencyDispatch::tick);
        NeoForge.EVENT_BUS.addListener(EmergencyDispatch::countKill);
        NeoForge.EVENT_BUS.addListener(EmergencyDispatch::protectBystanders);
        NeoForge.EVENT_BUS.addListener(EmergencyDispatch::protectBlocks);
        NeoForge.EVENT_BUS.addListener(EmergencyDispatch::onEntityLoad);
        NeoForge.EVENT_BUS.addListener(EmergencyDispatch::onInteract);
        NeoForge.EVENT_BUS.addListener(EmergencyDispatch::onStop);
    }

    static void request(ServerPlayer player, int option) {
        if (option < 0 || option >= EmergencyService.values().length || !player.isAlive() || player.isSpectator()) {
            return;
        }
        EmergencyService service = EmergencyService.values()[option];
        Call call = new Call(player.getUUID(), service);
        if (responses.containsKey(call)) {
            notify(player, service.label + " are already responding.");
            return;
        }
        if (responses.size() >= MAX_RESPONSES) {
            notify(player, "All emergency crews are busy. Try again shortly.");
            return;
        }
        String sim = DeliveryManager.findPlayerSimNetwork(player);
        if (SignalManager.getBestSignal(player, sim).bars() <= 0) {
            notify(player, sim == null ? "Insert a SIM before calling." : "Emergency calls need reception.");
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos pickup = ServiceTrip.safeSurface(level, player.blockPosition(), 12, 5);
        if (pickup == null) {
            pickup = ServiceTrip.safeSurface(level, player.blockPosition());
        }
        if (pickup == null) {
            notify(player, "Move near open ground so the crew has somewhere to stop.");
            return;
        }
        BlockPos source = TownServices.nearest(level, service.station(), pickup, STATION_RANGE);
        if (source == null) {
            notify(player, "No " + service.getSerializedName() + " station within " + STATION_RANGE + " blocks.");
            return;
        }
        responses.put(call, new Response(level, service, source, pickup));
        notify(player, service.label + " dispatched to " + pickup.toShortString() + ".");
        DeliveryManager.syncToPlayer(player);
    }

    private static void tick(ServerTickEvent.Post event) {
        var iterator = responses.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            Response response = entry.getValue();
            ServerPlayer owner = event.getServer().getPlayerList().getPlayer(entry.getKey().owner());
            if (owner == null || !owner.isAlive() || owner.serverLevel() != response.level || !response.tick(owner)) {
                response.close();
                iterator.remove();
            }
        }
    }

    static void addTracking(ServerPlayer player, List<ModNetwork.OrderSnapshot> orders) {
        for (EmergencyService service : EmergencyService.values()) {
            Response response = responses.get(new Call(player.getUUID(), service));
            if (response != null) {
                orders.add(response.snapshot());
            }
        }
    }

    private static void countKill(LivingDeathEvent event) {
        Entity attacker = event.getSource().getEntity();
        if (event.isCanceled() || attacker == null || !isResponder(attacker) || !isThreat(event.getEntity())) {
            return;
        }
        Response response = responseOf(attacker);
        if (response != null && response.service == EmergencyService.POLICE && response.phase == Phase.ON_SCENE) {
            response.kills++;
        }
    }

    private static Response responseOf(Entity entity) {
        UUID id = entity.getPersistentData().getUUID(RESPONSE_ID);
        for (Response response : responses.values()) {
            if (response.id.equals(id)) {
                return response;
            }
        }
        return null;
    }

    private static boolean isResponder(Entity entity) {
        return entity.getTags().contains(RESPONSE_TAG) && entity.getPersistentData().hasUUID(RESPONSE_ID);
    }

    private static boolean isThreat(LivingEntity entity) {
        return entity instanceof Enemy && !entity.getTags().contains(RESPONSE_TAG)
                && !entity.getTags().contains(TowerGuards.GUARD);
    }

    private static void protectBystanders(LivingIncomingDamageEvent event) {
        Entity attacker = event.getSource().getEntity();
        Entity direct = event.getSource().getDirectEntity();
        if (!isThreat(event.getEntity())
                && ((attacker != null && isResponder(attacker)) || (direct != null && isResponder(direct)))) {
            event.setCanceled(true);
        }
    }

    private static void protectBlocks(EntityMobGriefingEvent event) {
        if (event.getEntity().getTags().contains(RESPONSE_TAG)) {
            event.setCanGrief(false);
        }
    }

    private static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget().getTags().contains(RESPONSE_TAG)) {
            event.setCancellationResult(InteractionResult.CONSUME);
            event.setCanceled(true);
        }
    }

    private static void onEntityLoad(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) {
            return;
        }
        if (event.loadedFromDisk() && event.getEntity().getTags().contains(RESPONSE_TAG)) {
            event.setCanceled(true);
        } else if (event.getEntity() instanceof AbstractArrow arrow && arrow.getOwner() instanceof ServicePillager shooter) {
            Response response = responseOf(shooter);
            if (response != null) {
                response.tag(arrow);
                arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
                response.arrows.add(arrow);
            }
        }
    }

    private static void onStop(ServerStoppingEvent event) {
        responses.values().forEach(Response::close);
        responses.clear();
    }

    private static void notify(ServerPlayer player, String message) {
        player.displayClientMessage(Component.literal(message), false);
        PacketDistributor.sendToPlayer(player, new ModNetwork.ServiceFeedbackPayload(ModNetwork.EMERGENCY_FEEDBACK, message));
    }

    private static final class Response {
        private final UUID id = UUID.randomUUID();
        private final ServerLevel level;
        private final EmergencyService service;
        private final ChunkPos stationChunk;
        private final BlockPos pickup;
        private final long arrivalDeadline;
        private final Set<ChunkPos> tickets = new HashSet<>();
        private final List<Unit> units = new ArrayList<>();
        private final List<AbstractArrow> arrows = new ArrayList<>();
        private Phase phase = Phase.LOADING;
        private BlockPos origin;
        private Vec3 lead;
        private Vec3 forward = new Vec3(0, 0, 1);
        private Vec3 departure;
        private long sceneDeadline;
        private long clearSince;
        private long departDeadline;
        private int kills;
        private double initialDistance;

        Response(ServerLevel level, EmergencyService service, BlockPos station, BlockPos pickup) {
            this.level = level;
            this.service = service;
            this.stationChunk = new ChunkPos(station);
            this.pickup = pickup;
            this.lead = Vec3.atBottomCenterOf(station);
            this.initialDistance = Math.max(1, ServiceTrip.horizontalDistance(lead, Vec3.atBottomCenterOf(pickup)));
            this.arrivalDeadline = level.getGameTime() + 20 * 8 * 60;
            pin(station, station, station);
        }

        boolean tick(ServerPlayer owner) {
            long now = level.getGameTime();
            arrows.removeIf(Entity::isRemoved);
            if (phase.ordinal() < Phase.ON_SCENE.ordinal() && now >= arrivalDeadline) {
                EmergencyDispatch.notify(owner, service.label + " couldn't reach the pickup.");
                return false;
            }
            if (phase == Phase.LOADING) {
                return dispatch(owner);
            }
            if (units.stream().noneMatch(unit -> unit.responder.isAlive())) {
                EmergencyDispatch.notify(owner, "The " + service.getSerializedName() + " crew is down.");
                return false;
            }
            if (phase == Phase.LEAVING_STATION) {
                Vec3 exit = Vec3.atBottomCenterOf(origin.offset(7, 1, 20)).add(0.5, 0, 0);
                if (ServiceTrip.horizontalDistance(lead, exit) > 0.2) {
                    advance(exit, true);
                } else {
                    doors(false);
                    phase = Phase.EN_ROUTE;
                }
            } else if (phase == Phase.EN_ROUTE) {
                Vec3 target = Vec3.atBottomCenterOf(pickup);
                if (ServiceTrip.horizontalDistance(lead, target) > 2.5) {
                    advance(target, false);
                } else {
                    arrive(owner);
                }
            } else if (phase == Phase.ON_SCENE) {
                if (service == EmergencyService.MEDIC) {
                    treat(owner, now);
                } else {
                    patrol(owner, now);
                }
            } else {
                if (now >= departDeadline || ServiceTrip.horizontalDistance(lead, departure) < 1) {
                    return false;
                }
                advance(departure, false);
            }
            return true;
        }

        private boolean dispatch(ServerPlayer owner) {
            var chunk = level.getChunkSource().getChunkNow(stationChunk.x, stationChunk.z);
            if (chunk == null) {
                return true;
            }
            var station = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getOrThrow(service.station());
            var start = chunk.getStartForStructure(station);
            if (start == null || !start.isValid()) {
                EmergencyDispatch.notify(owner, "The station is unavailable.");
                return false;
            }
            for (Response other : responses.values()) {
                if (other != this && other.level == level && other.stationChunk.equals(stationChunk)
                        && other.phase == Phase.LEAVING_STATION) {
                    return true;
                }
            }
            origin = new BlockPos(stationChunk.getMinBlockX(), start.getBoundingBox().minY(), stationChunk.getMinBlockZ());
            lead = Vec3.atBottomCenterOf(origin.offset(ServiceSitePiece.EMERGENCY_BAY)).add(0.5, 0, 0);
            int cartCount = service == EmergencyService.MEDIC ? 2 : 6;
            for (int i = 0; i < cartCount; i++) {
                boolean ravager = service == EmergencyService.POLICE && (i == 1 || i == 3);
                if (!spawnUnit(ravager, lead.subtract(forward.scale(CART_GAP * i)))) {
                    EmergencyDispatch.notify(owner, "The station could not deploy a crew.");
                    return false;
                }
            }
            doors(true);
            phase = Phase.LEAVING_STATION;
            return true;
        }

        private boolean spawnUnit(boolean ravager, Vec3 position) {
            Minecart cart = EntityType.MINECART.create(level);
            if (cart == null) {
                return false;
            }
            var responder = ravager ? new ServiceRavager(level) : new ServicePillager(level, service);
            units.add(new Unit(responder, cart));
            tag(cart);
            tag(responder);
            responder.setPersistenceRequired();
            responder.setCanJoinRaid(false);
            responder.setNoAi(true);
            responder.setCustomName(Component.literal(ravager ? "Police Ravager"
                    : service == EmergencyService.MEDIC ? "Medic" : "Police Officer"));
            responder.setCustomNameVisible(true);
            cart.setNoGravity(true);
            cart.setInvulnerable(true);
            cart.moveTo(position.x, position.y, position.z, 0, 0);
            responder.moveTo(position.x, position.y, position.z, 0, 0);
            return level.addFreshEntity(cart) && level.addFreshEntity(responder) && responder.startRiding(cart, true);
        }

        private void tag(Entity entity) {
            entity.addTag(RESPONSE_TAG);
            entity.getPersistentData().putUUID(RESPONSE_ID, id);
        }

        private void advance(Vec3 target, boolean flat) {
            Vec3 delta = target.subtract(lead).multiply(1, 0, 1);
            Vec3 direction = delta.normalize();
            Vec3 next = lead.add(direction.scale(Math.min(SPEED, delta.length())));
            Vec3 tail = next.subtract(direction.scale(CART_GAP * (units.size() - 1)));
            BlockPos nextBlock = BlockPos.containing(next);
            BlockPos tailBlock = BlockPos.containing(tail);
            pin(nextBlock, tailBlock, BlockPos.containing(next.add(direction.scale(32))));
            if (!level.isLoaded(nextBlock) || !level.isLoaded(tailBlock)) {
                return;
            }
            lead = flat ? next : ServiceTrip.followGround(level, next, lead.y, target.y);
            forward = direction;
            float yaw = (float) Math.toDegrees(Mth.atan2(direction.z, direction.x));
            for (int i = 0; i < units.size(); i++) {
                Minecart cart = units.get(i).cart;
                if (cart.isAlive()) {
                    Vec3 at = lead.subtract(direction.scale(CART_GAP * i));
                    if (!flat && i > 0) {
                        at = ServiceTrip.followGround(level, at, cart.getY(), target.y);
                    }
                    DeliveryManager.positionCart(level, cart, at, yaw);
                    cart.setDeltaMovement(Vec3.ZERO);
                }
            }
        }

        private void pin(BlockPos center, BlockPos tail, BlockPos ahead) {
            ServiceTrip.pinVehicleChunks(level, id, tickets, center, tail, ahead);
        }

        private void arrive(ServerPlayer owner) {
            phase = Phase.ON_SCENE;
            clearSince = level.getGameTime();
            sceneDeadline = clearSince + 20L * (service == EmergencyService.MEDIC ? 180 : 300);
            for (Unit unit : units) {
                var responder = unit.responder;
                if (responder.isAlive()) {
                    responder.stopRiding();
                    responder.setNoAi(false);
                }
            }
            EmergencyDispatch.notify(owner, service == EmergencyService.MEDIC
                    ? "Medics arrived. Stay in view so they can treat you."
                    : "Police arrived. The squad is clearing nearby hostile mobs.");
        }

        private void treat(ServerPlayer owner, long now) {
            if (owner.getHealth() >= owner.getMaxHealth()) {
                leave(owner, "Treatment complete. The medics are leaving.");
                return;
            }
            if (now >= sceneDeadline) {
                leave(owner, "Medics ended the call. You can call again.");
                return;
            }
            if (!nearScene(owner)) {
                return;
            }
            for (int i = 0; i < units.size(); i++) {
                if (!(units.get(i).responder instanceof ServicePillager medic) || !medic.isAlive()) {
                    continue;
                }
                medic.getLookControl().setLookAt(owner, 30, 30);
                double distance = medic.distanceToSqr(owner);
                if (distance > 10 * 10 || !medic.hasLineOfSight(owner)) {
                    medic.setChargingCrossbow(false);
                    if (now % 10 == 0) {
                        medic.getNavigation().moveTo(owner, 1.15);
                    }
                    continue;
                }
                medic.getNavigation().stop();
                long shotTick = (now + 16L * i) % 45;
                medic.setChargingCrossbow(shotTick >= 23);
                if (shotTick == 0) {
                    shoot(medic, owner);
                }
            }
        }

        private void shoot(Pillager medic, ServerPlayer owner) {
            HealingArrow arrow = new HealingArrow(level, medic, owner.getUUID());
            double dx = owner.getX() - arrow.getX();
            double dz = owner.getZ() - arrow.getZ();
            double dy = owner.getY(0.45) - arrow.getY() + Math.hypot(dx, dz) * 0.14;
            arrow.shoot(dx, dy, dz, 1.6f, 0);
            if (level.addFreshEntity(arrow)) {
                medic.playSound(SoundEvents.CROSSBOW_SHOOT, 1, 1);
            }
        }

        private void patrol(ServerPlayer owner, long now) {
            if (kills >= KILL_TARGET) {
                leave(owner, "Police cleared 10 hostile mobs and are leaving.");
                return;
            }
            if (now >= sceneDeadline) {
                leave(owner, "Police ended the sweep at " + kills + "/10 hostile mobs.");
                return;
            }
            if (now % 10 != 0) {
                return;
            }
            BlockPos center = nearScene(owner) ? owner.blockPosition() : pickup;
            Vec3 centerPoint = Vec3.atCenterOf(center);
            List<LivingEntity> threats = level.getEntitiesOfClass(LivingEntity.class, new AABB(center).inflate(PATROL_RADIUS),
                    entity -> entity.isAlive() && isThreat(entity) && entity.distanceToSqr(centerPoint) <= PATROL_RADIUS * PATROL_RADIUS);
            if (!threats.isEmpty()) {
                clearSince = now;
            } else if (now - clearSince >= 20 * 60) {
                leave(owner, "Area clear. Police are leaving (" + kills + "/10 hostile mobs).");
                return;
            }
            for (Unit unit : units) {
                var responder = unit.responder;
                if (!responder.isAlive()) {
                    continue;
                }
                LivingEntity best = null;
                double bestDistance = Double.MAX_VALUE;
                for (LivingEntity threat : threats) {
                    double distance = responder.distanceToSqr(threat);
                    if (distance < bestDistance) {
                        best = threat;
                        bestDistance = distance;
                    }
                }
                responder.setTarget(best);
                if (best == null && responder.distanceToSqr(centerPoint) > 10 * 10) {
                    responder.getNavigation().moveTo(centerPoint.x, center.getY(), centerPoint.z, 1.0);
                }
            }
        }

        private boolean nearScene(ServerPlayer owner) {
            return owner.distanceToSqr(Vec3.atBottomCenterOf(pickup)) < 36 * 36;
        }

        private void leave(ServerPlayer owner, String message) {
            EmergencyDispatch.notify(owner, message);
            arrows.forEach(Entity::discard);
            arrows.clear();
            for (Unit unit : units) {
                var responder = unit.responder;
                if (responder.isAlive() && unit.cart.isAlive()) {
                    responder.setTarget(null);
                    responder.setNoAi(true);
                    responder.getNavigation().stop();
                    responder.moveTo(unit.cart.getX(), unit.cart.getY(), unit.cart.getZ(), unit.cart.getYRot(), 0);
                    responder.startRiding(unit.cart, true);
                }
            }
            Vec3 home = Vec3.atBottomCenterOf(origin).subtract(lead).multiply(1, 0, 1);
            Vec3 direction = home.lengthSqr() > 1 ? home.normalize() : forward;
            departure = lead.add(direction.scale(24));
            departDeadline = level.getGameTime() + 20 * 15;
            phase = Phase.LEAVING;
        }

        private void doors(boolean open) {
            if (origin != null && level.isLoaded(origin)) {
                for (int x : new int[]{7, 8}) {
                    ServiceSitePiece.setIronDoor(level, origin.offset(x, 1, 12), open);
                }
            }
        }

        ModNetwork.OrderSnapshot snapshot() {
            int distance = phase.ordinal() < Phase.ON_SCENE.ordinal()
                    ? (int) Math.ceil(ServiceTrip.horizontalDistance(lead, Vec3.atBottomCenterOf(pickup))) : 0;
            String status = switch (phase) {
                case LOADING -> "Preparing at station";
                case LEAVING_STATION, EN_ROUTE -> distance + "m away";
                case ON_SCENE -> service == EmergencyService.MEDIC ? "On scene · treating"
                        : "On scene · " + Math.min(kills, KILL_TARGET) + "/" + KILL_TARGET + " cleared";
                case LEAVING -> "Leaving the scene";
            };
            int progress = phase == Phase.ON_SCENE && service == EmergencyService.POLICE ? Math.min(100, kills * 10)
                    : Mth.clamp((int) ((1 - distance / initialDistance) * 100), 0, 100);
            return new ModNetwork.OrderSnapshot("EMERGENCY_" + service.name(), service.label, status, distance, progress, pickup.toShortString());
        }

        void close() {
            if (phase == Phase.LEAVING_STATION) {
                doors(false);
            }
            for (Unit unit : units) {
                unit.responder.discard();
                unit.cart.discard();
            }
            arrows.forEach(Entity::discard);
            ServiceTrip.releaseVehicleChunks(level, id, tickets);
        }
    }

    // These temporary entities use vanilla rendering and are discarded if loaded from an interrupted response.
    private static final class ServicePillager extends Pillager {
        ServicePillager(ServerLevel level, EmergencyService service) {
            super(EntityType.PILLAGER, level);
            targetSelector.removeAllGoals(goal -> true);
            goalSelector.removeAllGoals(goal -> true);
            goalSelector.addGoal(0, new FloatGoal(this));
            if (service == EmergencyService.POLICE) {
                goalSelector.addGoal(1, new RangedCrossbowAttackGoal<>(this, 1.1, 14));
            }
            setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CROSSBOW));
        }

        @Override
        protected boolean shouldDespawnInPeaceful() {
            return false;
        }
    }

    private static final class ServiceRavager extends Ravager {
        ServiceRavager(ServerLevel level) {
            super(EntityType.RAVAGER, level);
            targetSelector.removeAllGoals(goal -> true);
            goalSelector.removeAllGoals(goal -> true);
            goalSelector.addGoal(0, new FloatGoal(this));
            goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.05, true));
        }

        @Override
        protected boolean shouldDespawnInPeaceful() {
            return false;
        }
    }

    private static final class HealingArrow extends Arrow {
        private final UUID patient;

        HealingArrow(ServerLevel level, Pillager shooter, UUID patient) {
            super(level, shooter, healingTip(), null);
            this.patient = patient;
        }

        private static ItemStack healingTip() {
            ItemStack tip = new ItemStack(Items.TIPPED_ARROW);
            tip.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.HEALING));
            return tip;
        }

        @Override
        protected boolean canHitEntity(Entity entity) {
            return patient.equals(entity.getUUID()) && super.canHitEntity(entity);
        }

        @Override
        protected void onHitEntity(EntityHitResult hit) {
            if (level() instanceof ServerLevel serverLevel && hit.getEntity() instanceof LivingEntity target) {
                target.heal(6);
                serverLevel.sendParticles(ParticleTypes.HEART, target.getX(), target.getY() + 1.2, target.getZ(), 3, 0.35, 0.2, 0.35, 0);
                serverLevel.playSound(null, target.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6f, 1.3f);
            }
            discard();
        }

        @Override
        protected void onHitBlock(BlockHitResult hit) {
            discard();
        }
    }
}
