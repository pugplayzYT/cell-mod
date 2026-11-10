package com.example.intertnet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

final class ServiceTrip {
    private static final TicketType<UUID> TICKET = TicketType.create("intertnet_trip", Comparator.<UUID>naturalOrder());
    private static final int ENTITY_TICKET_RADIUS = 2;
    private static final double SPEED = 0.55;
    private static final double CART_GAP = 1.65;
    static final String VEHICLE_TAG = "intertnet_trip_vehicle";

    final ServerLevel level;
    final BlockPos pickup;
    private BlockPos destination;
    final String destinationLabel;
    final boolean delivery;
    private final UUID ticketId = UUID.randomUUID();
    private final Set<ChunkPos> corridorTickets = new LinkedHashSet<>();
    private final Queue<ChunkPos> remainingCorridor = new ArrayDeque<>();
    private final Set<ChunkPos> vehicleTickets = new LinkedHashSet<>();
    private Vec3 position;
    private Vec3 forward = new Vec3(0, 0, 1);
    private AbstractMinecart front;
    private Minecart passengerCart;
    private Villager driver;
    private BlockPos station;
    private boolean leavingStation;
    private boolean mansionDropoffSet;
    private double legDistance = 1;
    private long waitUntil;
    private Phase phase = Phase.PICKUP;

    private enum Phase { PICKUP, BOARDING, DESTINATION, COLLECTION }
    enum Result { ACTIVE, COMPLETED, ABANDONED, FAILED }

    ServiceTrip(ServerLevel level, BlockPos source, BlockPos pickup, BlockPos destination, String destinationLabel, boolean delivery) {
        this.level = level;
        this.pickup = pickup;
        this.destination = destination;
        this.destinationLabel = destinationLabel;
        this.delivery = delivery;
        this.position = Vec3.atBottomCenterOf(source);
        pinVehicleChunks(source, source);
        if (delivery) {
            ChunkPos start = new ChunkPos(source);
            ChunkPos end = new ChunkPos(pickup);
            int steps = Math.max(Math.abs(end.x - start.x), Math.abs(end.z - start.z));
            Set<ChunkPos> line = new LinkedHashSet<>();
            ChunkPos previous = start;
            for (int i = 0; i <= steps; i++) {
                float fraction = steps == 0 ? 0 : (float) i / steps;
                ChunkPos next = new ChunkPos(Math.round(Mth.lerp(fraction, start.x, end.x)),
                        Math.round(Mth.lerp(fraction, start.z, end.z)));
                if (previous.x != next.x && previous.z != next.z) {
                    line.add(new ChunkPos(next.x, previous.z));
                }
                line.add(next);
                previous = next;
            }
            remainingCorridor.addAll(line);
        }
    }

    void loadCorridor() {
        for (int i = 0; i < 4 && !remainingCorridor.isEmpty(); i++) {
            ChunkPos chunk = remainingCorridor.remove();
            level.getChunkSource().addRegionTicket(TICKET, chunk, 0, ticketId);
            corridorTickets.add(chunk);
        }
    }

    boolean dispatch(BlockPos spawn, BlockPos station, ItemStack meal) {
        this.position = Vec3.atBottomCenterOf(spawn);
        this.station = station;
        this.leavingStation = station != null;
        this.legDistance = Math.max(1, horizontalDistance(position, Vec3.atBottomCenterOf(pickup)));
        front = delivery ? EntityType.CHEST_MINECART.create(level) : EntityType.MINECART.create(level);
        driver = EntityType.VILLAGER.create(level);
        passengerCart = delivery ? null : EntityType.MINECART.create(level);
        if (front == null || driver == null || (!delivery && passengerCart == null)) {
            return false;
        }
        if (!spawn(front, position, delivery ? "Food Delivery" : "Rideshare Driver")) {
            return false;
        }
        driver.setNoAi(true);
        driver.setPersistenceRequired();
        if (!spawn(driver, position, null) || !driver.startRiding(front, true)) {
            return false;
        }
        if (delivery) {
            ((MinecartChest) front).setItem(0, meal);
        } else if (!spawn(passengerCart, position.subtract(forward.scale(CART_GAP)), "Rideshare Seat")) {
            return false;
        }
        setDoors(true);
        return true;
    }

    private boolean spawn(Entity entity, Vec3 pos, String name) {
        entity.addTag(VEHICLE_TAG);
        entity.setInvulnerable(true);
        entity.setNoGravity(true);
        if (name != null) {
            entity.setCustomName(Component.literal(name));
            entity.setCustomNameVisible(true);
        }
        entity.moveTo(pos.x, pos.y, pos.z, 0, 0);
        return level.addFreshEntity(entity);
    }

    Result tick(ServerPlayer owner) {
        if (!front.isAlive() || !driver.isAlive() || (passengerCart != null && !passengerCart.isAlive())) {
            return Result.FAILED;
        }
        if (phase == Phase.COLLECTION) {
            if (((MinecartChest) front).getItem(0).isEmpty()) {
                return Result.COMPLETED;
            }
            return level.getGameTime() > waitUntil ? Result.FAILED : Result.ACTIVE;
        }
        if (phase == Phase.BOARDING) {
            if (passengerCart.hasPassenger(owner)) {
                phase = Phase.DESTINATION;
                legDistance = Math.max(1, horizontalDistance(position, Vec3.atBottomCenterOf(destination)));
            } else {
                return level.getGameTime() > waitUntil ? Result.FAILED : Result.ACTIVE;
            }
        }
        if (phase == Phase.DESTINATION && !passengerCart.hasPassenger(owner)) {
            return Result.ABANDONED;
        }
        if (leavingStation) {
            Vec3 exit = Vec3.atBottomCenterOf(station.offset(7, 1, 16));
            if (horizontalDistance(position, exit) > 0.2) {
                advance(exit, true);
                return Result.ACTIVE;
            }
            leavingStation = false;
            setDoors(false);
        }
        if (phase == Phase.DESTINATION && !mansionDropoffSet && destinationLabel.equals(TownServices.MANSION_LABEL)) {
            findMansionDropoff();
        }
        BlockPos stop = phase == Phase.DESTINATION ? destination : pickup;
        Vec3 target = Vec3.atBottomCenterOf(stop);
        double stoppingDistance = phase == Phase.PICKUP && !delivery ? 1.25 : 2.8;
        if (horizontalDistance(position, target) > stoppingDistance) {
            advance(target, false);
            return Result.ACTIVE;
        }
        if (phase == Phase.DESTINATION) {
            BlockPos dropoff = safeSurface(level, stop);
            if (dropoff == null) {
                return Result.FAILED;
            }
            owner.stopRiding();
            owner.teleportTo(dropoff.getX() + 0.5, dropoff.getY(), dropoff.getZ() + 0.5);
            return Result.COMPLETED;
        }
        phase = delivery ? Phase.COLLECTION : Phase.BOARDING;
        if (!delivery) {
            parkPassengerCart();
        }
        waitUntil = level.getGameTime() + 20 * 120;
        owner.displayClientMessage(Component.literal(delivery
                ? "Delivery arrived. Open the chest cart to collect your meal."
                : "Your ride is here. Right-click the rear cart to board."), false);
        return Result.ACTIVE;
    }

    private void parkPassengerCart() {
        Vec3 rear = position.subtract(forward.scale(CART_GAP));
        BlockPos near = BlockPos.containing(rear.x, pickup.getY(), rear.z);
        BlockPos stand = safeSurface(level, near, 2, 1);
        if (stand == null) {
            stand = safeSurface(level, pickup, 2, 1);
        }
        if (stand != null) {
            DeliveryManager.positionCart(level, passengerCart, Vec3.atBottomCenterOf(stand), front.getYRot());
        }
        front.setDeltaMovement(Vec3.ZERO);
        passengerCart.setDeltaMovement(Vec3.ZERO);
    }

    private void findMansionDropoff() {
        ChunkPos chunk = new ChunkPos(destination);
        var loaded = level.getChunkSource().getChunkNow(chunk.x, chunk.z);
        if (loaded == null) {
            return;
        }
        var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getOrThrow(TownServices.MANSION);
        var start = loaded.getStartForStructure(structure);
        mansionDropoffSet = true;
        if (start == null || !start.isValid()) {
            return;
        }
        var bounds = start.getBoundingBox();
        BlockPos center = bounds.getCenter();
        BlockPos[] exits = {
                new BlockPos(bounds.minX() - 5, center.getY(), center.getZ()),
                new BlockPos(bounds.maxX() + 5, center.getY(), center.getZ()),
                new BlockPos(center.getX(), center.getY(), bounds.minZ() - 5),
                new BlockPos(center.getX(), center.getY(), bounds.maxZ() + 5)
        };
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos exit : exits) {
            double distance = horizontalDistance(position, Vec3.atBottomCenterOf(exit));
            if (distance < bestDistance && level.getWorldBorder().isWithinBounds(exit)) {
                destination = exit;
                bestDistance = distance;
            }
        }
        legDistance = Math.max(legDistance, bestDistance == Double.MAX_VALUE ? 1 : bestDistance);
    }

    private void advance(Vec3 target, boolean flat) {
        Vec3 direction = target.subtract(position).multiply(1, 0, 1).normalize();
        double step = Math.min(SPEED, horizontalDistance(position, target));
        Vec3 next = position.add(direction.scale(step));
        BlockPos ahead = BlockPos.containing(next.add(direction.scale(32)));
        BlockPos behind = BlockPos.containing(position.subtract(forward.scale(CART_GAP)));
        pinVehicleChunks(behind, ahead);
        // Retain the actual leading chunk too when the front and back straddle a corner.
        ChunkPos nextChunk = new ChunkPos(BlockPos.containing(next));
        if (level.getChunkSource().getChunkNow(nextChunk.x, nextChunk.z) == null) {
            return;
        }
        if (!flat) {
            next = followGround(next, position.y, target.y);
        }
        forward = direction;
        position = next;
        float yaw = (float) Math.toDegrees(Mth.atan2(direction.z, direction.x));
        DeliveryManager.positionCart(level, front, next, yaw);
        if (passengerCart != null) {
            Vec3 back = next.subtract(direction.scale(CART_GAP));
            if (!flat) {
                back = followGround(back, next.y, target.y);
            }
            DeliveryManager.positionCart(level, passengerCart, back, yaw);
        }
    }

    private Vec3 followGround(Vec3 at, double previousY, double targetY) {
        return followGround(level, at, previousY, targetY);
    }

    static Vec3 followGround(ServerLevel level, Vec3 at, double previousY, double targetY) {
        double y = DeliveryManager.getGroundY(level, at.x, previousY, at.z, targetY);
        BlockPos column = BlockPos.containing(at);
        if (level.isLoaded(column)) {
            y = Math.max(y, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()));
        }
        return new Vec3(at.x, y, at.z);
    }

    private void pinVehicleChunks(BlockPos at, BlockPos ahead) {
        pinVehicleChunks(level, ticketId, vehicleTickets, BlockPos.containing(position), at, ahead);
    }

    static void pinVehicleChunks(ServerLevel level, UUID ticketId, Set<ChunkPos> vehicleTickets, BlockPos center, BlockPos at, BlockPos ahead) {
        Set<ChunkPos> wanted = new LinkedHashSet<>();
        wanted.add(new ChunkPos(at));
        wanted.add(new ChunkPos(center));
        wanted.add(new ChunkPos(ahead));
        for (ChunkPos chunk : wanted) {
            if (vehicleTickets.add(chunk)) {
                level.getChunkSource().addRegionTicket(TICKET, chunk, ENTITY_TICKET_RADIUS, ticketId);
            }
        }
        vehicleTickets.removeIf(chunk -> {
            if (wanted.contains(chunk)) {
                return false;
            }
            level.getChunkSource().removeRegionTicket(TICKET, chunk, ENTITY_TICKET_RADIUS, ticketId);
            return true;
        });
    }

    private void setDoors(boolean open) {
        if (station == null || !level.isLoaded(station)) {
            return;
        }
        for (int x : new int[]{7, 8}) {
            ServiceSitePiece.setIronDoor(level, station.offset(x, 1, 12), open);
        }
    }

    boolean allowsInteraction(Entity entity) {
        return delivery ? entity == front && phase == Phase.COLLECTION
                : entity == passengerCart && phase == Phase.BOARDING;
    }

    void board(ServerPlayer player) {
        if (passengerCart.hasPassenger(player)) {
            return;
        }
        if (player.isShiftKeyDown()) {
            player.displayClientMessage(Component.literal("Release sneak, then right-click the rideshare seat."), true);
        } else if (passengerCart.isVehicle() || !player.startRiding(passengerCart, true)) {
            player.displayClientMessage(Component.literal("Couldn't board the rideshare seat. Try again."), true);
        }
    }

    boolean usesStation() {
        return front == null || leavingStation;
    }

    void openDeliveryChest(ServerPlayer player) {
        MinecartChest chest = (MinecartChest) front;
        player.openMenu(new SimpleMenuProvider((id, inventory, user) -> new ChestMenu(MenuType.GENERIC_9x1, id, inventory, chest, 1) {
            {
                for (int i = 0; i < 9; i++) {
                    Slot vanilla = slots.get(i);
                    Slot output = new Slot(chest, i, vanilla.x, vanilla.y) {
                        @Override
                        public boolean mayPlace(ItemStack stack) {
                            return false;
                        }
                    };
                    output.index = i;
                    slots.set(i, output);
                }
            }
        }, Component.literal("Delivery Chest")));
    }

    int remainingMeters() {
        if (phase == Phase.BOARDING || phase == Phase.COLLECTION) {
            return 0;
        }
        return (int) Math.ceil(horizontalDistance(position, Vec3.atBottomCenterOf(phase == Phase.DESTINATION ? destination : pickup)));
    }

    int progress() {
        return Mth.clamp((int) ((1.0 - remainingMeters() / legDistance) * 100), 0, 100);
    }

    String status() {
        return switch (phase) {
            case PICKUP -> "Driving to pickup";
            case BOARDING -> "At pickup. Board rear cart.";
            case COLLECTION -> "Arrived. Open the chest cart.";
            case DESTINATION -> "Driving to " + destinationLabel;
        };
    }

    String trackingPoint() {
        BlockPos point = phase == Phase.DESTINATION ? destination : pickup;
        return (phase == Phase.DESTINATION ? "Destination " : "Pickup ") + point.getX() + ", " + point.getZ();
    }

    void close() {
        if (leavingStation) {
            setDoors(false);
        }
        if (passengerCart != null) {
            passengerCart.ejectPassengers();
            passengerCart.discard();
        }
        if (driver != null) {
            driver.discard();
        }
        if (front != null) {
            front.discard();
        }
        for (ChunkPos chunk : corridorTickets) {
            level.getChunkSource().removeRegionTicket(TICKET, chunk, 0, ticketId);
        }
        releaseVehicleChunks(level, ticketId, vehicleTickets);
    }

    static void releaseVehicleChunks(ServerLevel level, UUID ticketId, Set<ChunkPos> vehicleTickets) {
        for (ChunkPos chunk : vehicleTickets) {
            level.getChunkSource().removeRegionTicket(TICKET, chunk, ENTITY_TICKET_RADIUS, ticketId);
        }
        vehicleTickets.clear();
    }

    static BlockPos safeSurface(ServerLevel level, BlockPos near) {
        return safeSurface(level, near, 3, Integer.MAX_VALUE);
    }

    static BlockPos safeSurface(ServerLevel level, BlockPos near, int searchRadius, int heightDifference) {
        for (int radius = 0; radius <= searchRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    BlockPos column = near.offset(dx, 0, dz);
                    if (!level.isLoaded(column)) {
                        continue;
                    }
                    BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
                    if (Math.abs(pos.getY() - near.getY()) <= heightDifference
                            && level.getWorldBorder().isWithinBounds(pos) && level.getBlockState(pos).isAir()
                            && level.getBlockState(pos.above()).isAir() && level.getFluidState(pos.below()).isEmpty()
                            && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    static double horizontalDistance(Vec3 from, Vec3 to) {
        return Math.hypot(from.x - to.x, from.z - to.z);
    }
}
