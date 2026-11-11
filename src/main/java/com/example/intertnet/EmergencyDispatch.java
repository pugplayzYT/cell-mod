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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.StructureStart;
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
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

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
    private static final Queue<StationRepair> pendingRepairs = new ConcurrentLinkedQueue<>();

    private record Call(UUID owner, EmergencyService service) {}
    private record StationRepair(ServerLevel level, StructureStart start) {}
    private enum Phase {
        LOADING, LEAVING_STATION, EN_ROUTE, BOARDING, RETURNING, ENTERING_STATION, TREATMENT, WAITING_FOR_EXIT,
        ON_SCENE, LEAVING
    }
    private record Unit(Raider responder, Minecart cart) {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(EmergencyDispatch::tick);
        NeoForge.EVENT_BUS.addListener(EmergencyDispatch::countKill);
        NeoForge.EVENT_BUS.addListener(EmergencyDispatch::protectBystanders);
        NeoForge.EVENT_BUS.addListener(EmergencyDispatch::protectBlocks);
        NeoForge.EVENT_BUS.addListener(EmergencyDispatch::onEntityLoad);
        NeoForge.EVENT_BUS.addListener(EmergencyDispatch::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(EmergencyDispatch::onInteract);
        NeoForge.EVENT_BUS.addListener(EmergencyDispatch::onInteractAt);
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
        repairLoadedStations();
        var iterator = responses.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            Response response = entry.getValue();
            ServerPlayer owner = event.getServer().getPlayerList().getPlayer(entry.getKey().owner());
            if (owner == null || !owner.isAlive() || owner.isSpectator()
                    || owner.serverLevel() != response.level || !response.tick(owner)) {
                response.close();
                iterator.remove();
            }
        }
    }

    private static void onChunkLoad(ChunkEvent.Load event) {
        if (event.isNewChunk() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        for (var start : event.getChunk().getAllStarts().values()) {
            if (start.isValid() && start.getStructure() instanceof ServiceSiteStructure site && site.emergency().isPresent()) {
                pendingRepairs.add(new StationRepair(level, start));
            }
        }
    }

    private static void repairLoadedStations() {
        StationRepair repair;
        while ((repair = pendingRepairs.poll()) != null) {
            ChunkPos pos = repair.start.getChunkPos();
            ServerLevel level = repair.level;
            var chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
            if (chunk != null && chunk.getStartForStructure(repair.start.getStructure()) == repair.start
                    && responses.values().stream().noneMatch(response -> response.level == level
                    && response.stationChunk.equals(pos) && response.usesGarage())) {
                ServiceSitePiece.repairEmergencyStation(level, chunk, repair.start);
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
            boardAmbulance(event.getEntity(), event.getHand(), event.getTarget());
        }
    }

    private static void onInteractAt(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getTarget().getTags().contains(RESPONSE_TAG)) {
            event.setCancellationResult(InteractionResult.CONSUME);
            event.setCanceled(true);
            boardAmbulance(event.getEntity(), event.getHand(), event.getTarget());
        }
    }

    private static void boardAmbulance(Entity entity, InteractionHand hand, Entity target) {
        if (hand != InteractionHand.MAIN_HAND || !(entity instanceof ServerPlayer player)) {
            return;
        }
        Response response = responses.get(new Call(player.getUUID(), EmergencyService.MEDIC));
        if (response != null && response.phase == Phase.BOARDING && target == response.patientCart) {
            if (player.isShiftKeyDown()) {
                player.displayClientMessage(Component.literal("Release sneak, then right-click the ambulance seat."), true);
            } else if (player.startRiding(response.patientCart, true)) {
                response.initialDistance = Math.max(1, ServiceTrip.horizontalDistance(response.lead, response.approach()));
                response.sceneDeadline = response.level.getGameTime() + 20L * 8 * 60;
                response.phase = Phase.RETURNING;
                notify(player, "On the way to the medic station.");
            }
        }
    }

    static boolean isTreatmentSeat(DiningSeat seat) {
        return seat.getTags().contains(RESPONSE_TAG);
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
        pendingRepairs.clear();
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
        private Minecart patientCart;
        private DiningSeat treatmentSeat;
        private BlockPos chair;
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
            if (outbound() && now >= arrivalDeadline) {
                EmergencyDispatch.notify(owner, service.label + " couldn't reach the pickup.");
                return false;
            }
            if (phase == Phase.LOADING) {
                return dispatch(owner);
            }
            if (phase != Phase.WAITING_FOR_EXIT && units.stream().noneMatch(unit -> unit.responder.isAlive())) {
                if ((phase == Phase.TREATMENT || phase == Phase.ENTERING_STATION) && insideClinic(owner)) {
                    finishTreatment(owner, "The crew is down. You can leave the clinic.");
                    return true;
                }
                EmergencyDispatch.notify(owner, "The " + service.getSerializedName() + " crew is down.");
                return false;
            }
            if (service == EmergencyService.MEDIC && phase != Phase.TREATMENT && phase != Phase.WAITING_FOR_EXIT
                    && !patientCart.isAlive()) {
                if (phase == Phase.ENTERING_STATION && insideClinic(owner)) {
                    finishTreatment(owner, "The ambulance seat is unavailable. The doors are open.");
                    return true;
                }
                EmergencyDispatch.notify(owner, "The ambulance seat is unavailable.");
                return false;
            }
            if (phase == Phase.LEAVING_STATION) {
                Vec3 exit = approach();
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
                patrol(owner, now);
            } else if (phase == Phase.BOARDING) {
                if (now >= sceneDeadline) {
                    EmergencyDispatch.notify(owner, "The ambulance left after waiting for you.");
                    return false;
                }
            } else if (phase == Phase.RETURNING || phase == Phase.ENTERING_STATION) {
                return returnToClinic(owner, now);
            } else if (phase == Phase.TREATMENT) {
                if (!insideClinic(owner)) {
                    return false;
                }
                treat(owner, now);
            } else if (phase == Phase.WAITING_FOR_EXIT) {
                return insideClinic(owner);
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
            if (garageOccupied()) {
                return true;
            }
            origin = new BlockPos(stationChunk.getMinBlockX(), start.getBoundingBox().minY(), stationChunk.getMinBlockZ());
            ServiceSitePiece.repairEmergencyStation(level, chunk, start);
            lead = Vec3.atBottomCenterOf(origin.offset(ServiceSitePiece.EMERGENCY_BAY)).add(0.5, 0, 0);
            int cartCount = service == EmergencyService.MEDIC ? 2 : 6;
            for (int i = 0; i < cartCount; i++) {
                boolean ravager = service == EmergencyService.POLICE && (i == 1 || i == 3);
                if (!spawnUnit(ravager, lead.subtract(forward.scale(CART_GAP * i)))) {
                    EmergencyDispatch.notify(owner, "The station could not deploy a crew.");
                    return false;
                }
            }
            if (service == EmergencyService.MEDIC) {
                patientCart = EntityType.MINECART.create(level);
                if (patientCart == null || !spawnCart(patientCart, lead.subtract(forward.scale(CART_GAP * cartCount)))) {
                    EmergencyDispatch.notify(owner, "The station could not deploy an ambulance seat.");
                    return false;
                }
                patientCart.setCustomName(Component.literal("Ambulance Seat"));
                patientCart.setCustomNameVisible(true);
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
            tag(responder);
            responder.setPersistenceRequired();
            responder.setCanJoinRaid(false);
            responder.setNoAi(true);
            responder.setCustomName(Component.literal(ravager ? "Police Ravager"
                    : service == EmergencyService.MEDIC ? "Medic" : "Police Officer"));
            responder.setCustomNameVisible(true);
            responder.moveTo(position.x, position.y, position.z, 0, 0);
            return spawnCart(cart, position) && level.addFreshEntity(responder) && responder.startRiding(cart, true);
        }

        private boolean spawnCart(Minecart cart, Vec3 position) {
            tag(cart);
            cart.setNoGravity(true);
            cart.setInvulnerable(true);
            cart.moveTo(position.x, position.y, position.z, 0, 0);
            return level.addFreshEntity(cart);
        }

        private void tag(Entity entity) {
            entity.addTag(RESPONSE_TAG);
            entity.getPersistentData().putUUID(RESPONSE_ID, id);
        }

        private void advance(Vec3 target, boolean flat) {
            Vec3 delta = target.subtract(lead).multiply(1, 0, 1);
            Vec3 direction = delta.normalize();
            Vec3 next = lead.add(direction.scale(Math.min(SPEED, delta.length())));
            int cartCount = units.size() + (patientCart == null ? 0 : 1);
            Vec3 tail = next.subtract(direction.scale(CART_GAP * (cartCount - 1)));
            BlockPos nextBlock = BlockPos.containing(next);
            BlockPos tailBlock = BlockPos.containing(tail);
            pin(nextBlock, tailBlock, BlockPos.containing(next.add(direction.scale(32))));
            if (!level.isLoaded(nextBlock) || !level.isLoaded(tailBlock)) {
                return;
            }
            lead = flat ? next : ServiceTrip.followGround(level, next, lead.y, target.y);
            forward = direction;
            float yaw = (float) Math.toDegrees(Mth.atan2(direction.z, direction.x));
            for (int i = 0; i < cartCount; i++) {
                Minecart cart = i < units.size() ? units.get(i).cart : patientCart;
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
            if (service == EmergencyService.MEDIC) {
                phase = Phase.BOARDING;
                sceneDeadline = level.getGameTime() + 20L * 120;
                EmergencyDispatch.notify(owner, "Ambulance arrived. Right-click the rear cart to board.");
                return;
            }
            phase = Phase.ON_SCENE;
            clearSince = level.getGameTime();
            sceneDeadline = clearSince + 20L * 300;
            for (Unit unit : units) {
                var responder = unit.responder;
                if (responder.isAlive()) {
                    responder.stopRiding();
                    responder.setNoAi(false);
                }
            }
            EmergencyDispatch.notify(owner, "Police arrived. The squad is clearing nearby hostile mobs.");
        }

        private Vec3 approach() {
            return Vec3.atBottomCenterOf(origin.offset(7, 1, 22)).add(0.5, 0, 0);
        }

        private boolean outbound() {
            return phase == Phase.LOADING || phase == Phase.LEAVING_STATION || phase == Phase.EN_ROUTE;
        }

        private boolean usesGarage() {
            return phase == Phase.LEAVING_STATION || phase == Phase.ENTERING_STATION || phase == Phase.WAITING_FOR_EXIT;
        }

        private boolean garageOccupied() {
            return responses.values().stream().anyMatch(other -> other != this && other.level == level
                    && other.stationChunk.equals(stationChunk) && other.usesGarage());
        }

        private boolean returnToClinic(ServerPlayer owner, long now) {
            if (!patientCart.hasPassenger(owner)) {
                if (phase == Phase.ENTERING_STATION && insideClinic(owner)) {
                    finishTreatment(owner, "You got out of the ambulance. The doors are open.");
                    return true;
                }
                EmergencyDispatch.notify(owner, "Medic transport ended because you got out of the ambulance.");
                return false;
            }
            if (phase == Phase.RETURNING) {
                if (now >= sceneDeadline) {
                    EmergencyDispatch.notify(owner, "The ambulance couldn't get you into the clinic.");
                    return false;
                }
                Vec3 approach = approach();
                if (ServiceTrip.horizontalDistance(lead, approach) > 0.2) {
                    advance(approach, false);
                } else if (!garageOccupied()) {
                    for (BlockPos offset : ServiceSitePiece.MEDIC_CHAIRS) {
                        BlockPos candidate = origin.offset(offset);
                        if (level.getBlockState(candidate).is(Blocks.SPRUCE_STAIRS)
                                && level.getBlockState(candidate.above()).isAir()
                                && responses.values().stream().noneMatch(other -> other.level == level
                                && candidate.equals(other.chair))) {
                            chair = candidate;
                            doors(true);
                            phase = Phase.ENTERING_STATION;
                            break;
                        }
                    }
                }
            } else {
                Vec3 threshold = Vec3.atBottomCenterOf(origin.offset(7, 1, 15)).add(0.5, 0, 0);
                Vec3 bay = Vec3.atBottomCenterOf(origin.offset(7, 1, 6)).add(0.5, 0, 0);
                if (lead.z > threshold.z + 0.2) {
                    advance(threshold, false);
                } else if (ServiceTrip.horizontalDistance(lead, bay) > 0.2) {
                    advance(bay, true);
                } else if (!seatPatient(owner)) {
                    finishTreatment(owner, "The clinic seat is unavailable. The doors are open.");
                }
            }
            return true;
        }

        private boolean seatPatient(ServerPlayer owner) {
            treatmentSeat = IntertnetMod.DINING_SEAT.get().create(level);
            if (treatmentSeat == null) {
                return false;
            }
            Vec3 seat = Vec3.atBottomCenterOf(chair).add(0, 0.48, 0.2);
            treatmentSeat.moveTo(seat.x, seat.y, seat.z, 0, 0);
            tag(treatmentSeat);
            if (!level.addFreshEntity(treatmentSeat) || !owner.startRiding(treatmentSeat, true)) {
                return false;
            }
            discardAmbulance();
            for (int i = 0; i < units.size(); i++) {
                Raider medic = units.get(i).responder;
                if (medic.isAlive()) {
                    Vec3 stand = Vec3.atBottomCenterOf(origin.offset(6 + 2 * i, 1, 10));
                    medic.moveTo(stand.x, stand.y, stand.z, 180, 0);
                    medic.setNoAi(false);
                }
            }
            // Keep the ward and its entrance loaded until the patient leaves.
            pin(origin, origin, origin);
            doors(false);
            phase = Phase.TREATMENT;
            sceneDeadline = level.getGameTime() + 20L * 180;
            EmergencyDispatch.notify(owner, "You're in a treatment chair. The medics will restore your health.");
            return true;
        }

        private void discardAmbulance() {
            if (patientCart != null) {
                patientCart.discard();
                patientCart = null;
            }
            for (Unit unit : units) {
                unit.responder.stopRiding();
                unit.cart.discard();
            }
        }

        private boolean insideClinic(ServerPlayer owner) {
            double x = owner.getX() - origin.getX();
            double y = owner.getY() - origin.getY();
            double z = owner.getZ() - origin.getZ();
            return x > 1.7 && x < 13.3 && y >= 0 && y < 5 && z > 1.7 && z < 13.3;
        }

        private void treat(ServerPlayer owner, long now) {
            if (owner.getHealth() >= owner.getMaxHealth()) {
                finishTreatment(owner, "Treatment complete. The doors are open.");
                if (phase == Phase.WAITING_FOR_EXIT) {
                    IntertnetAdvancements.award(owner, IntertnetAdvancements.EMERGENCY_RESPONSE);
                }
                return;
            }
            if (now >= sceneDeadline) {
                finishTreatment(owner, "Treatment has ended. The doors are open.");
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

        private void finishTreatment(ServerPlayer owner, String message) {
            clearArrows();
            for (Unit unit : units) {
                if (unit.responder instanceof ServicePillager medic) {
                    medic.setChargingCrossbow(false);
                }
            }
            if (garageOccupied()) {
                return;
            }
            if (treatmentSeat != null) {
                if (treatmentSeat.hasPassenger(owner)) {
                    owner.stopRiding();
                }
                treatmentSeat.discard();
                treatmentSeat = null;
            }
            if (patientCart != null && patientCart.hasPassenger(owner)) {
                owner.stopRiding();
            }
            discardAmbulance();
            chair = null;
            doors(true);
            phase = Phase.WAITING_FOR_EXIT;
            EmergencyDispatch.notify(owner, message);
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
                IntertnetAdvancements.award(owner, IntertnetAdvancements.EMERGENCY_RESPONSE);
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
                IntertnetAdvancements.award(owner, IntertnetAdvancements.EMERGENCY_RESPONSE);
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
            clearArrows();
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

        private void clearArrows() {
            arrows.forEach(Entity::discard);
            arrows.clear();
        }

        private void doors(boolean open) {
            if (origin != null && level.isLoaded(origin)) {
                ServiceSitePiece.setEmergencyGarage(level, origin, service, open);
            }
        }

        ModNetwork.OrderSnapshot snapshot() {
            Vec3 destination = phase == Phase.RETURNING ? approach() : Vec3.atBottomCenterOf(pickup);
            int distance = outbound() || phase == Phase.RETURNING
                    ? (int) Math.ceil(ServiceTrip.horizontalDistance(lead, destination)) : 0;
            String status = switch (phase) {
                case LOADING -> "Preparing at station";
                case LEAVING_STATION, EN_ROUTE -> distance + "m away";
                case BOARDING -> "At pickup · board rear cart";
                case RETURNING -> distance > 1 ? "To clinic · " + distance + "m" : "Waiting for a clinic bay";
                case ENTERING_STATION -> "Entering the clinic";
                case TREATMENT -> "At clinic · treatment";
                case WAITING_FOR_EXIT -> "Doors open · you can leave";
                case ON_SCENE -> "On scene · " + Math.min(kills, KILL_TARGET) + "/" + KILL_TARGET + " cleared";
                case LEAVING -> "Leaving the scene";
            };
            int progress = phase == Phase.ON_SCENE ? Math.min(100, kills * 10)
                    : Mth.clamp((int) ((1 - distance / initialDistance) * 100), 0, 100);
            boolean clinic = phase == Phase.RETURNING || phase == Phase.ENTERING_STATION
                    || phase == Phase.TREATMENT || phase == Phase.WAITING_FOR_EXIT;
            BlockPos location = clinic ? origin.offset(ServiceSitePiece.ENTRANCE) : pickup;
            return new ModNetwork.OrderSnapshot("EMERGENCY_" + service.name(), service.label, status, distance, progress, location.toShortString());
        }

        void close() {
            if (usesGarage()) {
                doors(false);
            }
            if (patientCart != null) {
                patientCart.discard();
            }
            if (treatmentSeat != null) {
                treatmentSeat.discard();
            }
            for (Unit unit : units) {
                unit.responder.discard();
                unit.cart.discard();
            }
            clearArrows();
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
