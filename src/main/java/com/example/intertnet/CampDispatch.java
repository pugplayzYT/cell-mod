package com.example.intertnet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
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

final class CampDispatch {
    static final String ORDER_ID = "RESCUE_CAMP";
    static final String BUILDER_TAG = "intertnet_camp_builder";
    static final int PRICE = 8;
    private static final int RANGE = 384;
    private static final String FARE = "IntertnetCampFare";
    private static final Map<UUID, Quote> quotes = new HashMap<>();
    private static final Map<UUID, Job> jobs = new HashMap<>();

    private record Quote(ServerLevel level, BlockPos site, BlockPos station, long expiry) {}
    private enum Phase { LOADING, WALKING, BUILDING, RETURNING }

    static void register() {
        NeoForge.EVENT_BUS.addListener(CampDispatch::tick);
        NeoForge.EVENT_BUS.addListener(CampDispatch::login);
        NeoForge.EVENT_BUS.addListener(CampDispatch::stop);
    }

    static void request(ServerPlayer player, boolean confirm) {
        if (!player.isAlive() || player.isSpectator()) return;
        UUID owner = player.getUUID();
        ServerLevel level = player.serverLevel();
        if (jobs.containsKey(owner)) {
            quoteTo(player, null, "You already have a camp crew assigned.");
            return;
        }
        if (SignalManager.getBestSignal(player, DeliveryManager.findPlayerSimNetwork(player)).bars() <= 0) {
            quoteTo(player, null, "Rescue Camp requires phone reception.");
            return;
        }
        Quote quote = quotes.get(owner);
        if (!confirm) {
            if (valid(player, quote)) {
                quoteTo(player, quote, "Crew available. Confirm the site to order.");
                return;
            }
            quotes.remove(owner);
            BlockPos site = RescueCamp.findSite(level, player.blockPosition());
            if (site == null) {
                quoteTo(player, null, "Find a flat clearing with room for a camp.");
                return;
            }
            BlockPos station = TownServices.nearest(level, RangerLookoutStructure.KEY, site, RANGE);
            if (station == null) {
                quoteTo(player, null, "No ranger lookout within " + RANGE + " blocks.");
                return;
            }
            Quote next = new Quote(level, site, station, level.getGameTime() + 600);
            quotes.put(owner, next);
            quoteTo(player, next, "Crew available. Confirm the site to order.");
            return;
        }
        if (!valid(player, quote) || !RescueCamp.fits(level, quote.site)
                || jobs.values().stream().anyMatch(job -> job.level == level && overlaps(job.site, quote.site))) {
            quotes.remove(owner);
            quoteTo(player, null, "The site is no longer available. Find a new clearing.");
            return;
        }
        if (jobs.size() >= 6) {
            quoteTo(player, quote, "Camp crews are busy. Try again shortly.");
            return;
        }
        if (!ServicePayment.takeEmeralds(player, PRICE)) {
            quoteTo(player, quote, "You need " + PRICE + " emeralds.");
            return;
        }
        quotes.remove(owner);
        setFare(player, PRICE);
        jobs.put(owner, new Job(quote));
        quoteTo(player, null, "Crew dispatched to " + quote.site.toShortString() + ".");
        tell(player, "Camp crew dispatched from the ranger lookout.");
        DeliveryManager.syncToPlayer(player);
    }

    private static boolean overlaps(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) <= 6 && Math.abs(a.getZ() - b.getZ()) <= 8
                && Math.abs(a.getY() - b.getY()) <= 4;
    }

    private static boolean valid(ServerPlayer player, Quote quote) {
        return quote != null && quote.level == player.serverLevel() && quote.expiry >= quote.level.getGameTime()
                && player.blockPosition().distSqr(quote.site) <= 44 * 44;
    }

    private static void quoteTo(ServerPlayer player, Quote quote, String text) {
        int seconds = quote == null ? 0 : 15 + (int) Math.ceil(distance(Vec3.atBottomCenterOf(quote.station), quote.site) / 4.0);
        PacketDistributor.sendToPlayer(player, new ModNetwork.CampQuotePayload(text, seconds,
                quote == null ? "" : quote.site.toShortString()));
    }

    private static void tell(ServerPlayer player, String message) {
        player.displayClientMessage(Component.literal(message), false);
        PacketDistributor.sendToPlayer(player, new ModNetwork.ServiceFeedbackPayload(ModNetwork.CAMP_FEEDBACK, message));
    }

    static void addTracking(ServerPlayer player, List<ModNetwork.OrderSnapshot> orders) {
        Job job = jobs.get(player.getUUID());
        if (job != null && job.level == player.serverLevel() && job.phase != Phase.RETURNING) {
            orders.add(job.snapshot());
        }
    }

    private static void tick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 == 0) {
            quotes.values().removeIf(quote -> quote.expiry < quote.level.getGameTime());
        }
        var iterator = jobs.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            Job job = entry.getValue();
            ServerPlayer owner = event.getServer().getPlayerList().getPlayer(entry.getKey());
            boolean active = job.phase == Phase.RETURNING
                    || (owner != null && owner.isAlive() && owner.serverLevel() == job.level);
            if (active && job.tick(owner)) continue;
            iterator.remove();
            job.close();
            if (owner != null && job.phase != Phase.RETURNING) {
                refund(owner);
                tell(owner, job.failure + " Your emeralds were refunded.");
                DeliveryManager.syncToPlayer(owner);
            }
        }
    }

    private static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !jobs.containsKey(player.getUUID())) refund(player);
    }

    private static void stop(ServerStoppingEvent event) {
        for (var entry : jobs.entrySet()) {
            Job job = entry.getValue();
            job.close();
            ServerPlayer owner = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (owner != null && job.phase != Phase.RETURNING) refund(owner);
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
        ServicePayment.give(player, new ItemStack(Items.EMERALD, paid));
    }

    private static double distance(Vec3 from, BlockPos to) {
        return ServiceTrip.horizontalDistance(from, Vec3.atBottomCenterOf(to));
    }

    private static final class Job {
        private final UUID ticket = UUID.randomUUID();
        private final Set<ChunkPos> tickets = new HashSet<>();
        private final List<ForestRanger> crew = new ArrayList<>();
        private final ServerLevel level;
        private final BlockPos site;
        private final ChunkPos station;
        private final RescueCamp camp;
        private BlockPos home;
        private final BlockPos arrival;
        private Phase phase = Phase.LOADING;
        private long deadline;
        private String failure = "The crew couldn't finish the camp.";

        Job(Quote quote) {
            level = quote.level;
            site = quote.site;
            station = new ChunkPos(quote.station);
            BlockPos approach = site.offset(0, 0, 4);
            BlockPos ground = ServiceTrip.safeSurface(level, approach, 2, 2);
            arrival = ground == null ? approach : ground;
            camp = new RescueCamp(site);
            deadline = level.getGameTime() + 600;
            pin(quote.station, quote.station, quote.station);
        }

        boolean tick(ServerPlayer owner) {
            if (level.getGameTime() >= deadline) return false;
            if (phase == Phase.LOADING) return deploy();
            crew.removeIf(builder -> !builder.isAlive());
            if (crew.isEmpty()) {
                failure = "The camp crew couldn't reach the site.";
                return false;
            }
            if (phase == Phase.BUILDING) {
                if (level.getGameTime() % 3 != 0) return true;
                switch (camp.buildNext(level)) {
                    case BLOCKED -> {
                        failure = "Something blocked the camp construction.";
                        return false;
                    }
                    case OCCUPIED -> {
                        if (level.getGameTime() % 60 == 0) {
                            owner.displayClientMessage(Component.literal("Step clear of the camp so the crew can build."), true);
                        }
                    }
                    case COMPLETE -> {
                        phase = Phase.RETURNING;
                        deadline = level.getGameTime() + 20 * 150;
                        setFare(owner, 0);
                        tell(owner, "Camp ready at " + site.toShortString() + ". Demolition uses live TNT. Stand clear!");
                        DeliveryManager.syncToPlayer(owner);
                    }
                    case PLACED -> {
                        ForestRanger builder = crew.get((camp.progress() / 2) % crew.size());
                        builder.getLookControl().setLookAt(site.getX() + 0.5, site.getY() + 1.0, site.getZ() + 0.5);
                        builder.swing(InteractionHand.MAIN_HAND);
                    }
                }
                return true;
            }
            BlockPos target = phase == Phase.RETURNING ? home : arrival;
            ForestRanger lead = crew.getFirst();
            if (level.getGameTime() % 10 == 0) {
                Vec3 forward = Vec3.atBottomCenterOf(target).subtract(lead.position()).multiply(1, 0, 1).normalize();
                pin(lead.blockPosition(), crew.getLast().blockPosition(), BlockPos.containing(lead.position().add(forward.scale(20))));
            }
            if (crew.stream().allMatch(builder -> distance(builder.position(), target) < 3.0
                    && Math.abs(builder.getY() - target.getY()) < 3)) {
                if (phase == Phase.RETURNING) return false;
                if (!camp.prepare(level)) {
                    failure = "The clearing changed before the crew arrived.";
                    return false;
                }
                crew.forEach(ForestRanger::stopWalking);
                phase = Phase.BUILDING;
                deadline = level.getGameTime() + 20 * 90;
                tell(owner, "The crew is building. Keep the site clear.");
            } else {
                for (ForestRanger builder : crew) builder.walkTo(target, 1.18);
            }
            return true;
        }

        private boolean deploy() {
            var chunk = level.getChunkSource().getChunkNow(station.x, station.z);
            if (chunk == null) return true;
            var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getOrThrow(RangerLookoutStructure.KEY);
            var start = chunk.getStartForStructure(structure);
            if (start == null || !start.isValid()) return false;
            home = new BlockPos(station.getMinBlockX() + 8, start.getBoundingBox().minY() + 1, station.getMinBlockZ() + 13);
            for (int offset : new int[]{-1, 1}) {
                BlockPos pos = home.offset(offset, 0, 0);
                if (!ForestRanger.canStand(level, pos)) return false;
                ForestRanger builder = IntertnetMod.FOREST_RANGER.get().create(level);
                if (builder == null) return false;
                builder.addTag(BUILDER_TAG);
                builder.setCustomName(Component.literal("Camp Builder"));
                builder.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                builder.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
                if (!level.addFreshEntity(builder)) return false;
                crew.add(builder);
            }
            phase = Phase.WALKING;
            deadline = level.getGameTime() + 20 * 180;
            return true;
        }

        private void pin(BlockPos center, BlockPos tail, BlockPos ahead) {
            ServiceTrip.pinVehicleChunks(level, ticket, tickets, center, tail, ahead);
        }

        ModNetwork.OrderSnapshot snapshot() {
            int meters = crew.isEmpty() ? 0 : (int) Math.ceil(distance(crew.getFirst().position(), arrival));
            String status = switch (phase) {
                case LOADING -> "Camp crew preparing";
                case WALKING -> "Camp crew · " + meters + "m";
                case BUILDING -> "Building camp · " + camp.progress() + "%";
                case RETURNING -> "Camp ready";
            };
            return new ModNetwork.OrderSnapshot(ORDER_ID, "Rescue Camp", status, meters, camp.progress(), site.toShortString());
        }

        void close() {
            if (phase != Phase.RETURNING) camp.rollback(level);
            crew.forEach(ForestRanger::discard);
            ServiceTrip.releaseVehicleChunks(level, ticket, tickets);
        }
    }
}
