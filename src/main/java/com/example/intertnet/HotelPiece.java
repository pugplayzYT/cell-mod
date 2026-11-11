package com.example.intertnet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
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
    static final BlockPos WIFI = new BlockPos(7, 1, 2);
    static final List<BlockPos> ROOM_DOORS = List.of(new BlockPos(5, 1, 4), new BlockPos(10, 1, 4),
            new BlockPos(5, 1, 9), new BlockPos(10, 1, 9));
    private final boolean wild;
    private boolean staffed;
    private boolean repairedLayout;

    HotelPiece(int x, int y, int z, boolean wild) {
        super(IntertnetMod.HOTEL_PIECE.get(), 0, new BoundingBox(x, y, z, x + 15, y + (wild ? 11 : 6), z + 15));
        this.wild = wild;
        repairedLayout = true;
        setOrientation(Direction.SOUTH);
    }

    HotelPiece(CompoundTag tag) {
        super(IntertnetMod.HOTEL_PIECE.get(), tag);
        wild = tag.getBoolean("Wild");
        staffed = tag.getBoolean("Staffed");
        repairedLayout = tag.getBoolean("RepairedLayout");
    }

    boolean isWild() {
        return wild;
    }

    static BlockPos entrance(boolean wild) {
        return new BlockPos(8, wild ? DEPTH + 1 : 1, 13);
    }

    static BlockPos reader(boolean wild, int role) {
        if (role == ENTRY) return entrance(wild).east();
        if (role == EXIT) return wild ? new BlockPos(6, 1, 6) : new BlockPos(7, 1, 12);
        return ROOM_DOORS.get(role).south();
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putBoolean("Wild", wild);
        tag.putBoolean("Staffed", staffed);
        tag.putBoolean("RepairedLayout", repairedLayout);
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
            put(level, box, x, 1, z + 1, Blocks.CRAFTING_TABLE);
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
            for (int x = 7; x <= 8; x++) {
                for (int z = 7; z <= 12; z++) buildStairColumn(level, box, x, z);
            }
            lightCheckout(level, box);
        }
        for (int x = 4; x <= 11; x++) {
            for (int z = 13; z <= 15; z++) {
                fillColumnDown(level, Blocks.COBBLESTONE.defaultBlockState(), x, surface - 1, z, box);
            }
        }
        fill(level, box, 4, surface, 13, 11, surface, 15, floor);
        generateAirBox(level, box, 4, surface + 1, 13, 11, surface + 3, 15);
        fill(level, box, 4, surface + 4, 11, 11, surface + 4, 14, Blocks.DARK_OAK_SLAB);
        fill(level, box, 4, surface + 1, 13, 4, surface + 3, 14, Blocks.DARK_OAK_LOG);
        fill(level, box, 11, surface + 1, 13, 11, surface + 3, 14, Blocks.DARK_OAK_LOG);
        fill(level, box, 4, surface + 1, 13, 11, surface + 3, 13, wild ? Blocks.DARK_OAK_PLANKS : wall);
        put(level, box, 6, surface + 2, 13, Blocks.GLASS);
        door(level, box, entrance(wild), Direction.NORTH);
        reader(level, box, ENTRY, "Insert hotel pass");
        reader(level, box, EXIT, "Checkout");
        sign(level, box, new BlockPos(10, surface + 1, 14), wild ? "WILD HOTEL" : "PLAINS HOTEL", "Room: 3 emeralds");
        put(level, box, 4, surface + 1, 15, Blocks.LANTERN);
        put(level, box, 11, surface + 1, 15, Blocks.LANTERN);
        if (!staffed) {
            Villager receptionist = EntityType.VILLAGER.create(level.getLevel());
            if (receptionist != null) {
                BlockPos position = getWorldPos(5, surface + 1, 14);
                receptionist.moveTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5, 0, 0);
                receptionist.setCustomName(Component.literal("Hotel Receptionist"));
                receptionist.setPersistenceRequired();
                receptionist.setNoAi(true);
                HotelAccess.markReceptionist(receptionist, getWorldPos(0, 0, 0), wild);
                level.addFreshEntityWithPassengers(receptionist);
                staffed = true;
            }
        }
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

    private void buildStairColumn(WorldGenLevel level, BoundingBox box, int x, int z) {
        int y = z - 6;
        fill(level, box, x, 0, z, x, y - 1, z, Blocks.STONE_BRICKS);
        generateAirBox(level, box, x, y + 1, z, x, y + 3, z);
        placeBlock(level, Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH),
                x, y, z, box);
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
        if (repairedLayout) return false;
        BoundingBox box = getBoundingBox();
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
                for (int x = 7; x <= 8; x++) {
                    if (level.getBlockState(getWorldPos(x, oldStepY, z)).is(Blocks.STONE_BRICK_STAIRS)) {
                        buildStairColumn(level, box, x, z);
                    }
                }
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
        repairedLayout = true;
        return true;
    }

    private void turnFurnace(ServerLevel level, BlockPos offset, Block block) {
        BlockPos pos = world(offset);
        BlockState state = level.getBlockState(pos);
        if (state.is(block)) {
            level.setBlock(pos, state.setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH), Block.UPDATE_CLIENTS);
        }
    }

    private void sign(WorldGenLevel level, BoundingBox box, BlockPos offset, String first, String second) {
        placeBlock(level, Blocks.SPRUCE_SIGN.defaultBlockState(), offset.getX(), offset.getY(), offset.getZ(), box);
        writeSign(level, offset, first, second);
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
