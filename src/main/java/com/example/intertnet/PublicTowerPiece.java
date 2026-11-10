package com.example.intertnet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

final class PublicTowerPiece extends StructurePiece {
    static final int HEIGHT = 14;
    static final String VENDOR_TAG = "intertnet_public_network_vendor";
    private static final int PHONE_PRICE = 2;
    private static final int SIM_PRICE = 5;

    private boolean vendorSpawned;

    PublicTowerPiece(int x, int y, int z) {
        super(IntertnetMod.PUBLIC_TOWER_PIECE.get(), 0, new BoundingBox(x, y, z, x + 15, y + HEIGHT, z + 15));
        setOrientation(Direction.SOUTH);
    }

    PublicTowerPiece(CompoundTag tag) {
        super(IntertnetMod.PUBLIC_TOWER_PIECE.get(), tag);
        this.vendorSpawned = tag.getBoolean("VendorSpawned");
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putBoolean("VendorSpawned", this.vendorSpawned);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                            RandomSource random, BoundingBox box, ChunkPos chunk, BlockPos pivot) {
        BlockState foundation = Blocks.STONE_BRICKS.defaultBlockState();
        for (int x = 2; x <= 12; x++) {
            for (int z = 2; z <= 12; z++) {
                fillColumnDown(level, foundation, x, -1, z, box);
            }
        }
        generateAirBox(level, box, 2, 1, 2, 12, 4, 12);
        generateBox(level, box, 2, 0, 2, 12, 0, 12, foundation, Blocks.SMOOTH_STONE.defaultBlockState(), false);
        for (int z = 11; z <= 14; z++) {
            placeBlock(level, Blocks.POLISHED_ANDESITE.defaultBlockState(), 7, 0, z, box);
        }
        buildFence(level, box);
        buildMast(level, box);
        buildStall(level, box);
        spawnVendor(level, box);
    }

    private void buildFence(WorldGenLevel level, BoundingBox box) {
        BlockState bars = Blocks.IRON_BARS.defaultBlockState();
        BlockState across = bars.setValue(IronBarsBlock.EAST, true).setValue(IronBarsBlock.WEST, true);
        BlockState along = bars.setValue(IronBarsBlock.NORTH, true).setValue(IronBarsBlock.SOUTH, true);
        for (int offset = 3; offset < 12; offset++) {
            placeBlock(level, across, offset, 1, 2, box);
            if (offset < 6 || offset > 8) {
                placeBlock(level, across, offset, 1, 12, box);
            }
            placeBlock(level, along, 2, 1, offset, box);
            placeBlock(level, along, 12, 1, offset, box);
        }
        for (int x : new int[]{2, 12}) {
            for (int z : new int[]{2, 12}) {
                placeBlock(level, Blocks.POLISHED_ANDESITE.defaultBlockState(), x, 1, z, box);
            }
        }
        for (int x : new int[]{6, 8}) {
            placeBlock(level, Blocks.POLISHED_ANDESITE.defaultBlockState(), x, 1, 12, box);
        }
    }

    private void buildMast(WorldGenLevel level, BoundingBox box) {
        BlockState beam = Blocks.WAXED_CUT_COPPER.defaultBlockState();
        BlockState strut = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        for (int x : new int[]{5, 9}) {
            for (int z : new int[]{3, 7}) {
                placeBlock(level, Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), x, 1, z, box);
                for (int y = 2; y <= 4; y++) {
                    placeBlock(level, strut, x, y, z, box);
                }
                placeBlock(level, beam, x, 5, z, box);
                int innerX = x < 7 ? x + 1 : x - 1;
                int innerZ = z < 5 ? z + 1 : z - 1;
                placeBlock(level, beam, innerX, 5, z, box);
                placeBlock(level, beam, innerX, 5, innerZ, box);
                placeBlock(level, strut, innerX, 6, innerZ, box);
            }
        }
        generateBox(level, box, 6, 7, 4, 8, 7, 6, beam, beam, false);
        placeBlock(level, strut, 7, 8, 5, box);
        placeBlock(level, IntertnetMod.CELL_TOWER_BLOCK.get().defaultBlockState().setValue(CellTowerBlock.POWERED, true),
                7, 9, 5, box);
        BlockPos antenna = getWorldPos(7, 9, 5);
        if (box.isInside(antenna) && level.getBlockEntity(antenna) instanceof CellTowerBlockEntity tower) {
            tower.initializeAsPublicTower();
        }
        BlockState crossbar = Blocks.IRON_BARS.defaultBlockState()
                .setValue(IronBarsBlock.EAST, true).setValue(IronBarsBlock.WEST, true);
        for (int x = 5; x <= 9; x++) {
            placeBlock(level, crossbar, x, 10, 5, box);
        }
        placeBlock(level, Blocks.LIGHTNING_ROD.defaultBlockState(), 7, 11, 5, box);
        placeBlock(level, Blocks.END_ROD.defaultBlockState(), 5, 11, 5, box);
        placeBlock(level, Blocks.END_ROD.defaultBlockState(), 9, 11, 5, box);
    }

    private void buildStall(WorldGenLevel level, BoundingBox box) {
        BlockState wood = Blocks.SPRUCE_PLANKS.defaultBlockState();
        generateBox(level, box, 6, 1, 10, 8, 1, 10, wood, wood, false);
        for (int x : new int[]{5, 9}) {
            for (int y = 1; y <= 3; y++) {
                placeBlock(level, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState(), x, y, 10, box);
                placeBlock(level, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState(), x, y, 8, box);
            }
        }
        for (int x = 5; x <= 9; x++) {
            BlockState awning = (x % 2 == 0 ? Blocks.WHITE_WOOL : Blocks.CYAN_WOOL).defaultBlockState();
            for (int z = 8; z <= 11; z++) {
                placeBlock(level, awning, x, 4, z, box);
            }
        }
        placeBlock(level, Blocks.LANTERN.defaultBlockState(), 5, 1, 11, box);
        placeBlock(level, Blocks.LANTERN.defaultBlockState(), 9, 1, 11, box);
        placeBlock(level, Blocks.SPRUCE_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, Direction.NORTH),
                7, 1, 11, box);

        BlockPos signPos = getWorldPos(7, 1, 11);
        if (box.isInside(signPos) && level.getBlockEntity(signPos) instanceof SignBlockEntity sign) {
            SignText text = new SignText()
                    .setMessage(0, Component.literal("OPEN WORLD"))
                    .setMessage(1, Component.literal("Get your SIM!"))
                    .setMessage(2, Component.literal("Phone " + PHONE_PRICE + " / SIM " + SIM_PRICE))
                    .setMessage(3, Component.literal("In emeralds"));
            ServiceSitePiece.writeSign(level, sign, text);
        }
    }

    private void spawnVendor(WorldGenLevel level, BoundingBox box) {
        BlockPos pos = getWorldPos(7, 1, 9);
        if (this.vendorSpawned || !box.isInside(pos)) {
            return;
        }
        WanderingTrader vendor = EntityType.WANDERING_TRADER.create(level.getLevel());
        if (vendor == null) {
            return;
        }
        vendor.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
        vendor.addTag(VENDOR_TAG);
        vendor.setCustomName(Component.literal("Open World Vendor"));
        vendor.setCustomNameVisible(true);
        vendor.setNoAi(true);
        vendor.setInvulnerable(true);
        vendor.setPersistenceRequired();
        vendor.setDespawnDelay(0);
        var offers = vendor.getOffers();
        offers.clear();
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, PHONE_PRICE), new ItemStack(IntertnetMod.PHONE_ITEM.get()),
                Integer.MAX_VALUE, 0, 0.0F));
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, SIM_PRICE), SimCardItem.createForNetwork(TowerNetworkManager.PUBLIC_NETWORK),
                Integer.MAX_VALUE, 0, 0.0F));
        level.addFreshEntityWithPassengers(vendor);
        this.vendorSpawned = true;
    }
}
