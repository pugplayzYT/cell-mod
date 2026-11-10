package com.example.intertnet;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class DeliveryManager {
    private static int syncTickCounter;

    public static void resetSessionState() {
        syncTickCounter = 0;
        CellTowerBlockEntity.clearAllTrackedTowers();
        ClientPhoneState.reset();
    }

    public record ChatMessage(String senderName, String senderSimNet, String text, long timestampMs) {}

    public static void onServerTick(final ServerTickEvent.Post event) {
        if (++syncTickCounter >= 10) {
            syncTickCounter = 0;
            for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                syncToPlayer(player);
            }
        }
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
        List<ModNetwork.OrderSnapshot> orders = new ArrayList<>();
        TownServices.addTracking(player, orders);
        EmergencyDispatch.addTracking(player, orders);

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
                orders,
                messages,
                peersOnNetwork
        ));
    }
}
