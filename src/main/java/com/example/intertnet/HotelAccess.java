package com.example.intertnet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

final class HotelAccess {
    static final int PRICE = 3;
    private static final String WIFI = "HOTEL-WIFI-";
    private static final String SIM = "HOTEL-GUEST-";
    private static final String HOTEL = "IntertnetHotel";
    private static final String PASS = "IntertnetHotelPass";
    private static final Map<DoorKey, Map<UUID, Lease>> doors = new HashMap<>();
    private static final ConcurrentLinkedQueue<LoadedHotel> loadedHotels = new ConcurrentLinkedQueue<>();

    private record Site(BlockPos origin, boolean wild) {
        BlockPos entrance() { return origin.offset(HotelPiece.entrance(wild)); }
    }
    private record Reader(Site site, int role) {}
    private record DoorKey(ServerLevel level, BlockPos pos) {}
    private record Lease(Direction crossing, long expiry, @Nullable Site entry) {}
    private record LoadedHotel(ServerLevel level, ChunkPos chunk, Site site, HotelPiece piece) {}
    private record Stay(UUID token, Site site, int room, boolean entered) {
        String sim() { return SIM + token; }
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(HotelAccess::talkToReception);
        NeoForge.EVENT_BUS.addListener(HotelAccess::useReader);
        NeoForge.EVENT_BUS.addListener(HotelAccess::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(HotelAccess::tick);
        NeoForge.EVENT_BUS.addListener(HotelAccess::stop);
    }

    static void markReceptionist(Villager villager, BlockPos origin, boolean wild) {
        villager.getPersistentData().put(HOTEL, encodeSite(new Site(origin, wild)));
    }

    static void markReader(HopperBlockEntity hopper, BlockPos origin, boolean wild, int role) {
        CompoundTag tag = encodeSite(new Site(origin, wild));
        tag.putInt("Role", role);
        hopper.getPersistentData().put(HOTEL, tag);
        hopper.setChanged();
    }

    static String wifiNetwork(BlockPos origin) {
        return WIFI + origin.asLong();
    }

    static boolean isHotelTower(CellTowerBlockEntity tower) {
        return tower.getNetworkUid().startsWith(WIFI);
    }

    static String simLabel(@Nullable String network) {
        return network != null && network.startsWith(SIM) ? "Hotel Wi-Fi" : network;
    }

    static boolean canUseSim(ServerPlayer player, @Nullable String network) {
        if (network == null || !network.startsWith(SIM)) return true;
        Stay stay = Stays.get(player.serverLevel()).byPlayer.get(player.getUUID());
        return stay != null && stay.entered && stay.site.wild && stay.sim().equals(network);
    }

    static boolean matchesWifi(Level level, String network, String sim) {
        if (!(level instanceof ServerLevel server) || !sim.startsWith(SIM)) return false;
        return Stays.get(server).byPlayer.values().stream().anyMatch(stay ->
                stay.entered && stay.site.wild && stay.sim().equals(sim) && wifiNetwork(stay.site.origin).equals(network));
    }

    private static void talkToReception(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getTarget() instanceof Villager receptionist)
                || !receptionist.getPersistentData().contains(HOTEL, Tag.TAG_COMPOUND)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.CONSUME);
        if (event.getHand() == InteractionHand.MAIN_HAND) {
            PacketDistributor.sendToPlayer(player, new ModNetwork.HotelReceptionPayload(receptionist.getId()));
        }
    }

    static void buyPass(ServerPlayer player, int entityId) {
        Entity entity = player.serverLevel().getEntity(entityId);
        if (!player.isAlive() || player.isSpectator() || !(entity instanceof Villager receptionist)
                || !receptionist.isAlive() || player.distanceToSqr(receptionist) > 25
                || !receptionist.getPersistentData().contains(HOTEL, Tag.TAG_COMPOUND)) return;
        Site site = decodeSite(receptionist.getPersistentData().getCompound(HOTEL));
        Stays data = Stays.get(player.serverLevel());
        Stay stay = data.byPlayer.get(player.getUUID());
        if (stay != null) {
            if (!site.equals(stay.site)) {
                tell(player, "Check out of your current hotel first.");
                return;
            }
            if (!hasPass(player, stay.token)) ServicePayment.give(player, pass(stay));
            tell(player, "Your stay is active. Use your pass at the entrance or your room.");
            return;
        }
        if (data.byPlayer.values().stream().filter(guest -> guest.site.equals(site)).count() >= HotelPiece.ROOM_DOORS.size()) {
            tell(player, "This hotel is fully booked.");
            return;
        }
        if (!ServicePayment.takeEmeralds(player, PRICE)) {
            tell(player, "A hotel pass costs " + PRICE + " emeralds.");
            return;
        }
        stay = new Stay(UUID.randomUUID(), site, -1, false);
        data.byPlayer.put(player.getUUID(), stay);
        data.setDirty();
        ServicePayment.give(player, pass(stay));
        tell(player, "Pass issued. Feed it into the entrance hopper.");
    }

    private static ItemStack pass(Stay stay) {
        ItemStack stack = new ItemStack(Items.PAPER);
        CompoundTag tag = new CompoundTag();
        tag.putUUID(PASS, stay.token);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(stay.site.wild ? "Wild Hotel Pass" : "Plains Hotel Pass"));
        return stack;
    }

    @Nullable
    private static UUID passId(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (!stack.is(Items.PAPER) || data == null) return null;
        CompoundTag tag = data.copyTag();
        return tag.hasUUID(PASS) ? tag.getUUID(PASS) : null;
    }

    private static boolean hasPass(ServerPlayer player, UUID token) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (token.equals(passId(player.getInventory().getItem(i)))) return true;
        }
        return false;
    }

    private static void useReader(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != InteractionHand.MAIN_HAND
                || !(player.serverLevel().getBlockEntity(event.getPos()) instanceof HopperBlockEntity hopper)) return;
        Reader reader = reader(hopper);
        if (reader == null) return;
        ItemStack stack = event.getItemStack();
        boolean emergencyExit = reader.role == HotelPiece.EXIT && player.isShiftKeyDown() && stack.isEmpty();
        boolean roomExit = false;
        BlockPos roomDoor = null;
        if (reader.role >= 0 && player.isShiftKeyDown() && stack.isEmpty()) {
            BlockPos relativeDoor = HotelPiece.ROOM_DOORS.get(reader.role);
            roomDoor = reader.site.origin.offset(relativeDoor);
            roomExit = relativeDoor.getX() == 5 ? player.getX() < roomDoor.getX()
                    : player.getX() > roomDoor.getX() + 1;
        }
        if (!emergencyExit && !roomExit && passId(stack) == null) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.CONSUME);
        if (emergencyExit) checkout(player, reader.site, ItemStack.EMPTY, true);
        else if (roomExit) open(player, roomDoor, player.getX() < roomDoor.getX() ? Direction.EAST : Direction.WEST, 120, null);
        else readPass(player, reader, stack);
    }

    @Nullable
    private static Reader reader(HopperBlockEntity hopper) {
        if (!hopper.getPersistentData().contains(HOTEL, Tag.TAG_COMPOUND)) return null;
        CompoundTag tag = hopper.getPersistentData().getCompound(HOTEL);
        int role = tag.getInt("Role");
        if (role < HotelPiece.EXIT || role >= HotelPiece.ROOM_DOORS.size()) return null;
        Site site = decodeSite(tag);
        return site.origin.offset(HotelPiece.reader(site.wild, role)).equals(hopper.getBlockPos()) ? new Reader(site, role) : null;
    }

    private static void readPass(ServerPlayer player, Reader reader, ItemStack stack) {
        ServerLevel level = player.serverLevel();
        Stays data = Stays.get(level);
        UUID guest = player.getUUID();
        Stay stay = data.byPlayer.get(guest);
        if (stay == null || !stay.site.equals(reader.site) || !stay.token.equals(passId(stack))) {
            tell(player, "This reader needs your active pass for this hotel.");
            return;
        }
        if (reader.role == HotelPiece.EXIT) {
            checkout(player, stay.site, stack, false);
        } else if (reader.role == HotelPiece.ENTRY) {
            open(player, stay.site.entrance(), Direction.NORTH, 240, stay.site);
            tell(player, "Pass accepted. Walk through the entrance.");
        } else {
            if (!stay.entered) {
                tell(player, "Use the entrance reader first.");
                return;
            }
            if (stay.room >= 0 && stay.room != reader.role) {
                tell(player, "Your pass is assigned to room " + (stay.room + 1) + ".");
                return;
            }
            if (data.byPlayer.entrySet().stream().anyMatch(other -> !other.getKey().equals(guest)
                    && other.getValue().site.equals(stay.site) && other.getValue().room == reader.role)) {
                tell(player, "This room is occupied. Try another room.");
                return;
            }
            BlockPos door = stay.site.origin.offset(HotelPiece.ROOM_DOORS.get(reader.role));
            if (!level.getBlockState(door).is(Blocks.IRON_DOOR)) {
                tell(player, "That room's door is out of service.");
                return;
            }
            data.byPlayer.put(guest, new Stay(stay.token, stay.site, reader.role, true));
            data.setDirty();
            open(player, door, player.getX() > door.getX() + 0.5 ? Direction.WEST : Direction.EAST, 120, null);
            tell(player, "Room " + (reader.role + 1) + " unlocked.");
        }
    }

    private static void checkout(ServerPlayer player, Site site, ItemStack pass, boolean emergencyExit) {
        Stays data = Stays.get(player.serverLevel());
        Stay stay = data.byPlayer.get(player.getUUID());
        if (stay != null && stay.site.equals(site)) {
            if (!emergencyExit) pass.shrink(1);
            data.byPlayer.remove(player.getUUID());
            data.setDirty();
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (stay.sim().equals(SimCardItem.getSimNetwork(stack))) stack.setCount(0);
                else if (stack.is(IntertnetMod.PHONE_ITEM.get()) && stay.sim().equals(PhoneItem.getInsertedSimNetwork(stack))) {
                    PhoneItem.setInsertedSimNetwork(stack, null);
                } else if (stay.token.equals(passId(stack))) stack.setCount(0);
            }
            player.getInventory().setChanged();
            DeliveryManager.syncToPlayer(player);
        }
        open(player, site.entrance(), Direction.SOUTH, 20 * 45, null);
        tell(player, "Checked out. The exit is open.");
    }

    private static void open(ServerPlayer player, BlockPos pos, Direction direction, int ticks, @Nullable Site entry) {
        ServerLevel level = player.serverLevel();
        if (!level.isLoaded(pos)) return;
        ServiceSitePiece.setIronDoor(level, pos, true);
        doors.computeIfAbsent(new DoorKey(level, pos), ignored -> new HashMap<>())
                .put(player.getUUID(), new Lease(direction, level.getGameTime() + ticks, entry));
    }

    private static void onChunkLoad(ChunkEvent.Load event) {
        if (event.isNewChunk() || !(event.getLevel() instanceof ServerLevel level)) return;
        for (var start : event.getChunk().getAllStarts().values()) {
            if (!start.isValid() || !(start.getStructure() instanceof HotelStructure)) continue;
            for (var piece : start.getPieces()) {
                if (piece instanceof HotelPiece hotel) {
                    ChunkPos chunk = start.getChunkPos();
                    loadedHotels.add(new LoadedHotel(level, chunk,
                            new Site(new BlockPos(chunk.getMinBlockX(), piece.getBoundingBox().minY(), chunk.getMinBlockZ()), hotel.isWild()),
                            hotel));
                }
            }
        }
    }

    private static void tick(ServerTickEvent.Post event) {
        for (LoadedHotel hotel; (hotel = loadedHotels.poll()) != null;) {
            var chunk = hotel.level.getChunkSource().getChunkNow(hotel.chunk.x, hotel.chunk.z);
            if (chunk == null) continue;
            if (hotel.piece.repairLayout(hotel.level)) chunk.setUnsaved(true);
            closeIdleDoor(hotel.level, hotel.site.entrance());
            for (BlockPos room : HotelPiece.ROOM_DOORS) closeIdleDoor(hotel.level, hotel.site.origin.offset(room));
        }
        doors.entrySet().removeIf(entry -> {
            DoorKey key = entry.getKey();
            if (!key.level.isLoaded(key.pos)) return true;
            var guests = entry.getValue();
            guests.entrySet().removeIf(guest -> {
                Lease lease = guest.getValue();
                ServerPlayer player = event.getServer().getPlayerList().getPlayer(guest.getKey());
                if (player == null || player.serverLevel() != key.level) return true;
                double dx = player.getX() - key.pos.getX() - 0.5;
                double dz = player.getZ() - key.pos.getZ() - 0.5;
                double across = dx * lease.crossing.getStepX() + dz * lease.crossing.getStepZ();
                boolean crossed = Math.abs(player.getY() - key.pos.getY()) < 2 && across > 0.85 && across < 3
                        && (lease.crossing.getAxis() == Direction.Axis.X ? Math.abs(dz) : Math.abs(dx)) < 1.4;
                if (crossed && lease.entry != null) enter(player, lease.entry);
                return crossed || key.level.getGameTime() >= lease.expiry;
            });
            if (!guests.isEmpty()) return false;
            if (!key.level.getEntitiesOfClass(LivingEntity.class, new AABB(key.pos).expandTowards(0, 1, 0),
                    LivingEntity::isAlive).isEmpty()) return false;
            ServiceSitePiece.setIronDoor(key.level, key.pos, false);
            return true;
        });
        if (event.getServer().getTickCount() % 5 == 0) {
            for (ServerLevel level : event.getServer().getAllLevels()) tickReaders(level);
        }
    }

    private static void closeIdleDoor(ServerLevel level, BlockPos pos) {
        if (!doors.containsKey(new DoorKey(level, pos))) ServiceSitePiece.setIronDoor(level, pos, false);
    }

    private static void tickReaders(ServerLevel level) {
        Set<Site> sites = new HashSet<>();
        Stays.get(level).byPlayer.values().forEach(stay -> sites.add(stay.site));
        for (Site site : sites) {
            if (!level.isLoaded(site.origin)) continue;
            if (site.wild && level.getBlockEntity(site.origin.offset(HotelPiece.WIFI)) instanceof CellTowerBlockEntity tower
                    && isHotelTower(tower) && !tower.isPowered() && tower.getItem(CellTowerBlockEntity.FUEL_SLOT).isEmpty()) {
                tower.setItem(CellTowerBlockEntity.FUEL_SLOT, new ItemStack(Items.COPPER_INGOT, 16));
            }
            for (int role = HotelPiece.EXIT; role < HotelPiece.ROOM_DOORS.size(); role++) {
                BlockPos pos = site.origin.offset(HotelPiece.reader(site.wild, role));
                if (!(level.getBlockEntity(pos) instanceof HopperBlockEntity hopper)) continue;
                Reader reader = reader(hopper);
                if (reader == null) continue;
                for (int slot = 0; slot < hopper.getContainerSize(); slot++) {
                    UUID token = passId(hopper.getItem(slot));
                    if (token == null) continue;
                    ServerPlayer owner = level.players().stream().filter(player -> player.distanceToSqr(
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 36
                            && hasStayToken(player, token)).findFirst().orElse(null);
                    if (owner == null) continue;
                    ItemStack pass = hopper.removeItem(slot, 1);
                    readPass(owner, reader, pass);
                    if (!pass.isEmpty()) ServicePayment.give(owner, pass);
                }
            }
        }
    }

    private static boolean hasStayToken(ServerPlayer player, UUID token) {
        Stay stay = Stays.get(player.serverLevel()).byPlayer.get(player.getUUID());
        return stay != null && stay.token.equals(token);
    }

    private static void enter(ServerPlayer player, Site site) {
        Stays data = Stays.get(player.serverLevel());
        Stay stay = data.byPlayer.get(player.getUUID());
        if (stay == null || !stay.site.equals(site) || stay.entered) return;
        data.byPlayer.put(player.getUUID(), new Stay(stay.token, site, stay.room, true));
        data.setDirty();
        if (site.wild) {
            ItemStack card = SimCardItem.createForNetwork(stay.sim());
            card.set(DataComponents.CUSTOM_NAME, Component.literal("Hotel Wi-Fi SIM"));
            ServicePayment.give(player, card);
            tell(player, "Hotel Wi-Fi SIM issued. Insert it in your phone to get reception.");
        }
    }

    private static void stop(ServerStoppingEvent event) {
        doors.keySet().forEach(key -> {
            if (key.level.isLoaded(key.pos)) ServiceSitePiece.setIronDoor(key.level, key.pos, false);
        });
        doors.clear();
        loadedHotels.clear();
    }

    private static void tell(ServerPlayer player, String text) {
        player.displayClientMessage(Component.literal(text), false);
        PacketDistributor.sendToPlayer(player, new ModNetwork.ServiceFeedbackPayload(ModNetwork.HOTEL_FEEDBACK, text));
    }

    private static CompoundTag encodeSite(Site site) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Origin", site.origin.asLong());
        tag.putBoolean("Wild", site.wild);
        return tag;
    }

    private static Site decodeSite(CompoundTag tag) {
        return new Site(BlockPos.of(tag.getLong("Origin")), tag.getBoolean("Wild"));
    }

    private static final class Stays extends SavedData {
        private final Map<UUID, Stay> byPlayer = new HashMap<>();

        static Stays get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(new Factory<>(Stays::new, Stays::load), "intertnet_hotel_stays");
        }

        private static Stays load(CompoundTag tag, HolderLookup.Provider registries) {
            Stays data = new Stays();
            for (Tag value : tag.getList("Stays", Tag.TAG_COMPOUND)) {
                CompoundTag entry = (CompoundTag) value;
                if (entry.hasUUID("Guest") && entry.hasUUID("Token")) {
                    data.byPlayer.put(entry.getUUID("Guest"), new Stay(entry.getUUID("Token"), decodeSite(entry),
                            entry.getInt("Room"), entry.getBoolean("Entered")));
                }
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag list = new ListTag();
            byPlayer.forEach((guest, stay) -> {
                CompoundTag entry = encodeSite(stay.site);
                entry.putUUID("Guest", guest);
                entry.putUUID("Token", stay.token);
                entry.putInt("Room", stay.room);
                entry.putBoolean("Entered", stay.entered);
                list.add(entry);
            });
            tag.put("Stays", list);
            return tag;
        }
    }
}
