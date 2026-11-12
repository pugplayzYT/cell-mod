package com.example.intertnet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import java.util.List;

final class HotelPiece extends StructurePiece {
    static final int DEPTH = 6;
    static final int ENTRY = -1;
    static final int EXIT = -2;
    private static final int CURRENT_LAYOUT = 5;
    private static final int PORCH_MIN_X = 4;
    private static final int PLAINS_PORCH_MAX_X = 11;
    private static final int WILD_PORCH_MAX_X = 12;
    private static final BlockPos WILD_CHECKOUT = new BlockPos(6, 0, 6);
    static final BlockPos WIFI = new BlockPos(7, 1, 2);
    static final List<BlockPos> ROOM_DOORS = List.of(new BlockPos(5, 1, 4), new BlockPos(10, 1, 4),
            new BlockPos(5, 1, 9), new BlockPos(10, 1, 9));
    private final boolean wild;
    private int layoutVersion;

    HotelPiece(int x, int y, int z, boolean wild) {
        super(IntertnetMod.HOTEL_PIECE.get(), 0, new BoundingBox(x, y, z, x + 15, y + (wild ? 11 : 6), z + 15));
        this.wild = wild;
        layoutVersion = CURRENT_LAYOUT;
        setOrientation(Direction.SOUTH);
    }

    HotelPiece(CompoundTag tag) {
        super(IntertnetMod.HOTEL_PIECE.get(), tag);
        wild = tag.getBoolean("Wild");
        layoutVersion = tag.contains("LayoutVersion") ? tag.getInt("LayoutVersion")
                : tag.getBoolean("RepairedLayout") ? 1 : 0;
    }

    boolean isWild() {
        return wild;
    }

    static BlockPos entrance(boolean wild) {
        return new BlockPos(8, wild ? DEPTH + 1 : 1, 13);
    }

    static BlockPos reception(boolean wild) {
        return new BlockPos(5, entrance(wild).getY(), 14);
    }

    static BlockPos reader(boolean wild, int role) {
        if (role == ENTRY) return entrance(wild).east();
        if (role == EXIT) return wild ? WILD_CHECKOUT : new BlockPos(7, 1, 12);
        return ROOM_DOORS.get(role).south();
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putBoolean("Wild", wild);
        tag.putInt("LayoutVersion", layoutVersion);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager manager, ChunkGenerator generator, RandomSource random,
                            BoundingBox box, ChunkPos chunk, BlockPos pivot) {
        if (!box.isInside(getWorldPos(8, 0, 8))) return;
        int surface = wild ? DEPTH : 0;
        Block wall = wild ? Blocks.MOSSY_STONE_BRICKS : Blocks.STRIPPED_OAK_WOOD;
        Block floor = wild ? Blocks.STONE_BRICKS : Blocks.SPRUCE_PLANKS;
        for (int x = 1; x <= 14; x++) {
            for (int z = 1; z <= 13; z++) fillColumnDown(level, Blocks.COBBLESTONE.defaultBlockState(), x, -1, z, box);
        }
        fill(level, box, 1, 0, 1, 14, 0, 13, floor);
        generateBox(level, box, 1, 1, 1, 14, 4, 13, wall.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
        generateAirBox(level, box, 2, 1, 2, 13, 3, 12);
        for (int x : new int[]{5, 10}) fill(level, box, x, 1, 2, x, 3, 12, wall);
        fill(level, box, 1, 1, 7, 5, 3, 7, wall);
        fill(level, box, 10, 1, 7, 14, 3, 7, wall);
        fill(level, box, 1, 4, 1, 14, 4, 13, wild ? Blocks.STONE_BRICKS : Blocks.DARK_OAK_PLANKS);
        if (!wild) {
            for (int x : new int[]{1, 14}) {
                for (int z : new int[]{3, 5, 9, 11}) put(level, box, x, 2, z, Blocks.GLASS);
            }
            fill(level, box, 1, 5, 1, 14, 5, 13, Blocks.DARK_OAK_SLAB);
        }

        for (int i = 0; i < ROOM_DOORS.size(); i++) {
            BlockPos roomDoor = ROOM_DOORS.get(i);
            boolean left = roomDoor.getX() == 5;
            door(level, box, roomDoor, left ? Direction.WEST : Direction.EAST);
            reader(level, box, i, "Room " + (i + 1));
            int x = left ? 2 : 13;
            int z = roomDoor.getZ();
            BlockState bed = (wild ? Blocks.GREEN_BED : Blocks.RED_BED).defaultBlockState()
                    .setValue(BedBlock.FACING, Direction.SOUTH);
            placeBlock(level, bed, x, 1, z, box);
            placeBlock(level, bed.setValue(BedBlock.PART, BedPart.HEAD), x, 1, z - 1, box);
            if (z == 4) put(level, box, x, 1, z - 2, wall);
            put(level, box, x, 1, z + 2, Blocks.CRAFTING_TABLE);
            if (z == 9) fill(level, box, left ? 2 : 11, 1, 12, left ? 4 : 13, 3, 12, wall);
            placeBlock(level, facing(Blocks.BLAST_FURNACE, Direction.SOUTH), left ? 3 : 12, 1, z + 2, box);
            placeBlock(level, facing(Blocks.FURNACE, Direction.SOUTH), left ? 4 : 11, 1, z + 2, box);
            put(level, box, left ? 4 : 11, 1, z - 1, Blocks.LANTERN);
        }
        put(level, box, 9, 1, 3, Blocks.LANTERN);
        put(level, box, 9, 1, 11, Blocks.LANTERN);
        if (wild) {
            placeBlock(level, IntertnetMod.CELL_TOWER_BLOCK.get().defaultBlockState(), WIFI.getX(), WIFI.getY(), WIFI.getZ(), box);
            if (level.getBlockEntity(world(WIFI)) instanceof CellTowerBlockEntity tower) {
                tower.setNetworkUid(HotelAccess.wifiNetwork(getWorldPos(0, 0, 0)));
                tower.setItem(CellTowerBlockEntity.FUEL_SLOT, new ItemStack(Items.COPPER_INGOT, 16));
            }
            buildWildStaircase(level, box);
            lightCheckout(level, box);
        }
        int porchMaxX = wild ? WILD_PORCH_MAX_X : PLAINS_PORCH_MAX_X;
        for (int x = PORCH_MIN_X; x <= porchMaxX; x++) {
            for (int z = 13; z <= 15; z++) {
                fillColumnDown(level, Blocks.COBBLESTONE.defaultBlockState(), x, surface - 1, z, box);
            }
        }
        fill(level, box, PORCH_MIN_X, surface, 13, porchMaxX, surface, 15, floor);
        generateAirBox(level, box, PORCH_MIN_X, surface + 1, 13, porchMaxX, surface + 3, 15);
        if (wild) {
            fill(level, box, 6, surface + 1, 13, 10, surface + 3, 13, Blocks.DARK_OAK_PLANKS);
            fill(level, box, 6, surface + 4, 13, 10, surface + 4, 13, Blocks.DARK_OAK_SLAB);
        } else {
            // The solid roof layer already covers the porch through z = 13.
            fill(level, box, PORCH_MIN_X, surface + 4, 14, porchMaxX, surface + 4, 14, Blocks.DARK_OAK_SLAB);
            fill(level, box, 4, surface + 1, 13, 4, surface + 3, 14, Blocks.DARK_OAK_LOG);
            fill(level, box, 11, surface + 1, 13, 11, surface + 3, 14, Blocks.DARK_OAK_LOG);
            fill(level, box, 4, surface + 1, 13, 11, surface + 3, 13, wall);
        }
        put(level, box, wild ? 7 : 6, surface + 2, 13, Blocks.GLASS);
        door(level, box, entrance(wild), Direction.NORTH);
        reader(level, box, ENTRY, "Insert hotel pass");
        reader(level, box, EXIT, "Checkout");
        entranceSign(level, box, surface);
        put(level, box, PORCH_MIN_X, surface + 1, 15, Blocks.LANTERN);
        put(level, box, porchMaxX, surface + 1, 15, Blocks.LANTERN);
        layoutVersion = CURRENT_LAYOUT;
    }

    private void reader(WorldGenLevel level, BoundingBox box, int role, String label) {
        BlockPos offset = reader(wild, role);
        placeBlock(level, Blocks.HOPPER.defaultBlockState(), offset.getX(), offset.getY(), offset.getZ(), box);
        if (level.getBlockEntity(world(offset)) instanceof HopperBlockEntity hopper) {
            HotelAccess.markReader(hopper, getWorldPos(0, 0, 0), wild, role);
        }
        if (role == ENTRY) {
            BlockPos sign = new BlockPos(9, offset.getY() + 1, 14);
            put(level, box, 9, offset.getY() + 1, 13, wild ? Blocks.DARK_OAK_PLANKS : Blocks.OAK_PLANKS);
            wallSign(level, box, sign, Direction.NORTH, label, "Pass returned");
        } else if (role == EXIT) {
            wallSign(level, box, offset.above(), wild ? Direction.EAST : Direction.SOUTH, label, "Pass ends stay");
        } else {
            Direction hall = roomHallDirection(role);
            wallSign(level, box, offset.above().relative(hall), hall, label, "Pass returned");
        }
    }

    private void buildWildStaircase(WorldGenLevel level, BoundingBox box) {
        BlockState stairs = Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
        fill(level, box, 7, 0, 7, 9, 0, 12, Blocks.STONE_BRICKS);
        // The lower hallway crosses beneath the upper flight to reach the rear rooms.
        fill(level, box, 7, 1, 8, 9, 1, 8, Blocks.STONE_BRICKS);
        for (int z = 7; z <= 12; z++) {
            int y = z - 6;
            if (z >= 10) fill(level, box, 7, 3, z, 9, y - 1, z, Blocks.STONE_BRICKS);
            generateAirBox(level, box, 7, y + 1, z, 9, z == 7 ? 4 : 9, z);
            for (int x = 7; x <= 9; x++) placeBlock(level, stairs, x, y, z, box);
        }
        fill(level, box, 6, 5, 7, 6, 9, 12, Blocks.DARK_OAK_PLANKS);
        fill(level, box, 10, 5, 7, 10, 9, 12, Blocks.DARK_OAK_PLANKS);
        fill(level, box, 7, 5, 7, 9, 9, 7, Blocks.DARK_OAK_PLANKS);
        fill(level, box, 6, 10, 7, 10, 10, 12, Blocks.DARK_OAK_SLAB);
        placeBlock(level, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 8, 9, 9, box);
    }

    private void lightCheckout(WorldGenLevel level, BoundingBox box) {
        placeBlock(level, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 6, 3, 6, box);
    }

    private static Direction roomHallDirection(int role) {
        return ROOM_DOORS.get(role).getX() == 5 ? Direction.EAST : Direction.WEST;
    }

    private static BlockState facing(Block block, Direction direction) {
        return block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, direction);
    }

    boolean repairLayout(ServerLevel level) {
        if (layoutVersion >= CURRENT_LAYOUT) return false;
        BoundingBox box = getBoundingBox();
        if (layoutVersion == 0) repairOriginalLayout(level, box);
        if (layoutVersion < 2) {
            repairWorkstations(level, box);
            if (wild) {
                repairWildStairs(level, box);
                moveCheckout(level, box);
            }
        }
        if (layoutVersion < 3) {
            repairExterior(level, box);
            for (int x : new int[]{2, 13}) {
                if (level.getBlockState(getWorldPos(x, 1, 3)).getBlock() instanceof BedBlock
                        && level.isEmptyBlock(getWorldPos(x, 1, 2))) {
                    put(level, box, x, 1, 2, wild ? Blocks.MOSSY_STONE_BRICKS : Blocks.STRIPPED_OAK_WOOD);
                }
            }
        }
        if (wild && layoutVersion < 4) balanceWildPorch(level, box);
        if (!wild) repairPlainsRoof(level, box);
        layoutVersion = CURRENT_LAYOUT;
        return true;
    }

    private void repairPlainsRoof(ServerLevel level, BoundingBox box) {
        BlockState slab = Blocks.DARK_OAK_SLAB.defaultBlockState();
        for (int x = PORCH_MIN_X; x <= PLAINS_PORCH_MAX_X; x++) {
            for (int z = 11; z <= 13; z++) {
                BlockPos pos = getWorldPos(x, 4, z);
                if (level.getBlockState(pos) == slab && level.getBlockState(pos.above()) == slab) {
                    put(level, box, x, 4, z, Blocks.DARK_OAK_PLANKS);
                }
            }
        }
    }

    private void balanceWildPorch(ServerLevel level, BoundingBox box) {
        for (int z = 13; z <= 15; z++) {
            if (!level.getBlockState(getWorldPos(PORCH_MIN_X, DEPTH, z)).is(Blocks.STONE_BRICKS)) continue;
            BlockPos edge = getWorldPos(WILD_PORCH_MAX_X, DEPTH, z);
            if (level.getBlockEntity(edge) != null) continue;
            fillColumnDown(level, Blocks.COBBLESTONE.defaultBlockState(), WILD_PORCH_MAX_X, DEPTH - 1, z, box);
            put(level, box, WILD_PORCH_MAX_X, DEPTH, z, Blocks.STONE_BRICKS);
        }
        BlockPos oldLantern = getWorldPos(WILD_PORCH_MAX_X - 1, DEPTH + 1, 15);
        if (level.getBlockState(oldLantern).is(Blocks.LANTERN)
                && level.isEmptyBlock(oldLantern.east())
                && level.getBlockState(oldLantern.east().below()).is(Blocks.STONE_BRICKS)) {
            put(level, box, WILD_PORCH_MAX_X, DEPTH + 1, 15, Blocks.LANTERN);
            level.removeBlock(oldLantern, false);
        }
    }

    private void repairExterior(ServerLevel level, BoundingBox box) {
        int surface = wild ? DEPTH : 0;
        BlockPos oldSign = getWorldPos(10, surface + 1, 14);
        if (level.getBlockState(oldSign).is(Blocks.SPRUCE_SIGN) && level.isEmptyBlock(oldSign.above())) {
            level.removeBlock(oldSign, false);
            entranceSign(level, box, surface);
        }
        if (!wild) return;
        if (level.getBlockState(getWorldPos(6, surface + 2, 13)).is(Blocks.GLASS)
                && level.getBlockState(getWorldPos(7, surface + 2, 13)).is(Blocks.DARK_OAK_PLANKS)) {
            put(level, box, 6, surface + 2, 13, Blocks.DARK_OAK_PLANKS);
            put(level, box, 7, surface + 2, 13, Blocks.GLASS);
        }
        for (int x = 4; x <= 11; x++) {
            removeExteriorBlock(level, x, surface + 4, 14, Blocks.DARK_OAK_SLAB);
            if (x >= 6 && x <= 10) continue;
            for (int z = 11; z <= 13; z++) removeExteriorBlock(level, x, surface + 4, z, Blocks.DARK_OAK_SLAB);
            for (int y = surface + 1; y <= surface + 3; y++) {
                removeExteriorBlock(level, x, y, 13, Blocks.DARK_OAK_PLANKS);
                if (x == 4 || x == 11) removeExteriorBlock(level, x, y, 14, Blocks.DARK_OAK_LOG);
            }
        }
    }

    private void removeExteriorBlock(ServerLevel level, int x, int y, int z, Block block) {
        BlockPos pos = getWorldPos(x, y, z);
        if (level.getBlockState(pos).is(block)) level.removeBlock(pos, false);
    }

    private void repairOriginalLayout(ServerLevel level, BoundingBox box) {
        for (int role = 0; role < ROOM_DOORS.size(); role++) {
            BlockPos door = ROOM_DOORS.get(role);
            boolean left = door.getX() == 5;
            BlockPos cap = reader(wild, role).above();
            Direction hall = roomHallDirection(role);
            BlockPos label = cap.relative(hall);
            BlockState oldCap = level.getBlockState(world(cap));
            if (oldCap.is(Blocks.SPRUCE_WALL_SIGN) || oldCap.isAir()) {
                put(level, box, cap.getX(), cap.getY(), cap.getZ(), wild ? Blocks.MOSSY_STONE_BRICKS : Blocks.STRIPPED_OAK_WOOD);
                if (level.isEmptyBlock(world(label))) wallSign(level, box, label, hall, "Room " + (role + 1), "Pass returned");
            }
            turnFurnace(level, new BlockPos(left ? 3 : 12, 1, door.getZ() + 2), Blocks.BLAST_FURNACE);
            turnFurnace(level, new BlockPos(left ? 4 : 11, 1, door.getZ() + 2), Blocks.FURNACE);
        }
        if (wild) {
            for (int z = 7; z <= 12; z++) {
                int oldStepY = z - 7;
                for (int x : new int[]{6, 9}) {
                    BlockPos cap = getWorldPos(x, oldStepY + 3, z);
                    if (level.getBlockState(cap).is(Blocks.DARK_OAK_PLANKS)) {
                        level.setBlock(cap, (oldStepY + 3 == 4 ? Blocks.STONE_BRICKS : Blocks.AIR).defaultBlockState(),
                                Block.UPDATE_CLIENTS);
                    }
                }
            }
            BlockPos oldLight = getWorldPos(7, 2, 6);
            if (level.getBlockState(oldLight).is(Blocks.LANTERN)) level.removeBlock(oldLight, false);
            if (level.isEmptyBlock(getWorldPos(6, 3, 6))) lightCheckout(level, box);
        }
    }

    private void repairWorkstations(ServerLevel level, BoundingBox box) {
        Block wall = wild ? Blocks.MOSSY_STONE_BRICKS : Blocks.STRIPPED_OAK_WOOD;
        for (BlockPos door : ROOM_DOORS) {
            boolean left = door.getX() == 5;
            int x = left ? 2 : 13;
            int z = door.getZ();
            BlockPos oldTable = getWorldPos(x, 1, z + 1);
            if (level.getBlockState(oldTable).is(Blocks.CRAFTING_TABLE) && level.isEmptyBlock(getWorldPos(x, 1, z + 2))) {
                put(level, box, x, 1, z + 2, Blocks.CRAFTING_TABLE);
                level.removeBlock(oldTable, false);
            }
            if (z != 9) continue;
            for (int backX = left ? 2 : 11; backX <= (left ? 4 : 13); backX++) {
                for (int y = 1; y <= 3; y++) {
                    if (level.isEmptyBlock(getWorldPos(backX, y, 12))) put(level, box, backX, y, 12, wall);
                }
            }
        }
    }

    private void repairWildStairs(ServerLevel level, BoundingBox box) {
        for (int x = 7; x <= 8; x++) {
            for (int z = 9; z <= 12; z++) {
                for (int y = 1; y <= 2; y++) {
                    BlockPos pos = getWorldPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.is(Blocks.STONE_BRICKS) || state.is(Blocks.STONE_BRICK_STAIRS)) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        buildWildStaircase(level, box);
    }

    private void moveCheckout(ServerLevel level, BoundingBox box) {
        BlockPos oldHopper = world(WILD_CHECKOUT.above());
        if (!(level.getBlockEntity(oldHopper) instanceof HopperBlockEntity oldReader)) return;
        BlockPos floor = world(WILD_CHECKOUT);
        if (!level.getBlockState(floor).is(Blocks.STONE_BRICKS) && !level.isEmptyBlock(floor)) return;
        level.setBlock(floor, Blocks.HOPPER.defaultBlockState(), Block.UPDATE_CLIENTS);
        if (!(level.getBlockEntity(floor) instanceof HopperBlockEntity newReader)) return;
        HotelAccess.markReader(newReader, getWorldPos(0, 0, 0), true, EXIT);
        for (int slot = 0; slot < oldReader.getContainerSize(); slot++) {
            newReader.setItem(slot, oldReader.removeItemNoUpdate(slot));
        }
        newReader.setChanged();
        if (level.getBlockState(oldHopper.above()).is(Blocks.SPRUCE_WALL_SIGN)) level.removeBlock(oldHopper.above(), false);
        wallSign(level, box, WILD_CHECKOUT.above(), Direction.EAST, "Checkout", "Pass ends stay");
    }

    private void turnFurnace(ServerLevel level, BlockPos offset, Block block) {
        BlockPos pos = world(offset);
        BlockState state = level.getBlockState(pos);
        if (state.is(block)) {
            level.setBlock(pos, state.setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH), Block.UPDATE_CLIENTS);
        }
    }

    private void entranceSign(WorldGenLevel level, BoundingBox box, int surface) {
        wallSign(level, box, new BlockPos(10, surface + 2, 14), Direction.NORTH,
                wild ? "WILD HOTEL" : "PLAINS HOTEL", "Room: 3 emeralds");
    }

    private void wallSign(WorldGenLevel level, BoundingBox box, BlockPos offset, Direction facing, String first, String second) {
        placeBlock(level, Blocks.SPRUCE_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, facing),
                offset.getX(), offset.getY(), offset.getZ(), box);
        writeSign(level, offset, first, second);
    }

    private void writeSign(WorldGenLevel level, BlockPos offset, String first, String second) {
        if (level.getBlockEntity(world(offset)) instanceof SignBlockEntity sign) {
            ServiceSitePiece.writeSign(level, sign, new SignText().setMessage(0, Component.literal(first))
                    .setMessage(1, Component.literal(second)));
        }
    }

    private void door(WorldGenLevel level, BoundingBox box, BlockPos offset, Direction facing) {
        BlockState state = Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing);
        placeBlock(level, state, offset.getX(), offset.getY(), offset.getZ(), box);
        placeBlock(level, state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER),
                offset.getX(), offset.getY() + 1, offset.getZ(), box);
    }

    private BlockPos world(BlockPos offset) {
        return getWorldPos(offset.getX(), offset.getY(), offset.getZ());
    }

    private void put(WorldGenLevel level, BoundingBox box, int x, int y, int z, Block block) {
        placeBlock(level, block.defaultBlockState(), x, y, z, box);
    }

    private void fill(WorldGenLevel level, BoundingBox box, int x1, int y1, int z1, int x2, int y2, int z2, Block block) {
        BlockState state = block.defaultBlockState();
        generateBox(level, box, x1, y1, z1, x2, y2, z2, state, state, false);
    }
}
