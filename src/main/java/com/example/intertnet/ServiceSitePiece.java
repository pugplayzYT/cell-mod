package com.example.intertnet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

final class ServiceSitePiece extends StructurePiece {
    static final int HEIGHT = 12;
    static final BlockPos STOCK = new BlockPos(6, 1, 3);
    static final BlockPos OVEN = new BlockPos(8, 1, 3);
    static final BlockPos CHEF = new BlockPos(7, 1, 8);
    static final BlockPos KITCHEN = new BlockPos(7, 1, 3);
    static final BlockPos KITCHEN_DOOR = new BlockPos(7, 1, 5);
    static final BlockPos PARKING = new BlockPos(7, 1, 8);
    static final BlockPos ENTRANCE = new BlockPos(7, 1, 14);
    static final BlockPos EMERGENCY_BAY = new BlockPos(7, 1, 11);
    static final List<BlockPos> MEDIC_CHAIRS = List.of(new BlockPos(5, 1, 8), new BlockPos(9, 1, 8));
    static final String CHEF_TAG = "intertnet_chef";
    static final String KITCHEN_TAG = "intertnet_kitchen";
    static final String SITE_POS = "IntertnetSite";
    private static final int APPROACH_START = 13;
    private static final int APPROACH_END = 15;
    private final boolean restaurant;
    private final boolean rooftop;
    @Nullable
    private final EmergencyService emergency;
    private boolean populated;
    private boolean narrowApproach;

    ServiceSitePiece(int x, int y, int z, boolean restaurant, boolean rooftop, @Nullable EmergencyService emergency) {
        super(IntertnetMod.SERVICE_SITE_PIECE.get(), 0, new BoundingBox(x, y, z, x + 15, y + HEIGHT, z + 15));
        setOrientation(Direction.SOUTH);
        this.restaurant = restaurant;
        this.rooftop = rooftop;
        this.emergency = emergency;
    }

    ServiceSitePiece(CompoundTag tag) {
        super(IntertnetMod.SERVICE_SITE_PIECE.get(), tag);
        restaurant = tag.getBoolean("Restaurant");
        rooftop = tag.getBoolean("Rooftop");
        emergency = EmergencyService.CODEC.byName(tag.getString("Emergency"));
        populated = tag.getBoolean("Populated");
        narrowApproach = tag.getBoolean("NarrowApproach");
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putBoolean("Restaurant", restaurant);
        tag.putBoolean("Rooftop", rooftop);
        tag.putBoolean("Populated", populated);
        if (emergency != null) {
            tag.putString("Emergency", emergency.getSerializedName());
            tag.putBoolean("NarrowApproach", narrowApproach);
        }
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                            RandomSource random, BoundingBox box, ChunkPos chunk, BlockPos pivot) {
        if (!box.isInside(getWorldPos(7, 0, 7))) {
            return;
        }
        Block floor = restaurant ? Blocks.SPRUCE_PLANKS : Blocks.POLISHED_ANDESITE;
        Block wall = restaurant ? Blocks.STRIPPED_OAK_WOOD : stationWall(emergency);
        Block roof = restaurant ? Blocks.DARK_OAK_PLANKS
                : emergency == EmergencyService.MEDIC ? Blocks.WHITE_CONCRETE : Blocks.DEEPSLATE_TILES;
        int west = restaurant ? 1 : 2;
        int east = restaurant ? 13 : 12;
        int approachWest = emergency == null ? 5 : 7;
        int approachEast = emergency == null ? 9 : 8;
        for (int x = west; x <= east; x++) {
            for (int z = 2; z <= 12; z++) {
                fillColumnDown(level, Blocks.STONE_BRICKS.defaultBlockState(), x, -1, z, box);
            }
        }
        for (int x = approachWest; x <= approachEast; x++) {
            for (int z = APPROACH_START; z <= APPROACH_END; z++) {
                fillColumnDown(level, Blocks.STONE_BRICKS.defaultBlockState(), x, -1, z, box);
            }
        }
        fill(level, box, west, 0, 2, east, 0, 12, floor);
        generateBox(level, box, west, 1, 2, east, 4, 12, wall.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
        // Keep the walls four blocks high and clear the full interior.
        generateAirBox(level, box, west + 1, 1, 3, east - 1, 4, 11);
        fill(level, box, west, 5, 2, east, 5, 12, roof);
        fill(level, box, approachWest, 0, 12, approachEast, 0, APPROACH_END, Blocks.POLISHED_ANDESITE);
        generateAirBox(level, box, approachWest, 1, APPROACH_START, approachEast, 3, APPROACH_END);
        for (int x : new int[]{4, 5, 10, 11}) {
            put(level, box, x, 2, 12, Blocks.GLASS);
        }
        for (int x : new int[]{west, east}) {
            for (int z : new int[]{7, 8, 9}) {
                put(level, box, x, 2, z, Blocks.GLASS);
            }
        }
        if (!restaurant) {
            put(level, box, 3, 1, 11, Blocks.LANTERN);
            put(level, box, 11, 1, 11, Blocks.LANTERN);
        }
        if (restaurant) {
            placeWallSign(level, box, 9, 3, 13, "RESTAURANT", "Order inside", "Eat in");
            buildRestaurant(level, box);
        } else if (emergency != null) {
            buildEmergencyStation(level, box);
            narrowApproach = true;
        } else {
            placeWallSign(level, box, 9, 3, 13, "RIDESHARE", "Dispatch center", "Book by phone");
            buildStation(level, box);
        }
        populated = true;
    }

    private void buildRestaurant(WorldGenLevel level, BoundingBox box) {
        generateAirBox(level, box, 6, 1, 12, 8, 3, 12);
        fill(level, box, 2, 1, 5, 12, 4, 5, Blocks.OAK_PLANKS);
        placeIronDoor(level, box, KITCHEN_DOOR.getX(), KITCHEN_DOOR.getZ(), DoorHingeSide.LEFT);
        placeWallSign(level, box, 6, 2, 6, "Kitchen", "Staff only");
        placeWallSign(level, box, 8, 2, 6, "Dining room", "Right-click", "a chair to sit");
        for (int x : new int[]{3, 11}) {
            for (int tableZ : new int[]{7, 10}) {
                put(level, box, x, 1, tableZ, Blocks.SPRUCE_FENCE);
                put(level, box, x, 2, tableZ, Blocks.SPRUCE_PRESSURE_PLATE);
                placeSeat(level, box, x, tableZ - 1, 1);
                placeSeat(level, box, x, tableZ + 1, -1);
                hangLantern(level, box, x < 7 ? x + 1 : x - 1, tableZ);
            }
        }
        fill(level, box, 5, 1, 6, 9, 1, 11, Blocks.RED_CARPET);
        put(level, box, 4, 1, 3, Blocks.CRAFTING_TABLE);
        put(level, box, 10, 1, 3, Blocks.CAULDRON);
        hangLantern(level, box, 5, 3);
        hangLantern(level, box, 9, 3);
        if (!populated) {
            BlockPos stockPos = getWorldPos(STOCK.getX(), STOCK.getY(), STOCK.getZ());
            BlockPos ovenPos = getWorldPos(OVEN.getX(), OVEN.getY(), OVEN.getZ());
            put(level, box, STOCK.getX(), STOCK.getY(), STOCK.getZ(), Blocks.CHEST);
            placeBlock(level, Blocks.FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING, Direction.NORTH),
                    OVEN.getX(), OVEN.getY(), OVEN.getZ(), box);
            if (level.getBlockEntity(stockPos) instanceof BaseContainerBlockEntity chest) {
                secureContainer(level, chest);
                for (Meal meal : Meal.values()) {
                    chest.setItem(meal.ordinal(), new ItemStack(meal.raw, 32));
                }
            }
            if (level.getBlockEntity(ovenPos) instanceof BaseContainerBlockEntity oven) {
                secureContainer(level, oven);
            }
            Villager chef = staff(level, "Restaurant Chef");
            if (chef != null) {
                chef.addTag(CHEF_TAG);
                chef.getPersistentData().putLong(SITE_POS, getWorldPos(0, 0, 0).asLong());
                spawn(level, chef, getWorldPos(CHEF.getX(), CHEF.getY(), CHEF.getZ()), 0);
            }
        }
        if (rooftop) {
            put(level, box, 7, 6, 6, Blocks.POLISHED_DEEPSLATE);
            put(level, box, 7, 7, 6, Blocks.WAXED_CUT_COPPER);
            placeBlock(level, IntertnetMod.CELL_TOWER_BLOCK.get().defaultBlockState().setValue(CellTowerBlock.POWERED, true),
                    7, 8, 6, box);
            if (level.getBlockEntity(getWorldPos(7, 8, 6)) instanceof CellTowerBlockEntity tower) {
                tower.initializeAsPublicTower();
            }
            put(level, box, 7, 9, 6, Blocks.IRON_BARS);
            put(level, box, 7, 10, 6, Blocks.LIGHTNING_ROD);
        }
    }

    private void placeSeat(WorldGenLevel level, BoundingBox box, int x, int z, int towardTable) {
        // The lower step faces the table. SOUTH orientation mirrors north and south block states.
        Direction back = towardTable > 0 ? Direction.SOUTH : Direction.NORTH;
        placeBlock(level, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, back), x, 1, z, box);
        if (!populated) {
            DiningSeat seat = IntertnetMod.DINING_SEAT.get().create(level.getLevel());
            if (seat != null) {
                Vec3 pos = Vec3.atBottomCenterOf(getWorldPos(x, 1, z)).add(0, 0.48, 0.2 * towardTable);
                seat.moveTo(pos.x, pos.y, pos.z, towardTable > 0 ? 0 : 180, 0);
                level.addFreshEntityWithPassengers(seat);
            }
        }
    }

    private void hangLantern(WorldGenLevel level, BoundingBox box, int x, int z) {
        put(level, box, x, 4, z, Blocks.CHAIN);
        placeBlock(level, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), x, 3, z, box);
    }

    private void placeWallSign(WorldGenLevel level, BoundingBox box, int x, int y, int z, String... lines) {
        placeBlock(level, Blocks.SPRUCE_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, Direction.NORTH), x, y, z, box);
        if (level.getBlockEntity(getWorldPos(x, y, z)) instanceof SignBlockEntity sign) {
            SignText text = new SignText();
            for (int i = 0; i < lines.length; i++) {
                text = text.setMessage(i, Component.literal(lines[i]));
            }
            writeSign(level, sign, text);
        }
    }

    private void buildStation(WorldGenLevel level, BoundingBox box) {
        fill(level, box, 5, 0, 3, 9, 0, 14, Blocks.LIGHT_GRAY_CONCRETE);
        put(level, box, 7, 1, 4, Blocks.SMOOTH_STONE);
        put(level, box, 6, 2, 12, Blocks.IRON_BARS);
        put(level, box, 9, 2, 12, Blocks.IRON_BARS);
        for (int x : new int[]{7, 8}) {
            placeIronDoor(level, box, x, 12, x == 7 ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT);
        }
        if (!populated) {
            spawn(level, staff(level, "Rideshare Dispatcher"), getWorldPos(5, 1, 5), 0);
            spawn(level, staff(level, "Rideshare Attendant"), getWorldPos(10, 1, 5), 0);
        }
    }

    private void buildEmergencyStation(WorldGenLevel level, BoundingBox box) {
        boolean medic = emergency == EmergencyService.MEDIC;
        Block stripe = medic ? Blocks.RED_CONCRETE : Blocks.BLUE_CONCRETE;
        fill(level, box, 2, 4, 12, 12, 4, 12, stripe);
        fill(level, box, 7, 0, 3, 8, 0, 11, Blocks.LIGHT_GRAY_CONCRETE);
        placeIronDoor(level, box, 7, 12, DoorHingeSide.LEFT);
        placeIronDoor(level, box, 8, 12, DoorHingeSide.RIGHT);
        placeWallSign(level, box, 9, 3, 13, medic ? "MEDIC STATION" : "POLICE STATION", "Free dispatch", "Call by phone");
        for (int x : new int[]{4, 10}) {
            hangLantern(level, box, x, 4);
            hangLantern(level, box, x, 9);
        }
        if (medic) {
            for (BlockPos chair : MEDIC_CHAIRS) {
                placeBlock(level, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH),
                        chair.getX(), chair.getY(), chair.getZ(), box);
            }
            fill(level, box, 6, 6, 10, 8, 6, 10, Blocks.RED_CONCRETE);
            fill(level, box, 7, 6, 9, 7, 6, 11, Blocks.RED_CONCRETE);
            for (int x : new int[]{4, 10}) {
                BlockState bed = Blocks.WHITE_BED.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.SOUTH);
                placeBlock(level, bed, x, 1, 4, box);
                placeBlock(level, bed.setValue(net.minecraft.world.level.block.BedBlock.PART,
                        net.minecraft.world.level.block.state.properties.BedPart.HEAD), x, 1, 3, box);
                put(level, box, x, 1, 7, Blocks.BARREL);
                put(level, box, x, 1, 8, Blocks.CAULDRON);
            }
        } else {
            fill(level, box, 2, 6, 12, 12, 6, 12, Blocks.BLUE_CONCRETE);
            for (int x : new int[]{4, 10}) {
                fill(level, box, x, 1, 3, x, 2, 6, Blocks.IRON_BARS);
                put(level, box, x, 1, 9, Blocks.SMITHING_TABLE);
            }
        }
    }

    private static Block stationWall(@Nullable EmergencyService service) {
        return service == EmergencyService.MEDIC ? Blocks.SMOOTH_QUARTZ : Blocks.STONE_BRICKS;
    }

    static void repairEmergencyStation(ServerLevel level, LevelChunk chunk, StructureStart start) {
        for (StructurePiece piece : start.getPieces()) {
            if (piece instanceof ServiceSitePiece site && site.emergency != null) {
                site.repairEmergencyStation(level, chunk, site.emergency);
            }
        }
    }

    private void repairEmergencyStation(ServerLevel level, LevelChunk chunk, EmergencyService service) {
        BlockPos origin = getWorldPos(0, 0, 0);
        Block stripe = service == EmergencyService.MEDIC ? Blocks.RED_CONCRETE : Blocks.BLUE_CONCRETE;
        for (int x : new int[]{7, 8}) {
            BlockPos door = origin.offset(x, 1, 12);
            if (!level.getBlockState(door).is(Blocks.IRON_DOOR)
                    || !level.getBlockState(door.above()).is(Blocks.IRON_DOOR)
                    || !level.getBlockState(door.above(3)).is(stripe)) {
                return;
            }
        }
        setEmergencyGarage(level, origin, service, false);
        if (!narrowApproach) {
            trimOldApproach(level, origin);
            narrowApproach = true;
            chunk.setUnsaved(true);
        }
        for (int x = 7; x <= 8; x++) {
            for (int z = 12; z <= 14; z++) {
                BlockPos paving = origin.offset(x, 0, z);
                if (level.getBlockState(paving).is(Blocks.LIGHT_GRAY_CONCRETE)) {
                    level.setBlockAndUpdate(paving, Blocks.POLISHED_ANDESITE.defaultBlockState());
                }
            }
        }
        if (service == EmergencyService.MEDIC) {
            BlockState stair = Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
            for (BlockPos offset : MEDIC_CHAIRS) {
                BlockPos chair = origin.offset(offset);
                if (level.getBlockState(chair).isAir()) {
                    level.setBlockAndUpdate(chair, stair);
                }
            }
        }
    }

    private static void trimOldApproach(ServerLevel level, BlockPos origin) {
        for (int x : new int[]{5, 6, 9}) {
            for (int z = APPROACH_START; z <= APPROACH_END; z++) {
                BlockPos paving = origin.offset(x, 0, z);
                if (!level.getBlockState(paving).is(Blocks.POLISHED_ANDESITE)
                        || !level.getBlockState(paving.above()).isAir()) {
                    continue;
                }
                level.removeBlock(paving, false);
                // Old approaches used stone bricks to fill the space down to solid ground.
                BlockPos support = paving.below();
                while (support.getY() >= level.getMinBuildHeight() && level.getBlockState(support).is(Blocks.STONE_BRICKS)) {
                    level.removeBlock(support, false);
                    support = support.below();
                }
            }
        }
    }

    static void setEmergencyGarage(ServerLevel level, BlockPos origin, EmergencyService service, boolean open) {
        Block wall = stationWall(service);
        for (int x : new int[]{7, 8}) {
            BlockPos door = origin.offset(x, 1, 12);
            BlockPos header = door.above(2);
            BlockState state = level.getBlockState(header);
            if (open && state.is(wall)) {
                level.setBlock(header, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            setIronDoor(level, door, open);
            if (!open && state.isAir() && level.getBlockState(door.above()).is(Blocks.IRON_DOOR)) {
                level.setBlock(header, wall.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    private void placeIronDoor(WorldGenLevel level, BoundingBox box, int x, int z, DoorHingeSide hinge) {
        BlockState door = Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH)
                .setValue(DoorBlock.HINGE, hinge);
        placeBlock(level, door, x, 1, z, box);
        placeBlock(level, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), x, 2, z, box);
    }

    static void setIronDoor(ServerLevel level, BlockPos lower, boolean open) {
        for (int half = 0; half <= 1; half++) {
            BlockPos pos = lower.above(half);
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.IRON_DOOR) && state.getValue(DoorBlock.OPEN) != open) {
                level.setBlock(pos, state.setValue(DoorBlock.OPEN, open), Block.UPDATE_CLIENTS);
            }
        }
    }
    private static void secureContainer(WorldGenLevel level, BaseContainerBlockEntity container) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Lock", "intertnet:" + java.util.UUID.randomUUID());
        container.loadWithComponents(tag, level.registryAccess());
        container.getPersistentData().putBoolean(KITCHEN_TAG, true);
        container.setChanged();
    }

    static void writeSign(WorldGenLevel level, SignBlockEntity sign, SignText text) {
        // Worldgen block entities can exist before they have a live Level.
        CompoundTag tag = new CompoundTag();
        tag.put("front_text", SignText.DIRECT_CODEC.encodeStart(
                level.registryAccess().createSerializationContext(NbtOps.INSTANCE), text).getOrThrow());
        tag.putBoolean("is_waxed", true);
        sign.loadWithComponents(tag, level.registryAccess());
    }

    private static Villager staff(WorldGenLevel level, String name) {
        Villager villager = EntityType.VILLAGER.create(level.getLevel());
        if (villager != null) {
            villager.setNoAi(true);
            villager.setInvulnerable(true);
            villager.setPersistenceRequired();
            villager.setCustomName(Component.literal(name));
        }
        return villager;
    }

    private static void spawn(WorldGenLevel level, Entity entity, BlockPos pos, float yaw) {
        if (entity != null) {
            Vec3 center = Vec3.atBottomCenterOf(pos);
            entity.moveTo(center.x, center.y, center.z, yaw, 0);
            level.addFreshEntityWithPassengers(entity);
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
