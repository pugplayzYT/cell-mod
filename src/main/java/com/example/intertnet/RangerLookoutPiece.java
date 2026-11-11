package com.example.intertnet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.phys.Vec3;

final class RangerLookoutPiece extends StructurePiece {
    static final int HEIGHT = 22;
    static final BlockPos FOOT = new BlockPos(8, 1, 9);
    static final BlockPos LANDING = new BlockPos(8, 14, 9);
    static final BlockPos SHELTER = new BlockPos(9, 14, 9);
    private boolean populated;

    RangerLookoutPiece(BlockPos origin) {
        super(IntertnetMod.RANGER_LOOKOUT_PIECE.get(), 0,
                new BoundingBox(origin.getX(), origin.getY(), origin.getZ(),
                        origin.getX() + 15, origin.getY() + HEIGHT, origin.getZ() + 15));
        setOrientation(Direction.SOUTH);
    }

    RangerLookoutPiece(CompoundTag tag) {
        super(IntertnetMod.RANGER_LOOKOUT_PIECE.get(), tag);
        populated = tag.getBoolean("Populated");
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putBoolean("Populated", populated);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                            RandomSource random, BoundingBox box, ChunkPos chunk, BlockPos pivot) {
        if (!box.isInside(getWorldPos(8, 0, 8))) return;

        generateAirBox(level, box, 3, 1, 3, 13, HEIGHT, 13);
        for (int x = 4; x <= 12; x++) {
            for (int z = 4; z <= 13; z++) {
                fillColumnDown(level, Blocks.COBBLESTONE.defaultBlockState(), x, -1, z, box);
            }
        }
        fill(level, box, 4, 0, 4, 12, 0, 13, Blocks.COBBLESTONE);
        fill(level, box, 7, 0, 9, 9, 0, 13, Blocks.MOSSY_COBBLESTONE);
        generateAirBox(level, box, 7, 1, 14, 9, 3, 15);
        fill(level, box, 7, 0, 14, 9, 0, 14, Blocks.MOSSY_COBBLESTONE);
        for (int x : new int[]{4, 12}) {
            for (int z : new int[]{4, 12}) {
                fill(level, box, x, 1, z, x, 12, z, Blocks.STRIPPED_DARK_OAK_LOG);
                put(level, box, x, 1, z, Blocks.STONE_BRICKS);
            }
            put(level, box, x, 1, 11, Blocks.LANTERN);
        }
        for (int z : new int[]{4, 12}) {
            for (int i = 0; i < 7; i++) {
                put(level, box, 5 + i, 5 + i, z, Blocks.DARK_OAK_WOOD);
                put(level, box, 11 - i, 5 + i, z, Blocks.DARK_OAK_WOOD);
            }
        }
        fill(level, box, 4, 12, 4, 12, 12, 12, Blocks.DARK_OAK_WOOD);
        fill(level, box, 3, 13, 3, 13, 13, 13, Blocks.SPRUCE_PLANKS);
        fill(level, box, 3, 14, 3, 13, 14, 13, Blocks.DARK_OAK_FENCE);
        generateAirBox(level, box, 4, 14, 4, 12, 14, 12);
        generateBox(level, box, 5, 14, 5, 11, 17, 11,
                Blocks.DARK_OAK_PLANKS.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
        generateAirBox(level, box, 6, 14, 6, 10, 17, 10);
        for (int x : new int[]{5, 11}) {
            for (int z : new int[]{5, 11}) {
                fill(level, box, x, 14, z, x, 17, z, Blocks.STRIPPED_DARK_OAK_LOG);
            }
            fill(level, box, x, 15, 7, x, 16, 9, Blocks.GLASS);
        }
        fill(level, box, 7, 15, 5, 9, 16, 5, Blocks.GLASS);
        put(level, box, 6, 15, 11, Blocks.GLASS);
        put(level, box, 10, 15, 11, Blocks.GLASS);

        BlockState door = Blocks.DARK_OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        placeBlock(level, door, 8, 14, 11, box);
        placeBlock(level, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 8, 15, 11, box);
        for (int slope = 0; slope < 4; slope++) {
            int y = 18 + slope;
            BlockState stair = Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState();
            for (int z = 4; z <= 12; z++) {
                placeBlock(level, stair.setValue(StairBlock.FACING, Direction.EAST), 4 + slope, y, z, box);
                placeBlock(level, stair.setValue(StairBlock.FACING, Direction.WEST), 12 - slope, y, z, box);
            }
            if (slope > 0) {
                fill(level, box, 5 + slope, y - 1, 5, 11 - slope, y - 1, 5, Blocks.DARK_OAK_PLANKS);
                fill(level, box, 5 + slope, y - 1, 11, 11 - slope, y - 1, 11, Blocks.DARK_OAK_PLANKS);
            }
        }
        fill(level, box, 8, 22, 4, 8, 22, 12, Blocks.DEEPSLATE_TILE_SLAB);

        // The ladder runs through the cabin floor and remains open at the top.
        fill(level, box, 8, 1, 8, 8, 14, 8, Blocks.STRIPPED_DARK_OAK_LOG);
        for (int y = 1; y <= 14; y++) {
            placeBlock(level, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH), 8, y, 9, box);
        }
        for (int x : new int[]{6, 10}) {
            BlockState bed = (x == 6 ? Blocks.GREEN_BED : Blocks.WHITE_BED).defaultBlockState()
                    .setValue(BedBlock.FACING, Direction.SOUTH);
            placeBlock(level, bed, x, 14, 7, box);
            placeBlock(level, bed.setValue(BedBlock.PART, BedPart.HEAD), x, 14, 6, box);
        }
        put(level, box, 6, 14, 9, Blocks.CARTOGRAPHY_TABLE);
        put(level, box, 10, 14, 9, Blocks.FURNACE);
        put(level, box, 10, 14, 10, Blocks.CRAFTING_TABLE);
        put(level, box, 6, 14, 10, Blocks.LANTERN);
        placeBlock(level, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 5, 17, 12, box);
        placeBlock(level, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 11, 17, 12, box);
        put(level, box, 5, 18, 12, Blocks.DARK_OAK_PLANKS);
        put(level, box, 11, 18, 12, Blocks.DARK_OAK_PLANKS);
        put(level, box, 4, 3, 12, Blocks.DARK_OAK_WOOD);
        placeBlock(level, Blocks.DARK_OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, Direction.NORTH),
                4, 3, 13, box);
        if (level.getBlockEntity(getWorldPos(4, 3, 13)) instanceof SignBlockEntity sign) {
            ServiceSitePiece.writeSign(level, sign, new SignText()
                    .setMessage(0, Component.literal("FOREST RANGER"))
                    .setMessage(1, Component.literal("LOOKOUT"))
                    .setMessage(2, Component.literal("Shelter upstairs")));
        }

        if (!populated) {
            ForestRanger ranger = IntertnetMod.FOREST_RANGER.get().create(level.getLevel());
            if (ranger != null) {
                ranger.setLookout(getWorldPos(0, 0, 0));
                Vec3 start = Vec3.atBottomCenterOf(getWorldPos(SHELTER.getX(), SHELTER.getY(), SHELTER.getZ()));
                ranger.moveTo(start.x, start.y, start.z, 0, 0);
                level.addFreshEntityWithPassengers(ranger);
                populated = true;
            }
        }
    }

    private void put(WorldGenLevel level, BoundingBox box, int x, int y, int z, Block block) {
        placeBlock(level, block.defaultBlockState(), x, y, z, box);
    }

    private void fill(WorldGenLevel level, BoundingBox box, int x1, int y1, int z1, int x2, int y2, int z2, Block block) {
        BlockState state = block.defaultBlockState();
        generateBox(level, box, x1, y1, z1, x2, y2, z2, state, state, false);
    }
}
