package com.example.intertnet;

import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ModNetwork {
    static final int EMERGENCY_FEEDBACK = -1;
    static final int RANGER_FEEDBACK = -2;
    static final int CAMP_FEEDBACK = -3;
    static final int HOTEL_FEEDBACK = -4;

    public record CampCallPayload(boolean confirm) implements CustomPacketPayload {
        static final Type<CampCallPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(IntertnetMod.MODID, "camp_call"));
        static final StreamCodec<FriendlyByteBuf, CampCallPayload> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.BOOL, CampCallPayload::confirm, CampCallPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record CampQuotePayload(String message, int seconds, String site) implements CustomPacketPayload {
        static final Type<CampQuotePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(IntertnetMod.MODID, "camp_quote"));
        static final StreamCodec<FriendlyByteBuf, CampQuotePayload> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, CampQuotePayload::message,
                        ByteBufCodecs.VAR_INT, CampQuotePayload::seconds, ByteBufCodecs.STRING_UTF8, CampQuotePayload::site,
                        CampQuotePayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record HotelReceptionPayload(int entityId) implements CustomPacketPayload {
        static final Type<HotelReceptionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(IntertnetMod.MODID, "hotel_reception"));
        static final StreamCodec<FriendlyByteBuf, HotelReceptionPayload> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, HotelReceptionPayload::entityId, HotelReceptionPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record BuyHotelPassPayload(int entityId) implements CustomPacketPayload {
        static final Type<BuyHotelPassPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(IntertnetMod.MODID, "buy_hotel_pass"));
        static final StreamCodec<FriendlyByteBuf, BuyHotelPassPayload> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, BuyHotelPassPayload::entityId, BuyHotelPassPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record RangerCallPayload(boolean confirm) implements CustomPacketPayload {
        static final Type<RangerCallPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(IntertnetMod.MODID, "ranger_call"));
        static final StreamCodec<FriendlyByteBuf, RangerCallPayload> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.BOOL, RangerCallPayload::confirm, RangerCallPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record RangerQuotePayload(String message, int distance, int seconds, String pickup) implements CustomPacketPayload {
        static final Type<RangerQuotePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(IntertnetMod.MODID, "ranger_quote"));
        static final StreamCodec<FriendlyByteBuf, RangerQuotePayload> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, RangerQuotePayload::message,
                        ByteBufCodecs.VAR_INT, RangerQuotePayload::distance, ByteBufCodecs.VAR_INT, RangerQuotePayload::seconds,
                        ByteBufCodecs.STRING_UTF8, RangerQuotePayload::pickup, RangerQuotePayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record RangerMeetPayload(int entityId) implements CustomPacketPayload {
        static final Type<RangerMeetPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(IntertnetMod.MODID, "ranger_meet"));
        static final StreamCodec<FriendlyByteBuf, RangerMeetPayload> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, RangerMeetPayload::entityId, RangerMeetPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record RangerEscortPayload(int entityId) implements CustomPacketPayload {
        static final Type<RangerEscortPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(IntertnetMod.MODID, "ranger_escort"));
        static final StreamCodec<FriendlyByteBuf, RangerEscortPayload> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, RangerEscortPayload::entityId, RangerEscortPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record OrderSnapshot(
            String orderId,
            String displayName,
            String status,
            int remainingMeters,
            int progressPercent,
            String destCoords
    ) {}

    public record MessageSnapshot(String senderName, String senderSimNet, String text) {}

    public record ServiceRequestPayload(int mode, boolean quoteOnly, int option, int reference) implements CustomPacketPayload {
        public static final Type<ServiceRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(IntertnetMod.MODID, "service_request"));
        public static final StreamCodec<ByteBuf, ServiceRequestPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ServiceRequestPayload::mode,
                ByteBufCodecs.BOOL, ServiceRequestPayload::quoteOnly,
                ByteBufCodecs.VAR_INT, ServiceRequestPayload::option,
                ByteBufCodecs.VAR_INT, ServiceRequestPayload::reference,
                ServiceRequestPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ServiceMenuPayload(int chefId) implements CustomPacketPayload {
        public static final Type<ServiceMenuPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(IntertnetMod.MODID, "service_menu"));
        public static final StreamCodec<ByteBuf, ServiceMenuPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ServiceMenuPayload::chefId, ServiceMenuPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ServiceQuotesPayload(int mode, int reference, String message, List<TownServices.Choice> choices) implements CustomPacketPayload {
        public static final Type<ServiceQuotesPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(IntertnetMod.MODID, "service_quotes"));
        private static final StreamCodec<ByteBuf, TownServices.Choice> CHOICE_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, TownServices.Choice::id,
                ByteBufCodecs.STRING_UTF8, TownServices.Choice::name,
                ByteBufCodecs.VAR_INT, TownServices.Choice::distance,
                ByteBufCodecs.VAR_INT, TownServices.Choice::price,
                TownServices.Choice::new
        );
        public static final StreamCodec<ByteBuf, ServiceQuotesPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ServiceQuotesPayload::mode,
                ByteBufCodecs.VAR_INT, ServiceQuotesPayload::reference,
                ByteBufCodecs.STRING_UTF8, ServiceQuotesPayload::message,
                CHOICE_CODEC.apply(ByteBufCodecs.list(3)), ServiceQuotesPayload::choices,
                ServiceQuotesPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ServiceFeedbackPayload(int mode, String message) implements CustomPacketPayload {
        public static final Type<ServiceFeedbackPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(IntertnetMod.MODID, "service_feedback"));
        public static final StreamCodec<ByteBuf, ServiceFeedbackPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ServiceFeedbackPayload::mode,
                ByteBufCodecs.STRING_UTF8, ServiceFeedbackPayload::message,
                ServiceFeedbackPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record EmergencyCallPayload(int service) implements CustomPacketPayload {
        public static final Type<EmergencyCallPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(IntertnetMod.MODID, "emergency_call"));
        public static final StreamCodec<ByteBuf, EmergencyCallPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, EmergencyCallPayload::service,
                EmergencyCallPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record PrintSimCardPayload(BlockPos towerPos) implements CustomPacketPayload {
        public static final Type<PrintSimCardPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(IntertnetMod.MODID, "print_sim_card"));
        public static final StreamCodec<ByteBuf, PrintSimCardPayload> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, PrintSimCardPayload::towerPos,
                PrintSimCardPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ManagePhoneSimPayload(boolean eject) implements CustomPacketPayload {
        public static final Type<ManagePhoneSimPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(IntertnetMod.MODID, "manage_phone_sim"));
        public static final StreamCodec<ByteBuf, ManagePhoneSimPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, ManagePhoneSimPayload::eject,
                ManagePhoneSimPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ToggleWirelessLinkPayload(BlockPos towerPos, BlockPos targetPos) implements CustomPacketPayload {
        public static final Type<ToggleWirelessLinkPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(IntertnetMod.MODID, "toggle_wireless_link"));
        public static final StreamCodec<ByteBuf, ToggleWirelessLinkPayload> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, ToggleWirelessLinkPayload::towerPos,
                BlockPos.STREAM_CODEC, ToggleWirelessLinkPayload::targetPos,
                ToggleWirelessLinkPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record SendNetworkMessagePayload(String message) implements CustomPacketPayload {
        public static final Type<SendNetworkMessagePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(IntertnetMod.MODID, "send_net_msg"));
        public static final StreamCodec<ByteBuf, SendNetworkMessagePayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, SendNetworkMessagePayload::message,
                SendNetworkMessagePayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record TogglePhoneAntennaPayload() implements CustomPacketPayload {
        static final Type<TogglePhoneAntennaPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(IntertnetMod.MODID, "toggle_phone_antenna"));
        static final StreamCodec<FriendlyByteBuf, TogglePhoneAntennaPayload> STREAM_CODEC =
                StreamCodec.unit(new TogglePhoneAntennaPayload());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record PhoneSignal(ResourceLocation dimension, @Nullable String sim, boolean antenna, SignalManager.SignalStatus status) {
        private static void write(FriendlyByteBuf buf, PhoneSignal signal) {
            buf.writeResourceLocation(signal.dimension());
            buf.writeNullable(signal.sim(), FriendlyByteBuf::writeUtf);
            buf.writeBoolean(signal.antenna());
            var status = signal.status();
            buf.writeVarInt(status.bars());
            buf.writeNullable(status.connectedTowerPos(), (output, pos) -> output.writeBlockPos(pos));
            buf.writeDouble(status.rawDistance());
            buf.writeDouble(status.effectiveDistance());
            buf.writeVarInt(status.maxRange());
            buf.writeVarInt(status.occludingBlocks());
            buf.writeBoolean(status.missingOrWrongSim());
            buf.writeBoolean(status.unpoweredTowerNearby());
            buf.writeBoolean(status.satelliteSkyBlockedNearby());
            buf.writeBoolean(status.connectedViaSatellite());
            buf.writeBoolean(status.connectedViaGlobalSatellite());
        }

        private static PhoneSignal read(FriendlyByteBuf buf) {
            ResourceLocation dimension = buf.readResourceLocation();
            String sim = buf.readNullable(FriendlyByteBuf::readUtf);
            boolean antenna = buf.readBoolean();
            var status = new SignalManager.SignalStatus(buf.readVarInt(), buf.readNullable(input -> input.readBlockPos()),
                    buf.readDouble(), buf.readDouble(), buf.readVarInt(), buf.readVarInt(),
                    buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
            return new PhoneSignal(dimension, sim, antenna, status);
        }
    }

    public record SyncPhoneDataPayload(
            String networkLabel,
            @Nullable PhoneSignal signal,
            List<OrderSnapshot> orders,
            List<MessageSnapshot> messages,
            List<String> peers
    ) implements CustomPacketPayload {
        public static final Type<SyncPhoneDataPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(IntertnetMod.MODID, "sync_phone_data"));
        public static final StreamCodec<FriendlyByteBuf, SyncPhoneDataPayload> STREAM_CODEC = StreamCodec.of(
                (buf, val) -> {
                    buf.writeUtf(val.networkLabel());
                    buf.writeNullable(val.signal(), PhoneSignal::write);
                    buf.writeVarInt(val.orders().size());
                    for (OrderSnapshot o : val.orders()) {
                        buf.writeUtf(o.orderId());
                        buf.writeUtf(o.displayName());
                        buf.writeUtf(o.status());
                        buf.writeVarInt(o.remainingMeters());
                        buf.writeVarInt(o.progressPercent());
                        buf.writeUtf(o.destCoords());
                    }
                    buf.writeVarInt(val.messages().size());
                    for (MessageSnapshot m : val.messages()) {
                        buf.writeUtf(m.senderName());
                        buf.writeUtf(m.senderSimNet());
                        buf.writeUtf(m.text());
                    }
                    buf.writeVarInt(val.peers().size());
                    for (String p : val.peers()) {
                        buf.writeUtf(p);
                    }
                },
                buf -> {
                    String netLabel = buf.readUtf();
                    PhoneSignal signal = buf.readNullable(PhoneSignal::read);
                    int oCount = buf.readVarInt();
                    List<OrderSnapshot> orders = new ArrayList<>(oCount);
                    for (int i = 0; i < oCount; i++) {
                        orders.add(new OrderSnapshot(
                                buf.readUtf(),
                                buf.readUtf(),
                                buf.readUtf(),
                                buf.readVarInt(),
                                buf.readVarInt(),
                                buf.readUtf()
                        ));
                    }
                    int mCount = buf.readVarInt();
                    List<MessageSnapshot> msgs = new ArrayList<>(mCount);
                    for (int i = 0; i < mCount; i++) {
                        msgs.add(new MessageSnapshot(
                                buf.readUtf(),
                                buf.readUtf(),
                                buf.readUtf()
                        ));
                    }
                    int pCount = buf.readVarInt();
                    List<String> peers = new ArrayList<>(pCount);
                    for (int i = 0; i < pCount; i++) {
                        peers.add(buf.readUtf());
                    }
                    return new SyncPhoneDataPayload(netLabel, signal, orders, msgs, peers);
                }
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void registerPayloads(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("12");
        registrar.playToServer(CampCallPayload.TYPE, CampCallPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) CampDispatch.request(player, payload.confirm());
                }));
        registrar.playToServer(BuyHotelPassPayload.TYPE, BuyHotelPassPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) HotelAccess.buyPass(player, payload.entityId());
                }));
        registrar.playToClient(CampQuotePayload.TYPE, CampQuotePayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> ClientHooks.receiveCampQuote(payload)));
        registrar.playToClient(HotelReceptionPayload.TYPE, HotelReceptionPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> ClientHooks.openHotelReception(payload.entityId())));
        registrar.playToServer(RangerCallPayload.TYPE, RangerCallPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) RangerDispatch.request(player, payload.confirm());
                }));
        registrar.playToServer(RangerEscortPayload.TYPE, RangerEscortPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) RangerDispatch.escort(player, payload.entityId());
                }));
        registrar.playToClient(RangerQuotePayload.TYPE, RangerQuotePayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> ClientHooks.receiveRangerQuote(payload)));
        registrar.playToClient(RangerMeetPayload.TYPE, RangerMeetPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> ClientHooks.openRangerMeeting(payload.entityId())));
        registrar.playToServer(TogglePhoneAntennaPayload.TYPE, TogglePhoneAntennaPayload.STREAM_CODEC, (payload, context) -> {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) PersonalSatelliteAntennaItem.toggle(player);
            });
        });
        registrar.playToServer(ServiceRequestPayload.TYPE, ServiceRequestPayload.STREAM_CODEC, (payload, context) -> {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    TownServices.request(player, payload.mode(), payload.quoteOnly(), payload.option(), payload.reference());
                }
            });
        });
        registrar.playToClient(ServiceMenuPayload.TYPE, ServiceMenuPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientHooks.openRestaurantScreen(payload.chefId())));
        registrar.playToClient(ServiceQuotesPayload.TYPE, ServiceQuotesPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientHooks.receiveServiceQuotes(payload)));
        registrar.playToClient(ServiceFeedbackPayload.TYPE, ServiceFeedbackPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientHooks.receiveServiceFeedback(payload)));
        registrar.playToServer(EmergencyCallPayload.TYPE, EmergencyCallPayload.STREAM_CODEC, (payload, context) -> {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    EmergencyDispatch.request(player, payload.service());
                }
            });
        });
        registrar.playToServer(
                PrintSimCardPayload.TYPE,
                PrintSimCardPayload.STREAM_CODEC,
                ModNetwork::handlePrintSimCard
        );
        registrar.playToServer(
                ManagePhoneSimPayload.TYPE,
                ManagePhoneSimPayload.STREAM_CODEC,
                ModNetwork::handleManagePhoneSim
        );
        registrar.playToServer(
                ToggleWirelessLinkPayload.TYPE,
                ToggleWirelessLinkPayload.STREAM_CODEC,
                ModNetwork::handleToggleWirelessLink
        );
        registrar.playToServer(
                SendNetworkMessagePayload.TYPE,
                SendNetworkMessagePayload.STREAM_CODEC,
                ModNetwork::handleSendNetworkMessage
        );
        registrar.playToClient(
                SyncPhoneDataPayload.TYPE,
                SyncPhoneDataPayload.STREAM_CODEC,
                ModNetwork::handleSyncPhoneData
        );
    }

    private static void handlePrintSimCard(final PrintSimCardPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (player.distanceToSqr(payload.towerPos().getX() + 0.5, payload.towerPos().getY() + 0.5, payload.towerPos().getZ() + 0.5) > 64.0) {
                return;
            }
            BlockEntity be = player.level().getBlockEntity(payload.towerPos());
            if (!(be instanceof CellTowerBlockEntity tower)) {
                return;
            }
            if (HotelAccess.isHotelTower(tower)) {
                player.displayClientMessage(Component.literal("Hotel guest SIMs are issued at check-in."), true);
                return;
            }

            if (tower.isPublicTower()) {
                player.displayClientMessage(Component.literal("Get your Open World SIM from the vendor at the base of the tower."), true);
                return;
            }

            boolean usedFreeSim = false;
            if (tower.hasFreeSimAvailable()) {
                tower.setFreeSimClaimed(true);
                usedFreeSim = true;
            } else if (!player.isCreative()) {
                int paperSlot = -1;
                int nuggetSlot = -1;
                var inv = player.getInventory();
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    ItemStack stack = inv.getItem(i);
                    if (paperSlot == -1 && stack.is(Items.PAPER)) {
                        paperSlot = i;
                    } else if (nuggetSlot == -1 && stack.is(Items.GOLD_NUGGET)) {
                        nuggetSlot = i;
                    }
                }
                if (paperSlot == -1 || nuggetSlot == -1) {
                    player.displayClientMessage(Component.literal("Need 1 Paper + 1 Gold Nugget in your inventory to print a SIM Card!")
                            .withStyle(ChatFormatting.RED), true);
                    return;
                }
                inv.getItem(paperSlot).shrink(1);
                inv.getItem(nuggetSlot).shrink(1);
            }

            TowerNetworkManager.syncNetworkUidAcrossChain(player.level(), tower);
            String netUid = tower.getNetworkUid();
            ItemStack simCard = SimCardItem.createForNetwork(netUid);
            if (!player.getInventory().add(simCard)) {
                player.drop(simCard, false);
            }
            player.level().playSound(null, payload.towerPos(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.BLOCKS, 0.9f, 1.2f);
            if (usedFreeSim) {
                player.displayClientMessage(Component.literal("🛰 Claimed your 1 FREE Satellite SIM Card [" + netUid + "]! (Extra SIMs cost Paper + Gold Nugget)")
                        .withStyle(ChatFormatting.AQUA), true);
            } else {
                player.displayClientMessage(Component.literal("💳 Printed SIM Card for Network [" + netUid + "]!")
                        .withStyle(ChatFormatting.GREEN), true);
            }
        });
    }

    private static void handleManagePhoneSim(final ManagePhoneSimPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            ItemStack phoneStack = DeliveryManager.findPlayerPhoneStack(player);
            if (phoneStack.isEmpty()) {
                return;
            }
            String currentSim = PhoneItem.getInsertedSimNetwork(phoneStack);

            if (payload.eject()) {
                if (currentSim == null) {
                    return;
                }
                PhoneItem.setInsertedSimNetwork(phoneStack, null);
                ItemStack ejectedSim = SimCardItem.createForNetwork(currentSim);
                if (!player.getInventory().add(ejectedSim)) {
                    player.drop(ejectedSim, false);
                }
                player.level().playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 0.8f, 1.4f);
                player.displayClientMessage(Component.literal("⏏ Ejected SIM Card [" + HotelAccess.simLabel(currentSim) + "] from Phone.")
                        .withStyle(ChatFormatting.YELLOW), true);
                DeliveryManager.syncToPlayer(player);
                return;
            }

            // Find a SIM card in the player's inventory to insert
            var inv = player.getInventory();
            int simSlot = -1;
            String newSimNet = null;
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (s.is(IntertnetMod.SIM_CARD_ITEM.get())) {
                    String net = SimCardItem.getSimNetwork(s);
                    if (net != null) {
                        simSlot = i;
                        newSimNet = net;
                        break;
                    }
                }
            }

            if (simSlot == -1 || newSimNet == null) {
                player.displayClientMessage(Component.literal("No SIM Card found. Buy one at an Open World stall or print one at your own tower.")
                        .withStyle(ChatFormatting.RED), true);
                return;
            }

            inv.getItem(simSlot).shrink(1);
            if (currentSim != null) {
                ItemStack oldSim = SimCardItem.createForNetwork(currentSim);
                if (!inv.add(oldSim)) {
                    player.drop(oldSim, false);
                }
            }

            PhoneItem.setInsertedSimNetwork(phoneStack, newSimNet);
            player.level().playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_GOLD.value(), SoundSource.PLAYERS, 0.9f, 1.3f);
            player.displayClientMessage(Component.literal("💳 Inserted SIM Card [" + HotelAccess.simLabel(newSimNet) + "] into your Phone!")
                    .withStyle(ChatFormatting.GREEN), true);
            DeliveryManager.syncToPlayer(player);
        });
    }

    private static void handleToggleWirelessLink(final ToggleWirelessLinkPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (player.distanceToSqr(payload.towerPos().getX() + 0.5, payload.towerPos().getY() + 0.5, payload.towerPos().getZ() + 0.5) > 64.0) {
                return;
            }
            BlockEntity be = player.level().getBlockEntity(payload.towerPos());
            if (be instanceof CellTowerBlockEntity tower) {
                if (tower.toggleWirelessLink(payload.targetPos())) {
                    IntertnetAdvancements.award(player, IntertnetAdvancements.NETWORK_OPERATOR);
                }
                player.level().playSound(null, payload.towerPos(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.7f, 1.3f);
            }
        });
    }

    private static void handleSendNetworkMessage(final SendNetworkMessagePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            DeliveryManager.postNetworkMessage(player, payload.message());
        });
    }

    private static void handleSyncPhoneData(final SyncPhoneDataPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientPhoneState.update(payload.networkLabel(), payload.signal(), payload.orders(), payload.messages(), payload.peers());
        });
    }
}
