package com.example.intertnet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class CellTowerBlockEntity extends BlockEntity implements Container, MenuProvider {
    private static final Map<ResourceKey<Level>, Set<BlockPos>> LOADED_TOWERS_SERVER = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, Set<BlockPos>> LOADED_TOWERS_CLIENT = new ConcurrentHashMap<>();

    static final int FUEL_SLOT = 0;
    static final int ANTENNA_SLOT = 1;
    static final int SLOT_COUNT = 2;
    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private int fuelRemaining = 0;
    private int fuelMax = 0;
    private int activeRange = 64;
    private String networkUid = "";
    private boolean publicTower = false;
    private boolean guardsDeployed = false;
    private boolean villageTower = false;
    private boolean freeSimClaimed = false;
    private final Set<BlockPos> wirelessLinks = new LinkedHashSet<>();
    private int passiveTickCounter = 0;
    private long lastSkyCheckTick = Long.MIN_VALUE;
    private boolean satelliteSkyAccess;

    public final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> fuelRemaining;
                case 1 -> fuelMax;
                case 2 -> getConfiguredRange();
                case 3 -> hasFreeSimAvailable() ? 1 : 0;
                case 4 -> isSatelliteConnector() ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> fuelRemaining = value;
                case 1 -> fuelMax = value;
                case 2 -> activeRange = value;
                case 3 -> freeSimClaimed = (value == 0);
            }
        }

        @Override
        public int getCount() {
            return 5;
        }
    };

    public CellTowerBlockEntity(BlockPos pos, BlockState blockState) {
        super(IntertnetMod.CELL_TOWER_BE.get(), pos, blockState);
        if (blockState.is(IntertnetMod.SATELLITE_CONNECTOR_BLOCK.get())) {
            int hash = Math.abs((pos.getX() * 734287 + pos.getZ() * 912931 + pos.getY() * 31) % 9000) + 1000;
            this.networkUid = "SAT-" + hash;
        } else {
            this.networkUid = "NET-" + pos.getX() + "_" + pos.getZ();
        }
    }

    public boolean isSatelliteConnector() {
        return this.getBlockState().is(IntertnetMod.SATELLITE_CONNECTOR_BLOCK.get());
    }

    public boolean isPublicTower() {
        return this.publicTower;
    }

    public boolean hasFreeSimAvailable() {
        return isSatelliteConnector() && !this.freeSimClaimed;
    }

    public boolean isFreeSimClaimed() {
        return this.freeSimClaimed;
    }

    public void setFreeSimClaimed(boolean claimed) {
        this.freeSimClaimed = claimed;
        syncToClients();
    }

    public record FuelSpec(int fuelUnits, int rangeBlocks, String tierName) {}

    @Nullable
    public static FuelSpec getFuelSpec(ItemStack stack) {
        return getFuelSpec(stack, false);
    }

    @Nullable
    public static FuelSpec getFuelSpec(ItemStack stack, boolean satelliteOnlyCopper) {
        if (stack.isEmpty()) return null;
        if (satelliteOnlyCopper) {
            if (stack.is(Items.COPPER_INGOT)) {
                return new FuelSpec(300, 64, "Copper (64m)");
            }
            if (stack.is(Items.COPPER_BLOCK)) {
                return new FuelSpec(2700, 64, "Copper Block (64m)");
            }
            return null;
        }
        if (stack.is(Items.COPPER_INGOT) || stack.is(Items.REDSTONE_BLOCK)) {
            return new FuelSpec(250, 64, "Tier 1 (64m)");
        }
        if (stack.is(Items.COPPER_BLOCK)) {
            return new FuelSpec(900, 72, "Tier 1+ (72m)");
        }
        if (stack.is(Items.GOLD_INGOT) || stack.is(Items.AMETHYST_SHARD)) {
            return new FuelSpec(500, 96, "Tier 2 (96m)");
        }
        if (stack.is(Items.GOLD_BLOCK) || stack.is(Items.LAPIS_BLOCK)) {
            return new FuelSpec(3600, 112, "Tier 2+ (112m)");
        }
        if (stack.is(Items.EMERALD)) {
            return new FuelSpec(1000, 128, "Tier 3 (128m)");
        }
        if (stack.is(Items.EMERALD_BLOCK)) {
            return new FuelSpec(9000, 160, "Tier 3+ (160m)");
        }
        if (stack.is(Items.DIAMOND)) {
            return new FuelSpec(2500, 192, "Tier 4 (192m)");
        }
        if (stack.is(Items.DIAMOND_BLOCK)) {
            return new FuelSpec(22500, 224, "Tier 4+ (224m)");
        }
        if (stack.is(Items.NETHERITE_SCRAP)) {
            return new FuelSpec(4500, 224, "Tier 4+ (224m)");
        }
        if (stack.is(Items.NETHERITE_INGOT) || stack.is(Items.NETHER_STAR)) {
            return new FuelSpec(9000, 256, "Tier 5 Max (256m)");
        }
        if (stack.is(Items.NETHERITE_BLOCK)) {
            return new FuelSpec(81000, 256, "Tier 5 Ultra (256m)");
        }
        return null;
    }

    public static Set<BlockPos> getTrackedTowers(Level level) {
        Map<ResourceKey<Level>, Set<BlockPos>> map = level.isClientSide ? LOADED_TOWERS_CLIENT : LOADED_TOWERS_SERVER;
        return map.computeIfAbsent(level.dimension(), k -> ConcurrentHashMap.newKeySet());
    }

    public static void clearAllTrackedTowers() {
        LOADED_TOWERS_SERVER.clear();
        LOADED_TOWERS_CLIENT.clear();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.networkUid == null || this.networkUid.isBlank()) {
            if (isSatelliteConnector()) {
                int hash = Math.abs((this.worldPosition.getX() * 734287 + this.worldPosition.getZ() * 912931) % 9000) + 1000;
                this.networkUid = "SAT-" + hash;
            } else {
                this.networkUid = "NET-" + this.worldPosition.getX() + "_" + this.worldPosition.getZ();
            }
        }
        if (this.level != null) {
            getTrackedTowers(this.level).add(this.worldPosition.immutable());
        }
        if (this.level instanceof ServerLevel serverLevel && !isSatelliteConnector()) {
            TowerChunkLoading.remember(serverLevel, this.worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        if (this.level != null) {
            getTrackedTowers(this.level).remove(this.worldPosition);
        }
        super.setRemoved();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, CellTowerBlockEntity be) {
        getTrackedTowers(level).add(pos.immutable());
        if (level.isClientSide) {
            return;
        }
        if (be.isPublicTower()) {
            if (!be.guardsDeployed && level instanceof net.minecraft.server.level.ServerLevel serverLevel
                    && TowerGuards.deploy(serverLevel, pos)) {
                be.guardsDeployed = true;
                be.setChanged();
            }
            return;
        }

        boolean isSat = be.isSatelliteConnector();
        if (isSat && !be.hasSatelliteSkyAccess()) {
            if (state.getValue(CellTowerBlock.POWERED)) {
                level.setBlock(pos, state.setValue(CellTowerBlock.POWERED, false), Block.UPDATE_ALL);
            }
            return;
        }
        boolean changed = false;
        ItemStack slotStack = be.items.get(0);
        FuelSpec slotSpec = getFuelSpec(slotStack, isSat);

        if (slotSpec != null && (be.fuelRemaining <= 0 || (!isSat && slotSpec.rangeBlocks() > be.activeRange))) {
            slotStack.shrink(1);
            be.fuelRemaining = slotSpec.fuelUnits();
            be.fuelMax = slotSpec.fuelUnits();
            be.activeRange = isSat ? 64 : slotSpec.rangeBlocks();
            changed = true;
        }

        // Share backup fuel across wirelessly linked towers (for regular Cell Towers)
        if (!isSat && be.fuelRemaining <= 0 && level.getGameTime() % 20 == 0) {
            List<CellTowerBlockEntity> linked = TowerNetworkManager.getConnectedNetworkTowers(level, pos);
            for (CellTowerBlockEntity peer : linked) {
                if (peer != be && !peer.isSatelliteConnector() && peer.fuelRemaining > 100) {
                    int shared = Math.min(250, peer.fuelRemaining / 2);
                    peer.fuelRemaining -= shared;
                    peer.setChanged();
                    level.sendBlockUpdated(peer.getBlockPos(), peer.getBlockState(), peer.getBlockState(), Block.UPDATE_ALL);
                    be.fuelRemaining = shared;
                    be.fuelMax = Math.max(shared, peer.fuelMax);
                    be.activeRange = peer.activeRange;
                    changed = true;
                    break;
                }
            }
        }

        // Passive maintenance drain (1 unit per second)
        if (be.fuelRemaining > 0) {
            be.passiveTickCounter++;
            if (be.passiveTickCounter >= 20) {
                be.passiveTickCounter = 0;
                be.fuelRemaining = Math.max(0, be.fuelRemaining - 1);
                if (be.fuelRemaining == 0) {
                    ItemStack nextStack = be.items.get(0);
                    FuelSpec nextSpec = getFuelSpec(nextStack, isSat);
                    if (nextSpec != null) {
                        nextStack.shrink(1);
                        be.fuelRemaining = nextSpec.fuelUnits();
                        be.fuelMax = nextSpec.fuelUnits();
                        be.activeRange = isSat ? 64 : nextSpec.rangeBlocks();
                    } else if (be.villageTower && !isSat) {
                        be.fuelRemaining = 1000;
                        be.fuelMax = 1000;
                        be.activeRange = 128;
                    }
                }
                changed = true;
            }
        } else if (be.villageTower && !isSat) {
            be.fuelRemaining = 1000;
            be.fuelMax = 1000;
            be.activeRange = 128;
            changed = true;
        }

        boolean isPowered = be.fuelRemaining > 0;
        if (state.getValue(CellTowerBlock.POWERED) != isPowered) {
            level.setBlock(pos, state.setValue(CellTowerBlock.POWERED, isPowered), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            changed = true;
        }

        if (changed) {
            be.setChanged();
            level.sendBlockUpdated(pos, be.getBlockState(), be.getBlockState(), Block.UPDATE_ALL);
        }
    }

    public boolean consumeServiceFuel(int fuelBurn) {
        if (isPublicTower()) {
            return true;
        }
        if (!isPowered()) {
            return false;
        }
        boolean isSat = isSatelliteConnector();
        this.fuelRemaining -= fuelBurn;
        while (this.fuelRemaining <= 0) {
            ItemStack nextStack = this.items.get(0);
            FuelSpec nextSpec = getFuelSpec(nextStack, isSat);
            if (nextSpec != null) {
                nextStack.shrink(1);
                this.fuelRemaining += nextSpec.fuelUnits();
                this.fuelMax = nextSpec.fuelUnits();
                this.activeRange = isSat ? 64 : nextSpec.rangeBlocks();
            } else {
                this.fuelRemaining = 0;
                break;
            }
        }
        this.setChanged();
        if (this.level != null) {
            boolean isPowered = this.fuelRemaining > 0;
            BlockState st = this.getBlockState();
            if (st.getValue(CellTowerBlock.POWERED) != isPowered) {
                this.level.setBlock(this.worldPosition, st.setValue(CellTowerBlock.POWERED, isPowered), Block.UPDATE_ALL);
            } else {
                this.level.sendBlockUpdated(this.worldPosition, st, st, Block.UPDATE_ALL);
            }
        }
        return true;
    }

    public boolean isPowered() {
        return this.fuelRemaining > 0 && (!isSatelliteConnector() || hasSatelliteSkyAccess());
    }

    boolean hasSatelliteSkyAccess() {
        Level level = this.level;
        if (level == null) {
            return false;
        }
        long tick = level.getGameTime();
        if (this.lastSkyCheckTick != tick) {
            this.satelliteSkyAccess = checkSkyColumn(level);
            this.lastSkyCheckTick = tick;
        }
        return this.satelliteSkyAccess;
    }

    private boolean checkSkyColumn(Level level) {
        int x = this.worldPosition.getX();
        int z = this.worldPosition.getZ();
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        BlockPos.MutableBlockPos above = this.worldPosition.mutable();
        int obstructions = 0;
        for (int y = this.worldPosition.getY() + 1; y < surface; y++) {
            above.setY(y);
            BlockState state = level.getBlockState(above);
            if (!state.getFluidState().isEmpty() || !state.getCollisionShape(level, above).isEmpty()) {
                if (++obstructions > SatelliteConnectorBlock.MAX_SKY_OBSTRUCTIONS) {
                    return false;
                }
            }
        }
        return true;
    }

    public int getFuelRemaining() {
        return this.fuelRemaining;
    }

    public int getFuelMax() {
        return this.fuelMax;
    }

    public int getActiveRange() {
        return isPowered() ? getConfiguredRange() : 0;
    }

    public int getConfiguredRange() {
        if (isSatelliteConnector()) return 64;
        int antennaBonus = isPublicTower() ? IntertnetMod.HIGH_ANTENNA.get().rangeBonus()
                : AntennaItem.rangeBonus(items.get(ANTENNA_SLOT));
        return Math.max(64, this.activeRange) + antennaBonus;
    }

    int getLinkRange() {
        return Math.max(128, this.activeRange);
    }

    public String getNetworkUid() {
        if (this.networkUid == null || this.networkUid.isBlank()) {
            if (isSatelliteConnector()) {
                int hash = Math.abs((this.worldPosition.getX() * 734287 + this.worldPosition.getZ() * 912931) % 9000) + 1000;
                this.networkUid = "SAT-" + hash;
            } else {
                this.networkUid = "NET-" + this.worldPosition.getX() + "_" + this.worldPosition.getZ();
            }
        }
        return this.networkUid;
    }

    public void setNetworkUid(String uid) {
        this.networkUid = uid;
        syncToClients();
    }

    void initializeAsPublicTower() {
        this.publicTower = true;
        this.fuelRemaining = 1000;
        this.fuelMax = 1000;
        this.activeRange = 256;
        this.networkUid = TowerNetworkManager.PUBLIC_NETWORK;
        setChanged();
    }

    public Set<BlockPos> getWirelessLinks() {
        return Collections.unmodifiableSet(this.wirelessLinks);
    }

    public boolean toggleWirelessLink(BlockPos targetPos) {
        if (isSatelliteConnector() || isPublicTower() || targetPos == null || targetPos.equals(this.worldPosition)) return false;
        BlockPos imm = targetPos.immutable();
        if (this.level == null || !this.level.isLoaded(imm)
                || !(this.level.getBlockEntity(imm) instanceof CellTowerBlockEntity peer)
                || peer.isPublicTower() || peer.isSatelliteConnector()) {
            return false;
        }
        boolean added;
        if (this.wirelessLinks.contains(imm)) {
            this.wirelessLinks.remove(imm);
            added = false;
        } else {
            this.wirelessLinks.add(imm);
            added = true;
        }
        syncToClients();

        if (added) {
            peer.wirelessLinks.add(this.worldPosition.immutable());
            peer.syncToClients();
            TowerNetworkManager.syncNetworkUidAcrossChain(this.level, this);
        } else {
            peer.wirelessLinks.remove(this.worldPosition);
            peer.syncToClients();
        }
        return added;
    }

    public void syncToClients() {
        this.setChanged();
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, this.items, registries);
        tag.putInt("FuelRemaining", this.fuelRemaining);
        tag.putInt("FuelMax", this.fuelMax);
        tag.putInt("ActiveRange", this.activeRange);
        tag.putString("NetworkUid", getNetworkUid());
        tag.putBoolean("PublicTower", this.publicTower);
        tag.putBoolean("GuardsDeployed", this.guardsDeployed);
        tag.putBoolean("VillageTower", this.villageTower);
        tag.putBoolean("FreeSimClaimed", this.freeSimClaimed);

        ListTag linksTag = new ListTag();
        for (BlockPos p : this.wirelessLinks) {
            CompoundTag pt = new CompoundTag();
            pt.putInt("X", p.getX());
            pt.putInt("Y", p.getY());
            pt.putInt("Z", p.getZ());
            linksTag.add(pt);
        }
        tag.put("WirelessLinks", linksTag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.items.clear();
        ContainerHelper.loadAllItems(tag, this.items, registries);
        this.fuelRemaining = tag.getInt("FuelRemaining");
        this.fuelMax = tag.getInt("FuelMax");
        this.activeRange = tag.contains("ActiveRange") ? tag.getInt("ActiveRange") : 64;
        if (tag.contains("NetworkUid")) {
            this.networkUid = tag.getString("NetworkUid");
        }
        this.publicTower = tag.getBoolean("PublicTower");
        this.guardsDeployed = tag.getBoolean("GuardsDeployed");
        this.villageTower = tag.getBoolean("VillageTower");
        this.freeSimClaimed = tag.getBoolean("FreeSimClaimed");

        this.wirelessLinks.clear();
        if (tag.contains("WirelessLinks", Tag.TAG_LIST)) {
            ListTag linksTag = tag.getList("WirelessLinks", Tag.TAG_COMPOUND);
            for (int i = 0; i < linksTag.size(); i++) {
                CompoundTag pt = linksTag.getCompound(i);
                this.wirelessLinks.add(new BlockPos(pt.getInt("X"), pt.getInt("Y"), pt.getInt("Z")));
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        this.saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(isSatelliteConnector() ? "gui.intertnet.satellite_connector.title" : "gui.intertnet.cell_tower.title");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new CellTowerMenu(containerId, playerInventory, this, this.dataAccess);
    }

    @Override
    public int getContainerSize() {
        return this.items.size();
    }

    @Override
    public boolean isEmpty() {
        return this.items.get(FUEL_SLOT).isEmpty() && this.items.get(ANTENNA_SLOT).isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack res = ContainerHelper.removeItem(this.items, slot, amount);
        if (!res.isEmpty()) {
            this.setChanged();
        }
        return res;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(this.items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        this.items.set(slot, stack);
        if (stack.getCount() > this.getMaxStackSize()) {
            stack.setCount(this.getMaxStackSize());
        }
        this.setChanged();
    }

    @Override
    public boolean canPlaceItem(int index, ItemStack stack) {
        if (index == ANTENNA_SLOT) {
            return !isSatelliteConnector() && !isPublicTower() && stack.getItem() instanceof AntennaItem;
        }
        return index == FUEL_SLOT && getFuelSpec(stack, isSatelliteConnector()) != null;
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        this.items.clear();
    }
}
