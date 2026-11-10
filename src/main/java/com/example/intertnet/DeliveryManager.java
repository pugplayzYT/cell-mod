package com.example.intertnet;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DeliveryManager {
    private static final List<RescueMission> ACTIVE_RESCUES = new ArrayList<>();
    private static int syncTickCounter = 0;

    public static void resetSessionState() {
        synchronized (ACTIVE_RESCUES) {
            ACTIVE_RESCUES.clear();
        }
        CellTowerBlockEntity.clearAllTrackedTowers();
        ClientPhoneState.reset();
    }

    public enum RescuePhase {
        DRIVING_TO_PLAYER,
        WAITING_FOR_PLAYER,
        DRIVING_TO_VILLAGE,
        COMPLETED
    }

    public static class RescueMission {
        public final UUID ownerUuid;
        public final String simNetwork;
        public final ResourceKey<Level> dimension;
        public final BlockPos towerPos;
        public final BlockPos pickupPos;
        public BlockPos villagePos;
        public final int signalBars;

        public RescuePhase phase = RescuePhase.DRIVING_TO_PLAYER;
        public Vec3 leadPos;
        public Vec3 forwardDir = new Vec3(1, 0, 0);
        public double initialDistToPlayer;
        public double initialDistToVillage = 100.0;

        public UUID frontCartUuid;
        public UUID driverVillagerUuid;
        public UUID backCartUuid;

        public String statusText = "Dispatching Rescue Carts...";
        public int completedTicks = -1;

        public RescueMission(
                UUID ownerUuid,
                String simNetwork,
                ResourceKey<Level> dimension,
                BlockPos towerPos,
                BlockPos pickupPos,
                BlockPos villagePos,
                int signalBars
        ) {
            this.ownerUuid = ownerUuid;
            this.simNetwork = simNetwork;
            this.dimension = dimension;
            this.towerPos = towerPos.immutable();
            this.pickupPos = pickupPos.immutable();
            this.villagePos = villagePos.immutable();
            this.signalBars = Mth.clamp(signalBars, 1, 4);
            this.leadPos = new Vec3(towerPos.getX() + 0.5, towerPos.getY() + 1.0, towerPos.getZ() + 0.5);
            Vec3 diff = Vec3.atBottomCenterOf(pickupPos).subtract(this.leadPos);
            double horiz = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
            if (horiz > 0.01) {
                this.forwardDir = new Vec3(diff.x / horiz, 0, diff.z / horiz);
            }
            this.initialDistToPlayer = Math.max(1.0, horiz);
        }

        public int getRemainingMeters() {
            if (phase == RescuePhase.DRIVING_TO_PLAYER) {
                return (int) Math.round(horizontalDist(leadPos, Vec3.atBottomCenterOf(pickupPos)));
            } else if (phase == RescuePhase.DRIVING_TO_VILLAGE) {
                return (int) Math.round(horizontalDist(leadPos, Vec3.atBottomCenterOf(villagePos)));
            }
            return 0;
        }

        public int getProgressPercent() {
            if (phase == RescuePhase.COMPLETED) return 100;
            if (phase == RescuePhase.WAITING_FOR_PLAYER) return 100;
            if (phase == RescuePhase.DRIVING_TO_PLAYER) {
                double rem = horizontalDist(leadPos, Vec3.atBottomCenterOf(pickupPos));
                return Mth.clamp((int) Math.round((1.0 - rem / Math.max(1.0, initialDistToPlayer)) * 100.0), 0, 99);
            }
            double rem = horizontalDist(leadPos, Vec3.atBottomCenterOf(villagePos));
            return Mth.clamp((int) Math.round((1.0 - rem / Math.max(1.0, initialDistToVillage)) * 100.0), 0, 99);
        }
    }

    private static double horizontalDist(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public record ChatMessage(String senderName, String senderSimNet, String text, long timestampMs) {}

    public static void startHelpCall(ServerPlayer player, String simNetwork, BlockPos towerPos, int signalBars) {
        ServerLevel level = player.serverLevel();

        synchronized (ACTIVE_RESCUES) {
            Iterator<RescueMission> it = ACTIVE_RESCUES.iterator();
            while (it.hasNext()) {
                RescueMission old = it.next();
                if (old.ownerUuid.equals(player.getUUID())) {
                    ServerLevel oldLvl = player.server.getLevel(old.dimension);
                    if (oldLvl != null) {
                        cleanupEntities(oldLvl, old);
                    }
                    it.remove();
                }
            }
        }

        BlockPos nearestVillage = level.findNearestMapStructure(StructureTags.VILLAGE, player.blockPosition(), 100, false);
        if (nearestVillage == null) {
            nearestVillage = level.getSharedSpawnPos();
        }
        int vy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, nearestVillage.getX(), nearestVillage.getZ());
        BlockPos villageSurface = new BlockPos(nearestVillage.getX(), Math.max(level.getMinBuildHeight() + 1, vy), nearestVillage.getZ());

        RescueMission mission = new RescueMission(
                player.getUUID(),
                simNetwork,
                level.dimension(),
                towerPos,
                player.blockPosition(),
                villageSurface,
                signalBars
        );

        synchronized (ACTIVE_RESCUES) {
            ACTIVE_RESCUES.add(mission);
        }

        if (level.isLoaded(towerPos)) {
            ensureRescueEntities(level, mission);
        }
        player.displayClientMessage(Component.literal("🚨 Help Call sent! A Villager Rescue Minecart is driving out from the Tower!")
                .withStyle(ChatFormatting.GREEN), true);
        syncToPlayer(player);
    }

    private static void cleanupEntities(ServerLevel level, RescueMission m) {
        if (m.frontCartUuid != null) {
            Entity e = level.getEntity(m.frontCartUuid);
            if (e != null) e.discard();
        }
        if (m.driverVillagerUuid != null) {
            Entity e = level.getEntity(m.driverVillagerUuid);
            if (e != null) e.discard();
        }
        if (m.backCartUuid != null) {
            Entity e = level.getEntity(m.backCartUuid);
            if (e != null) {
                e.ejectPassengers();
                e.discard();
            }
        }
    }

    private static boolean ensureRescueEntities(ServerLevel level, RescueMission m) {
        Entity fEnt = m.frontCartUuid != null ? level.getEntity(m.frontCartUuid) : null;
        Entity dEnt = m.driverVillagerUuid != null ? level.getEntity(m.driverVillagerUuid) : null;
        Entity bEnt = m.backCartUuid != null ? level.getEntity(m.backCartUuid) : null;

        Minecart frontCart = (fEnt instanceof Minecart mc && mc.isAlive()) ? mc : null;
        Villager driver = (dEnt instanceof Villager v && v.isAlive()) ? v : null;
        Minecart backCart = (bEnt instanceof Minecart mc && mc.isAlive()) ? mc : null;

        if (frontCart == null) {
            frontCart = EntityType.MINECART.create(level);
            if (frontCart == null) return false;
            frontCart.moveTo(m.leadPos.x, m.leadPos.y, m.leadPos.z, 0f, 0f);
            frontCart.setNoGravity(true);
            frontCart.setInvulnerable(true);
            frontCart.setCustomName(Component.literal("🚕 Rescue Driver").withStyle(ChatFormatting.GOLD));
            frontCart.setCustomNameVisible(true);
            level.addFreshEntity(frontCart);
            m.frontCartUuid = frontCart.getUUID();
        }

        if (driver == null) {
            driver = EntityType.VILLAGER.create(level);
            if (driver != null) {
                driver.moveTo(m.leadPos.x, m.leadPos.y, m.leadPos.z, 0f, 0f);
                driver.setInvulnerable(true);
                driver.setPersistenceRequired();
                driver.setNoAi(true);
                level.addFreshEntity(driver);
                driver.startRiding(frontCart, true);
                m.driverVillagerUuid = driver.getUUID();
            }
        } else if (!driver.isPassenger()) {
            driver.startRiding(frontCart, true);
        }

        if (backCart == null) {
            backCart = EntityType.MINECART.create(level);
            if (backCart == null) return false;
            Vec3 backPos = m.leadPos.subtract(m.forwardDir.scale(1.65));
            backCart.moveTo(backPos.x, backPos.y, backPos.z, 0f, 0f);
            backCart.setNoGravity(true);
            backCart.setInvulnerable(true);
            backCart.setCustomName(Component.literal("🚑 Hop In! (Village Rescue)").withStyle(ChatFormatting.AQUA));
            backCart.setCustomNameVisible(true);
            level.addFreshEntity(backCart);
            m.backCartUuid = backCart.getUUID();
        }

        return true;
    }

    public static void onServerTick(final ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        synchronized (ACTIVE_RESCUES) {
            Iterator<RescueMission> it = ACTIVE_RESCUES.iterator();
            while (it.hasNext()) {
                RescueMission m = it.next();
                ServerLevel level = server.getLevel(m.dimension);
                if (level == null) {
                    it.remove();
                    continue;
                }

                if (m.phase == RescuePhase.COMPLETED) {
                    m.completedTicks++;
                    if (m.completedTicks > 100) {
                        it.remove();
                    }
                    continue;
                }

                BlockPos curBlock = BlockPos.containing(m.leadPos);
                if (!level.isLoaded(curBlock)) {
                    m.statusText = "Paused (Chunk Unloaded)";
                    continue;
                }

                if (!ensureRescueEntities(level, m)) {
                    continue;
                }

                Minecart frontCart = (Minecart) level.getEntity(m.frontCartUuid);
                Minecart backCart = (Minecart) level.getEntity(m.backCartUuid);
                if (frontCart == null || backCart == null) {
                    continue;
                }

                if (m.phase == RescuePhase.DRIVING_TO_PLAYER) {
                    Vec3 target = Vec3.atBottomCenterOf(m.pickupPos);
                    double dist = horizontalDist(m.leadPos, target);
                    double speed = switch (m.signalBars) {
                        case 4 -> 0.48;
                        case 3 -> 0.36;
                        case 2 -> 0.26;
                        default -> 0.18;
                    };

                    if (dist <= 2.8) {
                        m.phase = RescuePhase.WAITING_FOR_PLAYER;
                        m.statusText = "Arrived! Hop in the back Minecart!";
                        level.playSound(null, BlockPos.containing(m.leadPos), SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 1.0f, 1.2f);
                        level.playSound(null, BlockPos.containing(m.leadPos), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0f, 1.0f);
                        ServerPlayer owner = server.getPlayerList().getPlayer(m.ownerUuid);
                        if (owner != null) {
                            owner.displayClientMessage(Component.literal("🚕 Rescue Minecart arrived! Right-click the back cart to hop in!")
                                    .withStyle(ChatFormatting.AQUA), true);
                            syncToPlayer(owner);
                        }
                        continue;
                    }

                    m.statusText = String.format("Driving to you (%.0fm away)", dist);
                    stepTrainOffRoad(level, m, frontCart, backCart, target, speed);

                } else if (m.phase == RescuePhase.WAITING_FOR_PLAYER) {
                    if (!backCart.getPassengers().isEmpty()) {
                        BlockPos foundVillage = level.findNearestMapStructure(StructureTags.VILLAGE, backCart.blockPosition(), 100, false);
                        if (foundVillage != null) {
                            int vy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, foundVillage.getX(), foundVillage.getZ());
                            m.villagePos = new BlockPos(foundVillage.getX(), Math.max(level.getMinBuildHeight() + 1, vy), foundVillage.getZ());
                        }
                        m.initialDistToVillage = Math.max(1.0, horizontalDist(m.leadPos, Vec3.atBottomCenterOf(m.villagePos)));
                        m.phase = RescuePhase.DRIVING_TO_VILLAGE;
                        m.statusText = "Driving to Village [" + m.villagePos.getX() + ", " + m.villagePos.getZ() + "]";
                        level.playSound(null, backCart.blockPosition(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.0f, 1.1f);
                        for (Entity passenger : backCart.getPassengers()) {
                            if (passenger instanceof ServerPlayer sp) {
                                sp.displayClientMessage(Component.literal("🚕 Hold on! Driving you to the nearest Village at [" + m.villagePos.getX() + ", " + m.villagePos.getZ() + "]!")
                                        .withStyle(ChatFormatting.GREEN), true);
                            }
                        }
                    }

                } else if (m.phase == RescuePhase.DRIVING_TO_VILLAGE) {
                    if (level.isLoaded(m.villagePos)) {
                        int vy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, m.villagePos.getX(), m.villagePos.getZ());
                        m.villagePos = new BlockPos(m.villagePos.getX(), Math.max(level.getMinBuildHeight() + 1, vy), m.villagePos.getZ());
                    }

                    Vec3 target = Vec3.atBottomCenterOf(m.villagePos);
                    double dist = horizontalDist(m.leadPos, target);
                    if (dist <= 3.5) {
                        for (Entity passenger : new ArrayList<>(backCart.getPassengers())) {
                            passenger.stopRiding();
                            passenger.teleportTo(target.x, target.y + 0.5, target.z);
                            if (passenger instanceof ServerPlayer sp) {
                                sp.displayClientMessage(Component.literal("🏡 Arrived safely at the nearest Village!")
                                        .withStyle(ChatFormatting.GREEN), true);
                            }
                        }
                        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, target.x, target.y + 1.0, target.z, 25, 0.5, 0.5, 0.5, 0.05);
                        level.playSound(null, m.villagePos, SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.0f, 1.0f);
                        cleanupEntities(level, m);
                        m.phase = RescuePhase.COMPLETED;
                        m.completedTicks = 0;
                        m.statusText = "Arrived at Village!";
                        continue;
                    }

                    m.statusText = String.format("To Village (%.0fm left)", dist);
                    stepTrainOffRoad(level, m, frontCart, backCart, target, 0.52);
                }
            }
        }

        syncTickCounter++;
        if (syncTickCounter >= 10) {
            syncTickCounter = 0;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                syncToPlayer(player);
            }
        }
    }

    private static void stepTrainOffRoad(ServerLevel level, RescueMission m, Minecart frontCart, Minecart backCart, Vec3 target, double speed) {
        double dx = target.x - m.leadPos.x;
        double dz = target.z - m.leadPos.z;
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        if (horizDist < 0.001) return;

        double dirX = dx / horizDist;
        double dirZ = dz / horizDist;
        m.forwardDir = new Vec3(dirX, 0, dirZ);

        double step = Math.min(horizDist, speed);
        double nextX = m.leadPos.x + dirX * step;
        double nextZ = m.leadPos.z + dirZ * step;
        double nextY = getGroundY(level, nextX, m.leadPos.y, nextZ, target.y);

        m.leadPos = new Vec3(nextX, nextY, nextZ);

        double backX = nextX - dirX * 1.65;
        double backZ = nextZ - dirZ * 1.65;
        double backY = getGroundY(level, backX, nextY, backZ, target.y);

        float cartYaw = (float) (Mth.atan2(dirZ, dirX) * (180.0 / Math.PI));

        positionCart(level, frontCart, m.leadPos, cartYaw);
        frontCart.setDeltaMovement(dirX * step, 0, dirZ * step);
        positionCart(level, backCart, new Vec3(backX, backY, backZ), cartYaw);
        backCart.setDeltaMovement(dirX * step, 0, dirZ * step);
    }

    static void positionCart(ServerLevel level, AbstractMinecart cart, Vec3 position, float yaw) {
        cart.setNoGravity(true);
        cart.moveTo(position.x, position.y + 0.1, position.z, yaw, 0f);
        cart.hurtMarked = true;
        level.getChunkSource().broadcast(cart, new ClientboundTeleportEntityPacket(cart));
    }

    static double getGroundY(ServerLevel level, double x, double currentY, double z, double fallbackTargetY) {
        BlockPos col = BlockPos.containing(x, currentY, z);
        if (level.isLoaded(col)) {
            int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, col.getX(), col.getZ());
            if (surfaceY > level.getMinBuildHeight()) {
                return Mth.lerp(0.35, currentY, (double) surfaceY);
            }
        }
        return Mth.lerp(0.15, currentY, fallbackTargetY);
    }

    public static void postNetworkMessage(ServerPlayer sender, String senderSimNet, String messageText) {
        String cleanText = messageText == null ? "" : messageText.trim();
        if (cleanText.isEmpty()) return;
        if (cleanText.length() > 140) {
            cleanText = cleanText.substring(0, 140);
        }

        SignalManager.SignalStatus senderSig = SignalManager.getBestSignal(sender, senderSimNet);
        if (senderSig.bars() <= 0 || senderSig.connectedTowerPos() == null) {
            sender.displayClientMessage(Component.literal("Cannot send message: No cellular signal!").withStyle(ChatFormatting.RED), true);
            return;
        }

        MessageSavedData.get(sender.server.overworld()).addMessage(
                new ChatMessage(sender.getScoreboardName(), senderSimNet, cleanText, System.currentTimeMillis())
        );

        // Deliver globally to every player who has signal on ANY cellular carrier!
        for (ServerPlayer peer : sender.server.getPlayerList().getPlayers()) {
            String peerSim = findPlayerSimNetwork(peer);
            if (peerSim == null) continue;
            SignalManager.SignalStatus peerSig = SignalManager.getBestSignal(peer, peerSim);
            if (peerSig.bars() > 0 && peerSig.connectedTowerPos() != null) {
                if (peer != sender) {
                    peer.displayClientMessage(Component.literal("💬 [" + sender.getScoreboardName() + "]: " + cleanText)
                            .withStyle(ChatFormatting.AQUA), true);
                    peer.level().playSound(null, peer.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6f, 1.6f);
                }
                syncToPlayer(peer);
            }
        }
        syncToPlayer(sender);
    }

    public static ItemStack findPlayerPhoneStack(Player player) {
        if (player.getMainHandItem().is(IntertnetMod.PHONE_ITEM.get())) {
            return player.getMainHandItem();
        }
        if (player.getOffhandItem().is(IntertnetMod.PHONE_ITEM.get())) {
            return player.getOffhandItem();
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.is(IntertnetMod.PHONE_ITEM.get())) {
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    @Nullable
    public static String findPlayerSimNetwork(Player player) {
        ItemStack phone = findPlayerPhoneStack(player);
        if (phone.isEmpty()) {
            return null;
        }
        return PhoneItem.getInsertedSimNetwork(phone);
    }

    public static boolean hasAnyPhoneInInventory(Player player) {
        return !findPlayerPhoneStack(player).isEmpty();
    }

    public static void syncToPlayer(ServerPlayer player) {
        String simNet = findPlayerSimNetwork(player);
        List<ModNetwork.OrderSnapshot> playerRescues = new ArrayList<>();
        TownServices.addTracking(player, playerRescues);
        synchronized (ACTIVE_RESCUES) {
            for (RescueMission m : ACTIVE_RESCUES) {
                if (m.ownerUuid.equals(player.getUUID())) {
                    playerRescues.add(new ModNetwork.OrderSnapshot(
                            "RESCUE",
                            "Minecart Taxi",
                            m.statusText,
                            m.getRemainingMeters(),
                            m.getProgressPercent(),
                            m.villagePos.toShortString()
                    ));
                }
            }
        }

        List<ModNetwork.MessageSnapshot> messages = new ArrayList<>();
        List<String> peersOnNetwork = new ArrayList<>();
        String currentNetLabel = "Offline";

        if (simNet != null) {
            SignalManager.SignalStatus sig = SignalManager.getBestSignal(player, simNet);
            if (sig.bars() > 0 && sig.connectedTowerPos() != null) {
                currentNetLabel = TowerNetworkManager.getNetworkId(player.level(), sig.connectedTowerPos());
                for (ChatMessage m : MessageSavedData.get(player.server.overworld()).getMessagesSnapshot()) {
                    messages.add(new ModNetwork.MessageSnapshot(m.senderName(), m.senderSimNet(), m.text()));
                }
                for (ServerPlayer p : player.server.getPlayerList().getPlayers()) {
                    String pSim = findPlayerSimNetwork(p);
                    if (pSim == null) continue;
                    SignalManager.SignalStatus ps = SignalManager.getBestSignal(p, pSim);
                    if (ps.bars() > 0 && ps.connectedTowerPos() != null) {
                        peersOnNetwork.add(p.getScoreboardName() + " [" + pSim + "]");
                    }
                }
            }
        }

        PacketDistributor.sendToPlayer(player, new ModNetwork.SyncPhoneDataPayload(
                currentNetLabel,
                playerRescues,
                messages,
                peersOnNetwork
        ));
    }
}
