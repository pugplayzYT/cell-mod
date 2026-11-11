package com.example.intertnet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class RangerDispatch {
    static final int PRICE = 3;
    static final String ORDER_ID = "FOREST_RANGER";
    private static final int RANGE = 256;
    private static final int MAX_CALLS = 8;
    private static final String FARE = "IntertnetRangerFare";
    private static final Map<UUID, Quote> quotes = new HashMap<>();
    private static final Map<UUID, Job> jobs = new HashMap<>();

    private enum Phase { LOADING, DESCENDING, OUTBOUND, MEETING, ESCORTING, CLIMBING, SHELTER }
    private enum Result { ACTIVE, FAILED, COMPLETE }
    private record Quote(ServerLevel level, BlockPos station, BlockPos pickup, long expires) {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(RangerDispatch::tick);
        NeoForge.EVENT_BUS.addListener(RangerDispatch::interact);
        NeoForge.EVENT_BUS.addListener(RangerDispatch::onLogin);
        NeoForge.EVENT_BUS.addListener(RangerDispatch::onStop);
    }

    static void request(ServerPlayer player, boolean confirm) {
        if (!player.isAlive() || player.isSpectator()) return;
        UUID owner = player.getUUID();
        ServerLevel level = player.serverLevel();
        if (jobs.containsKey(owner)) {
            sendQuote(player, null, "You already have a ranger on the way.");
            return;
        }
        String sim = DeliveryManager.findPlayerSimNetwork(player);
        if (SignalManager.getBestSignal(player, sim).bars() <= 0) {
            sendQuote(player, null, "Ranger calls need phone reception.");
            return;
        }
        if (!level.getBiome(player.blockPosition()).is(RangerLookoutStructure.FORESTS)) {
            sendQuote(player, null, "Rangers answer calls from forest biomes.");
            return;
        }
        if (!confirm) {
            quote(player);
            return;
        }
        Quote quote = quotes.remove(owner);
        if (quote == null || quote.level != level || quote.expires < level.getGameTime()
                || horizontalDistance(player.position(), quote.pickup) > 36) {
            sendQuote(player, null, "Pickup changed. Find a lookout again.");
            return;
        }
        ChunkPos station = new ChunkPos(quote.station);
        if (jobs.size() >= MAX_CALLS || !available(level, station)) {
            sendQuote(player, null, "That ranger is unavailable. Find another lookout.");
            return;
        }
        if (player.getInventory().countItem(Items.EMERALD) < PRICE) {
            sendQuote(player, quote, "You need " + PRICE + " emeralds.");
            quotes.put(owner, quote);
            return;
        }
        for (int slot = 0, remaining = PRICE; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.is(Items.EMERALD)) continue;
            int paid = Math.min(remaining, stack.getCount());
            stack.shrink(paid);
            remaining -= paid;
        }
        player.getInventory().setChanged();
        setFare(player, PRICE);
        jobs.put(owner, new Job(level, station, quote.pickup));
        sendQuote(player, null, "Dispatched. Head to " + quote.pickup.toShortString() + ".");
        tell(player, "Ranger dispatched. Meet them at " + quote.pickup.toShortString() + ".");
        DeliveryManager.syncToPlayer(player);
    }

    private static void quote(ServerPlayer player) {
        UUID owner = player.getUUID();
        quotes.remove(owner);
        ServerLevel level = player.serverLevel();
        BlockPos pickup = outdoorPickup(level, player.blockPosition());
        if (pickup == null) {
            sendQuote(player, null, "Move near open ground at the forest edge.");
            return;
        }
        BlockPos station = TownServices.nearest(level, RangerLookoutStructure.KEY, pickup, RANGE,
                pos -> available(level, new ChunkPos(pos)));
        if (station == null) {
            sendQuote(player, null, "No available ranger within " + RANGE + " blocks.");
            return;
        }
        Quote quote = new Quote(level, station, pickup, level.getGameTime() + 600);
        quotes.put(owner, quote);
        sendQuote(player, quote, "A ranger can meet you outdoors.");
    }

    private static boolean available(ServerLevel level, ChunkPos station) {
        return !LookoutRoster.get(level).lost.contains(station.toLong())
                && jobs.values().stream().noneMatch(job -> job.level == level && job.station.equals(station));
    }

    static void recordLoss(ServerLevel level, BlockPos lookout) {
        LookoutRoster roster = LookoutRoster.get(level);
        if (roster.lost.add(ChunkPos.asLong(lookout))) roster.setDirty();
    }

    private static final class LookoutRoster extends SavedData {
        private final Set<Long> lost = new HashSet<>();

        static LookoutRoster get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(new Factory<>(LookoutRoster::new, LookoutRoster::load),
                    "intertnet_ranger_roster");
        }

        private static LookoutRoster load(CompoundTag tag, HolderLookup.Provider registries) {
            LookoutRoster roster = new LookoutRoster();
            for (long pos : tag.getLongArray("Lost")) roster.lost.add(pos);
            return roster;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putLongArray("Lost", lost.stream().mapToLong(Long::longValue).toArray());
            return tag;
        }
    }

    private static BlockPos outdoorPickup(ServerLevel level, BlockPos near) {
        // A natural surface avoids selecting the roof of a mansion as the meeting point.
        for (int radius = 0; radius <= 28; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    BlockPos column = near.offset(dx, 0, dz);
                    if (!level.isLoaded(column)) continue;
                    BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
                    if (Math.abs(ground.getY() - near.getY()) > 20 || !ForestRanger.canStand(level, ground)) continue;
                    var base = level.getBlockState(ground.below());
                    if (base.is(BlockTags.DIRT) || base.is(Blocks.STONE) || base.is(Blocks.GRAVEL)
                            || base.is(Blocks.MOSS_BLOCK) || base.is(Blocks.SAND)) {
                        return ground;
                    }
                }
            }
        }
        return null;
    }

    private static void sendQuote(ServerPlayer player, Quote quote, String message) {
        int distance = quote == null ? 0 : (int) Math.ceil(horizontalDistance(Vec3.atBottomCenterOf(quote.station), quote.pickup));
        PacketDistributor.sendToPlayer(player, new ModNetwork.RangerQuotePayload(message, distance,
                quote == null ? 0 : 6 + (int) Math.ceil(distance / 4.0), quote == null ? "" : quote.pickup.toShortString()));
    }

    private static void tell(ServerPlayer player, String message) {
        player.displayClientMessage(Component.literal(message), false);
        PacketDistributor.sendToPlayer(player, new ModNetwork.ServiceFeedbackPayload(ModNetwork.RANGER_FEEDBACK, message));
    }

    private static void interact(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getTarget() instanceof ForestRanger ranger)) return;
        event.setCancellationResult(InteractionResult.CONSUME);
        event.setCanceled(true);
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        Job job = jobs.get(player.getUUID());
        if (job != null && job.ranger == ranger && job.phase == Phase.MEETING) {
            PacketDistributor.sendToPlayer(player, new ModNetwork.RangerMeetPayload(ranger.getId()));
        } else if (job != null && job.ranger == ranger && job.phase.ordinal() >= Phase.ESCORTING.ordinal()) {
            player.displayClientMessage(Component.literal("Stay close. I'll take you to the lookout."), true);
        } else {
            player.displayClientMessage(Component.literal("Use the Ranger app to request an escort."), true);
        }
    }

    static void escort(ServerPlayer player, int entityId) {
        Job job = jobs.get(player.getUUID());
        if (job == null || job.phase != Phase.MEETING || job.ranger.getId() != entityId
                || player.serverLevel() != job.level || !player.isAlive() || player.isSpectator()
                || !job.ranger.isAlive() || player.distanceToSqr(job.ranger) > 25) return;
        job.next(Phase.ESCORTING, 180);
        tell(player, "Follow the ranger to the lookout.");
        DeliveryManager.syncToPlayer(player);
    }

    static void addTracking(ServerPlayer player, List<ModNetwork.OrderSnapshot> orders) {
        Job job = jobs.get(player.getUUID());
        if (job != null && job.level == player.serverLevel()) orders.add(job.snapshot(player));
    }

    private static void tick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 == 0) {
            quotes.values().removeIf(quote -> quote.expires < quote.level.getGameTime());
        }
        var iterator = jobs.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            Job job = entry.getValue();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            Result result = player == null || player.serverLevel() != job.level || !player.isAlive() || player.isSpectator()
                    ? Result.FAILED : job.tick(player);
            if (result == Result.ACTIVE) continue;
            iterator.remove();
            job.close(result == Result.COMPLETE);
            if (player != null) {
                if (result == Result.COMPLETE) {
                    setFare(player, 0);
                    tell(player, "You're safe at the lookout. Help yourself to the spare bed and workstations.");
                } else {
                    refund(player);
                    tell(player, job.failure + " Your emeralds were refunded.");
                }
                DeliveryManager.syncToPlayer(player);
            }
        }
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !jobs.containsKey(player.getUUID())) refund(player);
    }

    private static void onStop(ServerStoppingEvent event) {
        for (var entry : jobs.entrySet()) {
            entry.getValue().close(false);
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player != null) refund(player);
        }
        jobs.clear();
        quotes.clear();
    }

    private static void setFare(ServerPlayer player, int amount) {
        CompoundTag data = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (amount == 0) data.remove(FARE);
        else data.putInt(FARE, amount);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, data);
    }

    private static void refund(ServerPlayer player) {
        int paid = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getInt(FARE);
        if (paid <= 0) return;
        setFare(player, 0);
        ItemStack emeralds = new ItemStack(Items.EMERALD, paid);
        if (!player.getInventory().add(emeralds)) player.drop(emeralds, false);
    }

    private static double horizontalDistance(Vec3 from, BlockPos to) {
        return ServiceTrip.horizontalDistance(from, Vec3.atBottomCenterOf(to));
    }

    private static final class Job {
        private final UUID ticketId = UUID.randomUUID();
        private final Set<ChunkPos> tickets = new HashSet<>();
        private final ServerLevel level;
        private final ChunkPos station;
        private final BlockPos pickup;
        private ForestRanger ranger;
        private BlockPos lookout;
        private Phase phase;
        private long deadline;
        private String failure = "The ranger could not finish the escort.";
        private boolean climbed;

        Job(ServerLevel level, ChunkPos station, BlockPos pickup) {
            this.level = level;
            this.station = station;
            this.pickup = pickup;
            next(Phase.LOADING, 30);
            BlockPos source = station.getMiddleBlockPosition(pickup.getY());
            ServiceTrip.pinVehicleChunks(level, ticketId, tickets, source, source, source);
        }

        void next(Phase phase, int seconds) {
            this.phase = phase;
            this.deadline = level.getGameTime() + seconds * 20L;
        }

        Result tick(ServerPlayer player) {
            if (level.getGameTime() >= deadline) {
                failure = phase == Phase.MEETING ? "The ranger waited at the pickup and returned to the lookout."
                        : phase == Phase.SHELTER ? "The ranger waited for you upstairs." : "The ranger ran out of time.";
                return Result.FAILED;
            }
            if (phase == Phase.LOADING) return load();
            if (!ranger.isAlive()) {
                failure = "The ranger was killed.";
                return Result.FAILED;
            }

            BlockPos foot = lookout.offset(RangerLookoutPiece.FOOT);
            BlockPos destination = phase.ordinal() < Phase.ESCORTING.ordinal() ? pickup : foot;
            if (level.getGameTime() % 10 == 0) {
                Vec3 direction = Vec3.atBottomCenterOf(destination).subtract(ranger.position()).multiply(1, 0, 1).normalize();
                BlockPos ahead = BlockPos.containing(ranger.position().add(direction.scale(22)));
                ServiceTrip.pinVehicleChunks(level, ticketId, tickets, ranger.blockPosition(), foot, ahead);
            }
            switch (phase) {
                case DESCENDING -> {
                    if (!ForestRanger.hasLadder(level, lookout)) return brokenLadder();
                    if (ranger.climb(lookout, false)) next(Phase.OUTBOUND, 150);
                }
                case OUTBOUND -> {
                    if (ranger.distanceToSqr(Vec3.atBottomCenterOf(pickup)) <= 9) {
                        ranger.stopWalking();
                        ranger.playSound(SoundEvents.GOAT_HORN_SOUND_VARIANTS.getFirst().value(), 1, 1);
                        next(Phase.MEETING, 90);
                        tell(player, "Your ranger is at the pickup. Right-click them to ask for an escort.");
                    } else {
                        ranger.walkTo(pickup, 1.22);
                    }
                }
                case ESCORTING -> {
                    if (ranger.distanceToSqr(player) > 12 * 12) {
                        ranger.stopWalking();
                    } else if (ranger.distanceToSqr(Vec3.atBottomCenterOf(foot)) < 2.25) {
                        next(Phase.CLIMBING, 35);
                        tell(player, "Follow the ranger up the ladder.");
                    } else {
                        ranger.walkTo(foot, 0.95);
                    }
                }
                case CLIMBING -> {
                    if (!ForestRanger.hasLadder(level, lookout)) return brokenLadder();
                    if (!climbed) climbed = ranger.climb(lookout, true);
                    if (climbed && ranger.enterShelter(lookout)) next(Phase.SHELTER, 120);
                }
                case SHELTER -> {
                    BlockPos room = lookout.offset(RangerLookoutPiece.SHELTER);
                    if (player.getY() >= room.getY() - 0.15 && player.getY() < room.getY() + 4
                            && player.distanceToSqr(ranger) < 16) {
                        ranger.spawnAtLocation(new ItemStack(Items.BAKED_POTATO, 4));
                        return Result.COMPLETE;
                    }
                }
                default -> { }
            }
            return Result.ACTIVE;
        }

        private Result load() {
            var chunk = level.getChunkSource().getChunkNow(station.x, station.z);
            if (chunk == null || !level.areEntitiesLoaded(station.toLong())) return Result.ACTIVE;
            var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getOrThrow(RangerLookoutStructure.KEY);
            var start = chunk.getStartForStructure(structure);
            if (start == null || !start.isValid()) {
                failure = "The lookout is unavailable.";
                return Result.FAILED;
            }
            lookout = new BlockPos(station.getMinBlockX(), start.getBoundingBox().minY(), station.getMinBlockZ());
            if (!ForestRanger.hasLadder(level, lookout)) return brokenLadder();
            var staff = level.getEntitiesOfClass(ForestRanger.class,
                    new AABB(lookout).expandTowards(15, RangerLookoutPiece.HEIGHT, 15),
                    entity -> entity.isAlive() && entity.serves(lookout));
            if (staff.isEmpty()) {
                failure = "There is no ranger on duty at this lookout.";
                return Result.FAILED;
            }
            ranger = staff.getFirst();
            ranger.setAssigned(true);
            boolean upstairs = ranger.getY() > lookout.offset(RangerLookoutPiece.FOOT).getY() + 1.5;
            next(upstairs ? Phase.DESCENDING : Phase.OUTBOUND, upstairs ? 35 : 150);
            return Result.ACTIVE;
        }

        private Result brokenLadder() {
            failure = "The lookout ladder is blocked or broken.";
            return Result.FAILED;
        }

        ModNetwork.OrderSnapshot snapshot(ServerPlayer player) {
            BlockPos target = phase == Phase.LOADING || phase == Phase.OUTBOUND || phase == Phase.DESCENDING
                    ? pickup : ranger.blockPosition();
            int distance = (int) Math.ceil(horizontalDistance(player.position(), target));
            String bearing = distance <= 2 ? "nearby" : Direction.getNearest(target.getX() + 0.5 - player.getX(), 0,
                    target.getZ() + 0.5 - player.getZ()).getName() + " " + distance + "m";
            String status = switch (phase) {
                case LOADING, DESCENDING -> "Ranger departing";
                case OUTBOUND -> "Pickup · " + bearing;
                case MEETING -> "Meet ranger · " + bearing;
                case ESCORTING -> "Follow ranger · " + bearing;
                case CLIMBING, SHELTER -> "Climb to the lookout";
            };
            return new ModNetwork.OrderSnapshot(ORDER_ID, "Forest Ranger", status, distance,
                    phase.ordinal() * 100 / (Phase.values().length - 1), target.toShortString());
        }

        void close(boolean completed) {
            if (ranger != null && ranger.isAlive()) {
                if (completed) ranger.setAssigned(false);
                else ranger.returnToPost();
            }
            ServiceTrip.releaseVehicleChunks(level, ticketId, tickets);
        }
    }
}
