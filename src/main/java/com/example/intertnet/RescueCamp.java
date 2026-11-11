package com.example.intertnet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

final class RescueCamp {
    private static final BlockPos CHEST = new BlockPos(1, 0, -1);
    private static final BlockPos SIGN = new BlockPos(1, 0, 5);
    private static final List<Placement> PLAN = layout();

    private record Placement(BlockPos offset, BlockState state) {}
    private record Change(BlockPos pos, BlockState before, BlockState after) {}
    enum Step { PLACED, OCCUPIED, BLOCKED, COMPLETE }

    private final BlockPos origin;
    private final List<Change> changes = new ArrayList<>();
    private List<Placement> plan = PLAN;

    RescueCamp(BlockPos origin) {
        this.origin = origin;
    }

    static BlockPos findSite(ServerLevel level, BlockPos near) {
        for (int radius = 0; radius <= 28; radius += 2) {
            for (int x = -radius; x <= radius; x += 2) {
                for (int z = -radius; z <= radius; z += 2) {
                    if (Math.max(Math.abs(x), Math.abs(z)) != radius) continue;
                    BlockPos column = near.offset(x, 0, z);
                    if (!level.isLoaded(column)) continue;
                    BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
                    if (Math.abs(ground.getY() - near.getY()) > 16) continue;
                    if (fits(level, ground)) return ground;
                    if (fits(level, ground.above())) return ground.above();
                }
            }
        }
        return null;
    }

    static boolean fits(ServerLevel level, BlockPos origin) {
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 5; z++) {
                BlockPos pos = origin.offset(x, 0, z);
                BlockPos ground = pos.below();
                if ((!solidGround(level, ground) && (!replaceable(level, ground) || !solidGround(level, ground.below())))
                        || !replaceable(level, pos) || !replaceable(level, pos.above())
                        || !replaceable(level, pos.above(2))
                        || !replaceable(level, pos.above(3))
                        || !replaceable(level, pos.above(4))) return false;
            }
        }
        return true;
    }

    boolean prepare(ServerLevel level) {
        if (!fits(level, origin)) return false;
        var placements = new ArrayList<Placement>();
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 5; z++) {
                if (!solidGround(level, origin.offset(x, -1, z))) {
                    add(placements, x, -1, z, Blocks.COBBLESTONE.defaultBlockState());
                }
            }
        }
        placements.addAll(PLAN);
        plan = placements;
        return true;
    }

    private static boolean solidGround(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) && level.getFluidState(pos).isEmpty()
                && level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP);
    }

    Step buildNext(ServerLevel level, List<ForestRanger> crew) {
        if (changes.size() == plan.size()) return Step.COMPLETE;
        int start = changes.size();
        BlockState first = plan.get(start).state;
        boolean paired = (first.getBlock() instanceof BedBlock && first.getValue(BedBlock.PART) == BedPart.FOOT)
                || (first.getBlock() instanceof DoorBlock && first.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER);
        int end = start + (paired ? 2 : 1);
        for (int index = start; index < end; index++) {
            Placement placement = plan.get(index);
            BlockPos pos = origin.offset(placement.offset);
            if (!replaceable(level, pos)) return Step.BLOCKED;
            if (!placement.state.getCollisionShape(level, pos).isEmpty()
                    && !level.getEntitiesOfClass(LivingEntity.class, new AABB(pos),
                    entity -> entity.isAlive() && !entity.isSpectator() && !crew.contains(entity)).isEmpty()) {
                return Step.OCCUPIED;
            }
        }
        for (int index = start; index < end; index++) {
            Placement placement = plan.get(index);
            BlockPos pos = origin.offset(placement.offset);
            BlockState before = level.getBlockState(pos);
            if (!level.setBlock(pos, placement.state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE)) return Step.BLOCKED;
            changes.add(new Change(pos, before, placement.state));
            if (pos.equals(origin.offset(SIGN)) && level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
                ServiceSitePiece.writeSign(level, sign, new SignText()
                        .setMessage(0, Component.literal("DEMOLISH CAMP"))
                        .setMessage(1, Component.literal("Take your gear."))
                        .setMessage(2, Component.literal("Pull when leaving."))
                        .setMessage(3, Component.literal("Stand clear!")));
            }
        }
        if (changes.size() == plan.size()) {
            // Update the finished circuit only after the two charges and lever are in place.
            for (Change change : changes) {
                if (change.after.is(Blocks.REDSTONE_WIRE)) {
                    level.updateNeighborsAt(change.pos, Blocks.REDSTONE_WIRE);
                }
            }
            if (level.getBlockEntity(origin.offset(CHEST)) instanceof ChestBlockEntity chest) {
                chest.setItem(0, new ItemStack(Items.BAKED_POTATO, 6));
                chest.setItem(1, new ItemStack(Items.TORCH, 8));
            }
            return Step.COMPLETE;
        }
        return Step.PLACED;
    }

    void rollback(ServerLevel level) {
        for (Change change : changes.reversed()) {
            if (level.isLoaded(change.pos) && level.getBlockState(change.pos).is(change.after.getBlock())) {
                level.setBlock(change.pos, change.before, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
    }

    int progress() {
        return changes.size() * 100 / plan.size();
    }

    private static boolean replaceable(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) && level.isInWorldBounds(pos) && level.getBlockEntity(pos) == null
                && level.getFluidState(pos).isEmpty() && level.getBlockState(pos).canBeReplaced();
    }

    private static List<Placement> layout() {
        List<Placement> plan = new ArrayList<>();
        for (int y = 0; y <= 2; y++) {
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    if (Math.abs(x) != 2 && Math.abs(z) != 2) continue;
                    if (z == 2 && ((x == 0 && y < 2) || (Math.abs(x) == 2 && y == 0))) continue;
                    Block block = Math.abs(x) == 2 && Math.abs(z) == 2 ? Blocks.SPRUCE_LOG : Blocks.GREEN_WOOL;
                    add(plan, x, y, z, block.defaultBlockState());
                }
            }
        }
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) add(plan, x, 3, z, Blocks.GREEN_WOOL.defaultBlockState());
        }
        for (int z = -2; z <= 2; z++) add(plan, 0, 4, z, Blocks.BROWN_WOOL.defaultBlockState());
        BlockState door = Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
        add(plan, 0, 0, 2, door);
        add(plan, 0, 1, 2, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        BlockState bed = Blocks.ORANGE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
        add(plan, -1, 0, 0, bed);
        add(plan, -1, 0, -1, bed.setValue(BedBlock.PART, BedPart.HEAD));
        plan.add(new Placement(CHEST, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.WEST)));
        add(plan, 1, 0, 0, Blocks.CRAFTING_TABLE.defaultBlockState());
        add(plan, 1, 0, 1, Blocks.FURNACE.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
        add(plan, -1, 0, 1, Blocks.LANTERN.defaultBlockState());
        add(plan, -3, 0, 2, Blocks.LANTERN.defaultBlockState());
        add(plan, 3, 0, 2, Blocks.LANTERN.defaultBlockState());
        plan.add(new Placement(SIGN, Blocks.SPRUCE_SIGN.defaultBlockState()));
        add(plan, -2, 0, 2, Blocks.TNT.defaultBlockState());
        add(plan, 2, 0, 2, Blocks.TNT.defaultBlockState());
        add(plan, -2, 0, 3, Blocks.TARGET.defaultBlockState());
        add(plan, 2, 0, 3, Blocks.TARGET.defaultBlockState());
        BlockState wire = Blocks.REDSTONE_WIRE.defaultBlockState();
        for (int x = -2; x <= 2; x++) {
            add(plan, x, 0, 4, wire.setValue(RedStoneWireBlock.EAST, x < 2 ? RedstoneSide.SIDE : RedstoneSide.NONE)
                    .setValue(RedStoneWireBlock.WEST, x > -2 ? RedstoneSide.SIDE : RedstoneSide.NONE)
                    .setValue(RedStoneWireBlock.NORTH, Math.abs(x) == 2 ? RedstoneSide.SIDE : RedstoneSide.NONE)
                    .setValue(RedStoneWireBlock.SOUTH, x == 0 ? RedstoneSide.SIDE : RedstoneSide.NONE));
        }
        add(plan, 0, 0, 5, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR));
        return List.copyOf(plan);
    }

    private static void add(List<Placement> plan, int x, int y, int z, BlockState state) {
        plan.add(new Placement(new BlockPos(x, y, z), state));
    }
}
