package com.example.intertnet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class TownServices {
    static final int MEAL_SIZE = 3;
    private static final int DELIVERY_FEE = 1;
    private static final int MAX_DELIVERY_DISTANCE = 1536;
    private static final int MAX_RIDE_DISTANCE = 6000;
    private static final int MAX_ACTIVE_JOBS = 8;
    static final ResourceKey<Structure> MANSION =
            ResourceKey.create(Registries.STRUCTURE, net.minecraft.resources.ResourceLocation.withDefaultNamespace("mansion"));
    static final String MANSION_LABEL = "Woodland Mansion";
    private static final Map<JobKey, Job> jobs = new HashMap<>();
    private static final Map<UUID, EnumMap<Mode, Quotes>> quoteCache = new HashMap<>();
    private static int nextQuoteId;

    enum Mode {
        DINING("Restaurant"), DELIVERY("Delivery"), RIDESHARE("Rideshare");

        final String label;

        Mode(String label) {
            this.label = label;
        }

        Slot slot() {
            return this == RIDESHARE ? Slot.RIDE : Slot.FOOD;
        }
    }

    private enum Slot {
        FOOD("IntertnetServicePayment"), RIDE("IntertnetRidePayment");

        final String paymentKey;

        Slot(String paymentKey) {
            this.paymentKey = paymentKey;
        }
    }

    private record JobKey(UUID owner, Slot slot) {}

    record Choice(int id, String name, int distance, int price) {}
    private record Offer(Choice choice, BlockPos destination) {}
    private record SiteCandidate(Structure structure, RandomSpreadStructurePlacement placement, ChunkPos chunk,
                                 BlockPos position, double distanceSquared) {}
    private record Quotes(int id, ResourceKey<Level> dimension, BlockPos playerPos, long issued,
                          BlockPos source, List<Offer> offers, String message) {}
    private enum Stage { LOADING, TO_KITCHEN, COOKING, TO_COUNTER, TRAVELLING }

    private static final class Job {
        final Mode mode;
        final ServerLevel level;
        final ChunkPos source;
        final BlockPos pickup;
        final Meal meal;
        final ServiceTrip trip;
        final long deadline;
        BlockPos origin;
        Villager chef;
        Stage stage = Stage.LOADING;
        boolean kitchenInUse;
        ItemStack prepared = ItemStack.EMPTY;

        Job(Mode mode, ServerLevel level, BlockPos source, BlockPos pickup, Meal meal, ServiceTrip trip) {
            this.mode = mode;
            this.level = level;
            this.source = new ChunkPos(source);
            this.pickup = pickup;
            this.meal = meal;
            this.trip = trip;
            this.deadline = level.getGameTime() + 20 * (mode == Mode.RIDESHARE ? 16 * 60 : 7 * 60);
        }

        String status() {
            return switch (stage) {
                case LOADING -> "Preparing " + (mode == Mode.RIDESHARE ? "dispatch" : "kitchen");
                case TO_KITCHEN -> "Chef is fetching ingredients";
                case COOKING -> "Your meal is cooking";
                case TO_COUNTER -> "Meal ready at the counter";
                case TRAVELLING -> trip.status();
            };
        }
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(TownServices::onTick);
        NeoForge.EVENT_BUS.addListener(TownServices::onInteract);
        NeoForge.EVENT_BUS.addListener(TownServices::onKitchenUse);
        NeoForge.EVENT_BUS.addListener(TownServices::onKitchenBreak);
        NeoForge.EVENT_BUS.addListener(TownServices::onKitchenExplosion);
        NeoForge.EVENT_BUS.addListener(TownServices::onEntityLoad);
        NeoForge.EVENT_BUS.addListener(TownServices::onLogin);
        NeoForge.EVENT_BUS.addListener(TownServices::onLogout);
        NeoForge.EVENT_BUS.addListener(TownServices::onClone);
        NeoForge.EVENT_BUS.addListener(TownServices::onStop);
    }

    private static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        var target = event.getTarget();
        if (target.getTags().contains(ServiceTrip.VEHICLE_TAG)) {
            Slot slot = target instanceof net.minecraft.world.entity.vehicle.MinecartChest ? Slot.FOOD : Slot.RIDE;
            Job job = jobs.get(new JobKey(player.getUUID(), slot));
            if (job == null || job.stage != Stage.TRAVELLING || !job.trip.allowsInteraction(target)) {
                event.setCancellationResult(InteractionResult.CONSUME);
                event.setCanceled(true);
            } else if (job.mode == Mode.DELIVERY) {
                event.setCancellationResult(InteractionResult.CONSUME);
                event.setCanceled(true);
                if (event.getHand() == InteractionHand.MAIN_HAND) {
                    job.trip.openDeliveryChest(player);
                }
            }
        } else if (target instanceof Villager chef && chef.getTags().contains(ServiceSitePiece.CHEF_TAG)) {
            event.setCancellationResult(InteractionResult.CONSUME);
            event.setCanceled(true);
            if (event.getHand() == InteractionHand.MAIN_HAND) {
                PacketDistributor.sendToPlayer(player, new ModNetwork.ServiceMenuPayload(chef.getId()));
            }
        }
    }

    private static boolean secureKitchen(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        var entity = level.getBlockEntity(pos);
        return entity != null && entity.getPersistentData().getBoolean(ServiceSitePiece.KITCHEN_TAG);
    }

    private static void onKitchenUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos pos = event.getPos();
        if (event.getHand() == InteractionHand.MAIN_HAND && !player.isShiftKeyDown()
                && level.getBlockState(pos).is(Blocks.SPRUCE_STAIRS)) {
            var seats = level.getEntities(IntertnetMod.DINING_SEAT.get(), new AABB(pos),
                    seat -> seat.isAlive() && seat.blockPosition().equals(pos));
            if (!seats.isEmpty()) {
                DiningSeat seat = seats.getFirst();
                if (!seat.isVehicle()) {
                    player.startRiding(seat);
                }
                event.setCancellationResult(InteractionResult.CONSUME);
                event.setCanceled(true);
                return;
            }
        }
        if (secureKitchen(level, pos)) {
            event.setCancellationResult(InteractionResult.CONSUME);
            event.setCanceled(true);
            tell(player, "Staff only. Order from the chef at the counter.");
        }
    }

    private static void onKitchenBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && !player.isCreative()
                && secureKitchen(player.serverLevel(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    private static void onKitchenExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level) {
            event.getAffectedBlocks().removeIf(pos -> secureKitchen(level, pos));
        }
    }

    private static void onEntityLoad(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() && !event.getLevel().isClientSide
                && event.getEntity().getTags().contains(ServiceTrip.VEHICLE_TAG)) {
            event.setCanceled(true);
        }
    }

    static void request(ServerPlayer player, int modeId, boolean quoteOnly, int option, int reference) {
        if (modeId < 0 || modeId >= Mode.values().length || player.isSpectator() || !player.isAlive()) {
            return;
        }
        Mode mode = Mode.values()[modeId];
        if (mode == Mode.DINING) {
            if (!quoteOnly && option >= 0 && option < Meal.values().length
                    && player.serverLevel().getEntity(reference) instanceof Villager chef
                    && chef.getTags().contains(ServiceSitePiece.CHEF_TAG) && player.distanceToSqr(chef) <= 36) {
                BlockPos origin = BlockPos.of(chef.getPersistentData().getLong(ServiceSitePiece.SITE_POS));
                Meal meal = Meal.values()[option];
                begin(player, mode, origin, player.blockPosition(), meal, null, meal.price, "");
            } else if (!quoteOnly) {
                feedback(player, mode, "Move closer to the chef to order.");
            }
            return;
        }
        String sim = DeliveryManager.findPlayerSimNetwork(player);
        var signal = SignalManager.getBestSignal(player, sim);
        if (signal.bars() <= 0) {
            String message = sim == null ? "Insert a SIM to connect." : "No reception. Move into tower range.";
            if (quoteOnly) {
                sendQuotes(player, mode, 0, List.of(), message);
            } else {
                feedback(player, mode, message);
            }
            return;
        }
        if (quoteOnly) {
            quote(player, mode);
            return;
        }
        EnumMap<Mode, Quotes> cache = quoteCache.get(player.getUUID());
        Quotes quotes = cache == null ? null : cache.get(mode);
        if (quotes == null || quotes.id != reference || !validQuote(player, quotes)) {
            quote(player, mode);
            feedback(player, mode, "Quote refreshed. Select an option again.");
            return;
        }
        Offer selected = quotes.offers.stream().filter(offer -> offer.choice.id == option).findFirst().orElse(null);
        if (selected == null || quotes.source == null) {
            return;
        }
        BlockPos pickup = ServiceTrip.safeSurface(player.serverLevel(), player.blockPosition(), 12, 3);
        if (pickup == null) {
            feedback(player, mode, "Move to an open spot at ground level for pickup.");
            return;
        }
        Meal meal = mode == Mode.DELIVERY ? Meal.values()[option] : null;
        begin(player, mode, quotes.source, pickup, meal, selected.destination, selected.choice.price,
                selected.choice.name);
    }

    private static void quote(ServerPlayer player, Mode mode) {
        var cached = quoteCache.computeIfAbsent(player.getUUID(), id -> new EnumMap<>(Mode.class));
        Quotes old = cached.get(mode);
        if (old != null && validQuote(player, old)) {
            sendQuotes(player, mode, old.id, old.offers, old.message);
            return;
        }
        ServerLevel level = player.serverLevel();
        if (old != null && old.dimension.equals(level.dimension()) && level.getGameTime() - old.issued < 80) {
            sendQuotes(player, mode, 0, List.of(), "Wait a moment before refreshing.");
            return;
        }
        BlockPos pickup = player.blockPosition();
        BlockPos source = nearest(level, mode == Mode.DELIVERY ? ServiceSiteStructure.RESTAURANT : ServiceSiteStructure.RIDESHARE,
                pickup, MAX_DELIVERY_DISTANCE);
        List<Offer> offers = new ArrayList<>();
        String message;
        if (source == null) {
            message = mode == Mode.DELIVERY ? "No restaurant within delivery range." : "No rideshare center nearby.";
        } else if (mode == Mode.DELIVERY) {
            int distance = distance(pickup, source);
            for (Meal meal : Meal.values()) {
                offers.add(new Offer(new Choice(meal.ordinal(), meal.label, distance, meal.price + DELIVERY_FEE), null));
            }
            message = "Nearest kitchen · " + distance + " blocks";
        } else {
            var villages = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getTag(StructureTags.VILLAGE);
            villages.ifPresent(tag -> addRide(offers, 0, "Village", pickup, nearest(level, tag, pickup, MAX_RIDE_DISTANCE)));
            addRide(offers, 1, "Restaurant", pickup, nearest(level, ServiceSiteStructure.RESTAURANT, pickup, MAX_RIDE_DISTANCE));
            addRide(offers, 2, MANSION_LABEL, pickup, nearest(level, MANSION, pickup, MAX_RIDE_DISTANCE));
            message = offers.isEmpty() ? "No destinations within range." : "Choose a destination";
        }
        Quotes quotes = new Quotes(++nextQuoteId, level.dimension(), pickup, level.getGameTime(), source, List.copyOf(offers), message);
        cached.put(mode, quotes);
        sendQuotes(player, mode, quotes.id, quotes.offers, message);
    }

    private static boolean validQuote(ServerPlayer player, Quotes quotes) {
        return player.serverLevel().dimension().equals(quotes.dimension)
                && player.serverLevel().getGameTime() - quotes.issued < 20 * 30
                && distance(player.blockPosition(), quotes.playerPos) <= 24;
    }

    private static void sendQuotes(ServerPlayer player, Mode mode, int id, List<Offer> offers, String message) {
        PacketDistributor.sendToPlayer(player, new ModNetwork.ServiceQuotesPayload(mode.ordinal(), id, message,
                offers.stream().map(Offer::choice).toList()));
    }

    private static void addRide(List<Offer> offers, int id, String name, BlockPos from, BlockPos to) {
        if (to != null) {
            int distance = distance(from, to);
            if (distance <= MAX_RIDE_DISTANCE) {
                offers.add(new Offer(new Choice(id, name, distance, 2 + (distance + 159) / 160), to));
            }
        }
    }

    private static BlockPos nearest(ServerLevel level, ResourceKey<Structure> key, BlockPos from, int radius) {
        var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolder(key);
        if (structure.isEmpty()) {
            return null;
        }
        return nearest(level, HolderSet.direct(structure.get()), from, radius);
    }

    private static BlockPos nearest(ServerLevel level, HolderSet<Structure> targets, BlockPos from, int radius) {
        var generatorState = level.getChunkSource().getGeneratorState();
        long seed = generatorState.getLevelSeed();
        List<SiteCandidate> candidates = new ArrayList<>();
        for (Holder<Structure> target : targets) {
            for (var placement : generatorState.getPlacementsForStructure(target)) {
                if (!(placement instanceof RandomSpreadStructurePlacement grid)) {
                    continue;
                }
                int spacing = grid.spacing();
                int minX = Math.floorDiv(SectionPos.blockToSectionCoord(from.getX() - radius), spacing);
                int maxX = Math.floorDiv(SectionPos.blockToSectionCoord(from.getX() + radius), spacing);
                int minZ = Math.floorDiv(SectionPos.blockToSectionCoord(from.getZ() - radius), spacing);
                int maxZ = Math.floorDiv(SectionPos.blockToSectionCoord(from.getZ() + radius), spacing);
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        ChunkPos chunk = grid.getPotentialStructureChunk(seed, x * spacing, z * spacing);
                        BlockPos pos = grid.getLocatePos(chunk);
                        double dx = pos.getX() - from.getX();
                        double dz = pos.getZ() - from.getZ();
                        double squared = dx * dx + dz * dz;
                        if (squared <= (double) radius * radius && level.getWorldBorder().isWithinBounds(pos)) {
                            candidates.add(new SiteCandidate(target.value(), grid, chunk, pos, squared));
                        }
                    }
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(SiteCandidate::distanceSquared));
        for (SiteCandidate candidate : candidates) {
            ChunkPos chunk = candidate.chunk;
            if (!candidate.placement.isStructureChunk(generatorState, chunk.x, chunk.z)) {
                continue;
            }
            var presence = level.structureManager().checkStructurePresence(chunk, candidate.structure, candidate.placement, false);
            if (presence == StructureCheckResult.START_PRESENT) {
                return candidate.position;
            }
            if (presence == StructureCheckResult.CHUNK_LOAD_NEEDED) {
                var start = level.getChunk(chunk.x, chunk.z, ChunkStatus.STRUCTURE_STARTS).getStartForStructure(candidate.structure);
                if (start != null && start.isValid()) {
                    return candidate.position;
                }
            }
        }
        return null;
    }

    private static int distance(BlockPos from, BlockPos to) {
        return (int) Math.ceil(Math.hypot(from.getX() - to.getX(), from.getZ() - to.getZ()));
    }

    private static boolean kitchenBusy(ServerLevel level, ChunkPos source) {
        return jobs.values().stream().anyMatch(job -> job.mode != Mode.RIDESHARE && job.level == level
                && job.source.equals(source) && job.stage != Stage.TRAVELLING);
    }

    private static void begin(ServerPlayer player, Mode mode, BlockPos source, BlockPos pickup, Meal meal,
                              BlockPos destination, int price, String destinationLabel) {
        ServerLevel level = player.serverLevel();
        JobKey key = new JobKey(player.getUUID(), mode.slot());
        if (jobs.containsKey(key)) {
            feedback(player, mode, mode == Mode.RIDESHARE ? "You already have a ride booked." : "You already have a food order.");
            return;
        }
        if (jobs.size() >= MAX_ACTIVE_JOBS) {
            feedback(player, mode, "All services are busy. Try again shortly.");
            return;
        }
        if (mode != Mode.RIDESHARE && kitchenBusy(level, new ChunkPos(source))) {
            feedback(player, mode, "The chef is already making an order.");
            return;
        }
        if (mode == Mode.RIDESHARE && jobs.values().stream().anyMatch(job -> job.mode == Mode.RIDESHARE && job.level == level
                && job.source.equals(new ChunkPos(source)) && job.trip.usesStation())) {
            feedback(player, mode, "A cart is leaving this station. Try again in a moment.");
            return;
        }
        if (player.getInventory().countItem(Items.EMERALD) < price) {
            feedback(player, mode, "You need " + price + " emeralds.");
            return;
        }
        int remaining = price;
        for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(Items.EMERALD)) {
                int count = Math.min(remaining, stack.getCount());
                stack.shrink(count);
                remaining -= count;
            }
        }
        player.getInventory().setChanged();
        setPayment(player, key.slot, price);
        ServiceTrip trip = new ServiceTrip(level, source, pickup, destination, destinationLabel, mode == Mode.DELIVERY);
        Job job = new Job(mode, level, source, pickup, meal, trip);
        if (mode == Mode.DINING) {
            job.origin = source;
        }
        jobs.put(key, job);
        feedback(player, mode, mode == Mode.RIDESHARE ? "Ride booked. Stay near your pickup point." : "Order received.");
        DeliveryManager.syncToPlayer(player);
    }

    private static void onTick(ServerTickEvent.Post event) {
        var iter = jobs.entrySet().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            Job job = entry.getValue();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey().owner);
            if (player == null) {
                closeJob(job);
                iter.remove();
                continue;
            }
            ServiceTrip.Result result = player.serverLevel() != job.level || !player.isAlive()
                    || job.level.getGameTime() > job.deadline ? ServiceTrip.Result.FAILED : tickJob(job, player);
            if (result != ServiceTrip.Result.ACTIVE) {
                closeJob(job);
                iter.remove();
                settle(player, entry.getKey().slot, result != ServiceTrip.Result.FAILED);
                feedback(player, job.mode, switch (result) {
                    case COMPLETED -> job.mode == Mode.RIDESHARE ? "You have arrived." : "Enjoy your meal!";
                    case ABANDONED -> "Ride ended early.";
                    default -> "Service cancelled. Your emeralds were refunded.";
                });
            }
        }
    }

    private static ServiceTrip.Result tickJob(Job job, ServerPlayer player) {
        job.trip.loadCorridor();
        if (job.stage == Stage.TRAVELLING) {
            return job.trip.tick(player);
        }
        if (job.stage == Stage.LOADING) {
            var chunk = job.level.getChunkSource().getChunkNow(job.source.x, job.source.z);
            if (chunk == null) {
                return ServiceTrip.Result.ACTIVE;
            }
            if (job.origin == null) {
                ResourceKey<Structure> key = job.mode == Mode.RIDESHARE ? ServiceSiteStructure.RIDESHARE : ServiceSiteStructure.RESTAURANT;
                var structure = job.level.registryAccess().registryOrThrow(Registries.STRUCTURE).getOrThrow(key);
                var start = chunk.getStartForStructure(structure);
                if (start == null || !start.isValid()) {
                    return ServiceTrip.Result.FAILED;
                }
                job.origin = new BlockPos(job.source.getMinBlockX(), start.getBoundingBox().minY(), job.source.getMinBlockZ());
            }
            if (job.mode == Mode.RIDESHARE) {
                if (!job.trip.dispatch(job.origin.offset(ServiceSitePiece.PARKING), job.origin, ItemStack.EMPTY)) {
                    return ServiceTrip.Result.FAILED;
                }
                job.stage = Stage.TRAVELLING;
                return ServiceTrip.Result.ACTIVE;
            }
            List<Villager> chefs = job.level.getEntities(EntityType.VILLAGER, new AABB(job.origin).expandTowards(16, 8, 16),
                    chef -> chef.getTags().contains(ServiceSitePiece.CHEF_TAG)
                            && chef.getPersistentData().getLong(ServiceSitePiece.SITE_POS) == job.origin.asLong());
            if (chefs.isEmpty()) {
                return ServiceTrip.Result.ACTIVE;
            }
            job.chef = chefs.getFirst();
            job.stage = Stage.TO_KITCHEN;
        }
        return cook(job, player);
    }

    private static ServiceTrip.Result cook(Job job, ServerPlayer player) {
        if (!job.chef.isAlive() || !(job.level.getBlockEntity(job.origin.offset(ServiceSitePiece.OVEN))
                instanceof AbstractFurnaceBlockEntity oven)) {
            return ServiceTrip.Result.FAILED;
        }
        BlockPos door = job.origin.offset(ServiceSitePiece.KITCHEN_DOOR);
        ServiceSitePiece.setIronDoor(job.level, door, Math.abs(job.chef.getZ() - (door.getZ() + 0.5)) < 1.25);
        if (job.stage == Stage.TO_KITCHEN && walkChef(job.chef, job.origin.offset(ServiceSitePiece.KITCHEN))) {
            if (!(job.level.getBlockEntity(job.origin.offset(ServiceSitePiece.STOCK)) instanceof BaseContainerBlockEntity stock)) {
                return ServiceTrip.Result.FAILED;
            }
            int slot = job.meal.ordinal();
            if (!stock.getItem(slot).is(job.meal.raw) || stock.getItem(slot).getCount() < MEAL_SIZE) {
                stock.setItem(slot, new ItemStack(job.meal.raw, 32));
            }
            oven.setItem(2, ItemStack.EMPTY);
            oven.setItem(0, stock.removeItem(slot, MEAL_SIZE));
            if (oven.getItem(1).isEmpty()) {
                oven.setItem(1, new ItemStack(Items.COAL));
            }
            job.kitchenInUse = true;
            job.stage = Stage.COOKING;
        } else if (job.stage == Stage.COOKING && oven.getItem(2).is(job.meal.cooked)
                && oven.getItem(2).getCount() >= MEAL_SIZE) {
            job.prepared = oven.removeItem(2, MEAL_SIZE);
            job.chef.setItemInHand(InteractionHand.MAIN_HAND, job.prepared.copy());
            job.kitchenInUse = false;
            job.stage = Stage.TO_COUNTER;
        } else if (job.stage == Stage.TO_COUNTER) {
            if (job.mode == Mode.DINING) {
                if (!serveDiner(job, player)) {
                    return ServiceTrip.Result.ACTIVE;
                }
                give(player, job.prepared);
                return ServiceTrip.Result.COMPLETED;
            }
            if (!walkChef(job.chef, job.origin.offset(ServiceSitePiece.CHEF))) {
                return ServiceTrip.Result.ACTIVE;
            }
            job.chef.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            if (!job.trip.dispatch(job.origin.offset(ServiceSitePiece.ENTRANCE), null, job.prepared)) {
                return ServiceTrip.Result.FAILED;
            }
            job.stage = Stage.TRAVELLING;
        }
        return ServiceTrip.Result.ACTIVE;
    }

    private static boolean serveDiner(Job job, ServerPlayer player) {
        BlockPos aisle = job.origin.offset(7, 1, 10);
        if (job.chef.getZ() < aisle.getZ() + 0.35 && !walkChef(job.chef, aisle)) {
            return false;
        }
        int dinerX = player.getBlockX() - job.origin.getX();
        int dinerZ = player.getBlockZ() - job.origin.getZ();
        if (dinerX < 2 || dinerX > 12 || dinerZ < 6 || dinerZ > 12 || Math.abs(player.getBlockY() - aisle.getY()) > 2) {
            return false;
        }
        int serverX = Math.clamp(dinerX, 5, 9);
        return walkChef(job.chef, job.origin.offset(serverX, 1, 10)) && player.distanceToSqr(job.chef) < 25;
    }

    private static boolean walkChef(Villager chef, BlockPos to) {
        Vec3 target = Vec3.atBottomCenterOf(to);
        Vec3 difference = target.subtract(chef.position());
        if (difference.lengthSqr() <= 0.025) {
            return true;
        }
        Vec3 step = chef.position().add(difference.normalize().scale(0.12));
        chef.moveTo(step.x, step.y, step.z, difference.z < 0 ? 180 : 0, 0);
        return false;
    }

    private static void closeJob(Job job) {
        if (job.kitchenInUse && job.level.getBlockEntity(job.origin.offset(ServiceSitePiece.OVEN))
                instanceof AbstractFurnaceBlockEntity oven) {
            oven.setItem(0, ItemStack.EMPTY);
            oven.setItem(2, ItemStack.EMPTY);
        }
        if (job.chef != null && job.stage != Stage.TRAVELLING && job.chef.isAlive()) {
            job.chef.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            Vec3 counter = Vec3.atBottomCenterOf(job.origin.offset(ServiceSitePiece.CHEF));
            job.chef.moveTo(counter.x, counter.y, counter.z, 0, 0);
        }
        if (job.mode != Mode.RIDESHARE && job.stage != Stage.TRAVELLING && job.origin != null && job.level.isLoaded(job.origin)) {
            ServiceSitePiece.setIronDoor(job.level, job.origin.offset(ServiceSitePiece.KITCHEN_DOOR), false);
        }
        job.trip.close();
    }

    static void addTracking(ServerPlayer player, List<ModNetwork.OrderSnapshot> orders) {
        for (Slot slot : Slot.values()) {
            Job job = jobs.get(new JobKey(player.getUUID(), slot));
            if (job == null) {
                continue;
            }
            int remaining = job.stage == Stage.TRAVELLING ? job.trip.remainingMeters() : 0;
            int progress = job.stage == Stage.TRAVELLING ? job.trip.progress() : 0;
            String coords = job.mode == Mode.DINING ? "Dining room" : job.trip.trackingPoint();
            orders.add(new ModNetwork.OrderSnapshot(job.mode.name(), job.mode.label, job.status(), remaining, progress, coords));
        }
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            for (Slot slot : Slot.values()) {
                settle(player, slot, false);
            }
        }
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            quoteCache.remove(player.getUUID());
            for (Slot slot : Slot.values()) {
                Job job = jobs.remove(new JobKey(player.getUUID(), slot));
                if (job != null) {
                    closeJob(job);
                    settle(player, slot, false);
                }
            }
        }
    }

    private static void onClone(PlayerEvent.Clone event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            for (Slot slot : Slot.values()) {
                int payment = event.getOriginal().getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getInt(slot.paymentKey);
                if (payment > 0) {
                    setPayment(player, slot, payment);
                    if (!jobs.containsKey(new JobKey(player.getUUID(), slot))) {
                        settle(player, slot, false);
                    }
                }
            }
        }
    }

    private static void onStop(ServerStoppingEvent event) {
        for (var entry : jobs.entrySet()) {
            closeJob(entry.getValue());
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey().owner);
            if (player != null) {
                settle(player, entry.getKey().slot, false);
            }
        }
        jobs.clear();
        quoteCache.clear();
    }

    private static void setPayment(ServerPlayer player, Slot slot, int value) {
        var persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        persisted.putInt(slot.paymentKey, value);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    private static void settle(ServerPlayer player, Slot slot, boolean completed) {
        int paid = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getInt(slot.paymentKey);
        setPayment(player, slot, 0);
        if (!completed && paid > 0) {
            give(player, new ItemStack(Items.EMERALD, paid));
        }
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static void tell(ServerPlayer player, String message) {
        player.displayClientMessage(Component.literal(message), false);
    }

    private static void feedback(ServerPlayer player, Mode mode, String message) {
        tell(player, message);
        PacketDistributor.sendToPlayer(player, new ModNetwork.ServiceFeedbackPayload(mode.ordinal(), message));
    }
}
