package com.example.intertnet;

import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(IntertnetMod.MODID)
public class IntertnetMod {
    public static final String MODID = "intertnet";
    private static final String STARTER_GUIDE_TAG = "intertnet_received_starter_guide";

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MODID);
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    private static final DeferredRegister<StructureType<?>> STRUCTURES = DeferredRegister.create(Registries.STRUCTURE_TYPE, MODID);
    private static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, MODID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    static final DeferredHolder<StructureType<?>, StructureType<PublicTowerStructure>> PUBLIC_TOWER_STRUCTURE =
            STRUCTURES.register("public_cell_tower", () -> () -> PublicTowerStructure.CODEC);
    static final DeferredHolder<StructurePieceType, StructurePieceType> PUBLIC_TOWER_PIECE =
            STRUCTURE_PIECES.register("public_cell_tower", () -> (StructurePieceType.ContextlessType) PublicTowerPiece::new);
    static final DeferredHolder<StructureType<?>, StructureType<ServiceSiteStructure>> SERVICE_SITE_STRUCTURE =
            STRUCTURES.register("service_site", () -> () -> ServiceSiteStructure.CODEC);
    static final DeferredHolder<StructurePieceType, StructurePieceType> SERVICE_SITE_PIECE =
            STRUCTURE_PIECES.register("service_site", () -> (StructurePieceType.ContextlessType) ServiceSitePiece::new);
    static final DeferredHolder<StructureType<?>, StructureType<RangerLookoutStructure>> RANGER_LOOKOUT_STRUCTURE =
            STRUCTURES.register("ranger_lookout", () -> () -> RangerLookoutStructure.CODEC);
    static final DeferredHolder<StructurePieceType, StructurePieceType> RANGER_LOOKOUT_PIECE =
            STRUCTURE_PIECES.register("ranger_lookout", () -> (StructurePieceType.ContextlessType) RangerLookoutPiece::new);
    static final DeferredHolder<EntityType<?>, EntityType<ForestRanger>> FOREST_RANGER =
            ENTITIES.register("forest_ranger", () -> EntityType.Builder.<ForestRanger>of(ForestRanger::new, MobCategory.MISC)
                    .sized(0.6f, 1.95f).clientTrackingRange(8).build("intertnet:forest_ranger"));
    static final DeferredHolder<EntityType<?>, EntityType<DiningSeat>> DINING_SEAT =
            ENTITIES.register("dining_seat", () -> EntityType.Builder.<DiningSeat>of(DiningSeat::new, MobCategory.MISC)
                    .sized(0.75f, 0.45f).clientTrackingRange(6).build("intertnet:dining_seat"));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> SIM_NETWORK =
            DATA_COMPONENTS.register("sim_network", () -> DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());
    static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> PHONE_SATELLITE_ANTENNA =
            DATA_COMPONENTS.register("phone_satellite_antenna", () -> DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build());

    public static final DeferredBlock<Block> CELL_TOWER_BLOCK = BLOCKS.register("cell_tower",
            () -> new CellTowerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 6.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(CellTowerBlock.POWERED) ? 9 : 0)));

    public static final DeferredItem<BlockItem> CELL_TOWER_ITEM = ITEMS.registerSimpleBlockItem("cell_tower", CELL_TOWER_BLOCK);
    static final DeferredItem<AntennaItem> LOW_ANTENNA = ITEMS.register("low_antenna", () -> new AntennaItem(32));
    static final DeferredItem<AntennaItem> MEDIUM_ANTENNA = ITEMS.register("medium_antenna", () -> new AntennaItem(80));
    static final DeferredItem<AntennaItem> HIGH_ANTENNA = ITEMS.register("high_antenna", () -> new AntennaItem(160));

    public static final DeferredBlock<Block> SATELLITE_CONNECTOR_BLOCK = BLOCKS.register("satellite_connector",
            () -> new SatelliteConnectorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(0.8f, 3.0f)
                    .sound(SoundType.COPPER)
                    .lightLevel(state -> state.getValue(CellTowerBlock.POWERED) ? 7 : 0)));

    public static final DeferredItem<BlockItem> SATELLITE_CONNECTOR_ITEM = ITEMS.registerSimpleBlockItem("satellite_connector", SATELLITE_CONNECTOR_BLOCK);
    static final DeferredBlock<Block> SATELLITE_RELAY = BLOCKS.register("satellite_relay",
            () -> new GlobalSatelliteNetwork.RelayBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL).strength(3.5f, 6.0f).sound(SoundType.METAL).noOcclusion()
                    .lightLevel(state -> 9)));
    static final DeferredItem<Item> PERSONAL_SATELLITE_ANTENNA = ITEMS.register("personal_satellite_antenna",
            PersonalSatelliteAntennaItem::new);

    public static final DeferredItem<Item> PHONE_ITEM = ITEMS.register("phone",
            () -> new PhoneItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> SIM_CARD_ITEM = ITEMS.register("sim_card",
            () -> new SimCardItem(new Item.Properties().stacksTo(16)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CellTowerBlockEntity>> CELL_TOWER_BE =
            BLOCK_ENTITIES.register("cell_tower", () -> BlockEntityType.Builder
                    .of(CellTowerBlockEntity::new, CELL_TOWER_BLOCK.get(), SATELLITE_CONNECTOR_BLOCK.get())
                    .build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<CellTowerMenu>> CELL_TOWER_MENU =
            MENUS.register("cell_tower", () -> IMenuTypeExtension.create(CellTowerMenu::new));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> INTERTNET_TAB =
            CREATIVE_TABS.register("intertnet_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.intertnet"))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .icon(() -> new ItemStack(PHONE_ITEM.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(new ItemStack(PHONE_ITEM.get()));
                        output.accept(new ItemStack(SIM_CARD_ITEM.get()));
                        output.accept(SATELLITE_CONNECTOR_ITEM.get());
                        output.accept(PERSONAL_SATELLITE_ANTENNA.get());
                        output.accept(CELL_TOWER_ITEM.get());
                        output.accept(LOW_ANTENNA.get());
                        output.accept(MEDIUM_ANTENNA.get());
                        output.accept(HIGH_ANTENNA.get());
                        output.accept(GuidebookHelper.createSignedGuidebook());
                    }).build());

    public IntertnetMod(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);
        DATA_COMPONENTS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
        STRUCTURES.register(modEventBus);
        STRUCTURE_PIECES.register(modEventBus);
        ENTITIES.register(modEventBus);

        modEventBus.addListener(ModNetwork::registerPayloads);
        modEventBus.addListener((EntityAttributeCreationEvent event) ->
                event.put(FOREST_RANGER.get(), Pillager.createAttributes().add(Attributes.FOLLOW_RANGE, 40).build()));

        NeoForge.EVENT_BUS.addListener(this::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(this::onServerChat);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(this::onPlayerClone);
        NeoForge.EVENT_BUS.addListener(this::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);
        NeoForge.EVENT_BUS.addListener(TowerChunkLoading::tick);
        NeoForge.EVENT_BUS.addListener(GlobalSatelliteNetwork::tick);
        NeoForge.EVENT_BUS.addListener(DeliveryManager::onServerTick);
        TownServices.register();
        TowerGuards.register();
        EmergencyDispatch.register();
        RangerDispatch.register();

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientRegistrar.register(modEventBus);
        }
    }

    private void onServerAboutToStart(final ServerAboutToStartEvent event) {
        DeliveryManager.resetSessionState();
    }

    private void onServerStopped(final ServerStoppedEvent event) {
        DeliveryManager.resetSessionState();
    }

    private void onServerChat(final ServerChatEvent event) {
        event.setCanceled(true);
        ServerPlayer player = event.getPlayer();
        player.sendSystemMessage(Component.literal("📵 Server chat is disabled! You must use a Phone (connected to a Cell Tower or Satellite Connector) to message players.")
                .withStyle(ChatFormatting.RED));
        player.displayClientMessage(Component.literal("📵 Chat disabled — use your Phone's [Messages] app!")
                .withStyle(ChatFormatting.RED), true);
    }

    private void onRegisterCommands(final RegisterCommandsEvent event) {
        event.getDispatcher().register(
                net.minecraft.commands.Commands.literal("guide")
                        .requires(source -> true)
                        .executes(ctx -> {
                            CommandSourceStack source = ctx.getSource();
                            ServerPlayer player = source.getPlayerOrException();
                            ItemStack guide = GuidebookHelper.createSignedGuidebook();
                            if (!player.getInventory().add(guide)) {
                                player.drop(guide, false);
                            }
                            player.sendSystemMessage(Component.literal("📘 Added the Intertnet Manual (signed by The Internet Maker) to your inventory!").withStyle(ChatFormatting.AQUA));
                            return 1;
                        })
        );
    }

    private void onPlayerLoggedIn(final PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!persisted.getBoolean(STARTER_GUIDE_TAG)) {
            persisted.putBoolean(STARTER_GUIDE_TAG, true);
            player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
            ItemStack guide = GuidebookHelper.createSignedGuidebook();
            if (!player.getInventory().add(guide)) {
                player.drop(guide, false);
            }
            player.sendSystemMessage(Component.literal("📘 Welcome! You received the Intertnet Manual signed by The Internet Maker (use /guide anytime for another copy).")
                    .withStyle(ChatFormatting.AQUA));
        }
        DeliveryManager.syncToPlayer(player);
    }

    private void onPlayerClone(final PlayerEvent.Clone event) {
        CompoundTag oldPersisted = event.getOriginal().getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (oldPersisted.getBoolean(STARTER_GUIDE_TAG)) {
            CompoundTag newPersisted = event.getEntity().getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
            newPersisted.putBoolean(STARTER_GUIDE_TAG, true);
            event.getEntity().getPersistentData().put(Player.PERSISTED_NBT_TAG, newPersisted);
        }
    }

    private void onEntityInteract(final PlayerInteractEvent.EntityInteract event) {
        if (!event.getLevel().isClientSide && event.getTarget() instanceof AbstractVillager trader
                && !trader.isTrading()) {
            if (trader.getTags().contains(PublicTowerPiece.VENDOR_TAG)) {
                PublicTowerPiece.ensureSatelliteOffer(trader.getOffers());
            } else {
                // Old saves can still contain the original offers.
                trader.getOffers().removeIf(offer -> offer.getResult().is(PHONE_ITEM.get())
                        || offer.getResult().is(SIM_CARD_ITEM.get()));
            }
        }
    }

    private static class ClientRegistrar {
        static void register(IEventBus modEventBus) {
            modEventBus.addListener(ClientRegistrar::onRegisterMenuScreens);
            modEventBus.addListener((net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item event) -> {
                event.register((stack, layer) -> 0xFFFFBB82, LOW_ANTENNA.get());
                event.register((stack, layer) -> 0xFF91E9FF, HIGH_ANTENNA.get());
            });
            modEventBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) -> {
                event.registerEntityRenderer(DINING_SEAT.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
                event.registerEntityRenderer(FOREST_RANGER.get(), net.minecraft.client.renderer.entity.PillagerRenderer::new);
            });
            NeoForge.EVENT_BUS.addListener(CellTowerDebugRenderer::onRenderLevelStage);
            NeoForge.EVENT_BUS.addListener(CellTowerDebugRenderer::onDebugText);
            NeoForge.EVENT_BUS.addListener(PhoneHudOverlay::onRenderGui);
            NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) ->
                    ClientPhoneState.reset());
        }

        private static void onRegisterMenuScreens(final RegisterMenuScreensEvent event) {
            event.register(CELL_TOWER_MENU.get(), CellTowerScreen::new);
        }
    }
}
