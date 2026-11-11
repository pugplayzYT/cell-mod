package com.example.intertnet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class GlobalSatelliteNetwork extends SavedData {
    static final String NAME = "Global Satellite";
    static final int RANGE = 1024;
    private static final int SPACING = 8;
    private static final int MIN_ALTITUDE = 196;
    private static final TicketType<ChunkPos> RELAY_TICKET =
            TicketType.create("intertnet_satellite_relay", Comparator.comparingLong(ChunkPos::toLong));

    private final Map<ChunkPos, BlockPos> relays = new HashMap<>();
    private final Set<ChunkPos> destroyed = new HashSet<>();
    private final Set<ChunkPos> pending = new HashSet<>();

    private static GlobalSatelliteNetwork get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new Factory<>(GlobalSatelliteNetwork::new, GlobalSatelliteNetwork::load), "intertnet_satellite_relays");
    }

    private static GlobalSatelliteNetwork load(CompoundTag tag, HolderLookup.Provider registries) {
        var network = new GlobalSatelliteNetwork();
        for (long packed : tag.getLongArray("Relays")) {
            BlockPos pos = BlockPos.of(packed);
            network.relays.put(new ChunkPos(pos), pos);
        }
        for (long packed : tag.getLongArray("Destroyed")) {
            ChunkPos chunk = new ChunkPos(packed);
            network.destroyed.add(chunk);
            network.relays.remove(chunk);
        }
        return network;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLongArray("Relays", relays.values().stream().mapToLong(BlockPos::asLong).toArray());
        tag.putLongArray("Destroyed", destroyed.stream().mapToLong(ChunkPos::toLong).toArray());
        return tag;
    }

    static void tick(ServerTickEvent.Post event) {
        ServerLevel level = event.getServer().overworld();
        if (level.getGameTime() % 20 == 0) get(level).update(level);
    }

    private void update(ServerLevel level) {
        Set<ChunkPos> wanted = new HashSet<>();
        for (var player : level.players()) {
            if (player.isSpectator()) continue;
            ChunkPos center = player.chunkPosition();
            int firstX = nearestGridChunk(center.x) - SPACING;
            int firstZ = nearestGridChunk(center.z) - SPACING;
            ChunkPos nearestUnloaded = null;
            double nearestDistance = Double.MAX_VALUE;

            for (int x = firstX; x <= firstX + 2 * SPACING; x += SPACING) {
                for (int z = firstZ; z <= firstZ + 2 * SPACING; z += SPACING) {
                    ChunkPos candidate = new ChunkPos(x, z);
                    if (destroyed.contains(candidate)) continue;
                    var chunk = level.getChunkSource().getChunkNow(x, z);
                    if (chunk != null) {
                        updateRelay(level, chunk, candidate);
                    } else if (!relays.containsKey(candidate) && level.getWorldBorder().isWithinBounds(candidate)) {
                        double distance = horizontalDistanceSquared(player.getX(), player.getZ(),
                                candidate.getMiddleBlockX(), candidate.getMiddleBlockZ());
                        if (distance < nearestDistance) {
                            nearestUnloaded = candidate;
                            nearestDistance = distance;
                        }
                    }
                }
            }
            // On small render distances, load one candidate long enough to place a physical relay.
            if (nearestUnloaded != null && PhoneItem.hasSatelliteAntenna(DeliveryManager.findPlayerPhoneStack(player))
                    && findRelay(player.getX(), player.getZ()) == null) {
                wanted.add(nearestUnloaded);
            }
        }
        for (ChunkPos chunk : wanted) {
            if (pending.add(chunk)) level.getChunkSource().addRegionTicket(RELAY_TICKET, chunk, 0, chunk);
        }
        pending.removeIf(chunk -> {
            if (wanted.contains(chunk)) return false;
            level.getChunkSource().removeRegionTicket(RELAY_TICKET, chunk, 0, chunk);
            return true;
        });
    }

    private void updateRelay(ServerLevel level, LevelChunk chunk, ChunkPos key) {
        Block relay = IntertnetMod.SATELLITE_RELAY.get();
        BlockPos existing = relays.get(key);
        if (existing != null) {
            if (!chunk.getBlockState(existing).is(relay)) removeRelay(existing);
            return;
        }

        int x = key.getMiddleBlockX();
        int z = key.getMiddleBlockZ();
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        BlockPos top = new BlockPos(x, surface - 1, z);
        if (chunk.getBlockState(top).is(relay)) {
            relays.put(key, top);
            setDirty();
            return;
        }
        int y = Math.min(level.getMaxBuildHeight() - 2, Math.max(MIN_ALTITUDE, surface + 32));
        BlockPos pos = new BlockPos(x, y, z);
        if (y >= surface && y >= level.getMinBuildHeight() && level.getWorldBorder().isWithinBounds(pos)
                && chunk.getBlockState(pos).isAir()
                && level.setBlock(pos, relay.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE)) {
            relays.put(key, pos);
            setDirty();
        }
    }

    private void removeRelay(BlockPos pos) {
        ChunkPos key = new ChunkPos(pos);
        if (relays.remove(key, pos)) {
            destroyed.add(key);
            setDirty();
        }
    }

    @Nullable
    static BlockPos findRelay(ServerLevel level, double x, double z) {
        return level.dimension() == Level.OVERWORLD ? get(level).findRelay(x, z) : null;
    }

    @Nullable
    private BlockPos findRelay(double x, double z) {
        int minX = Math.floorDiv((int) Math.floor(x - RANGE), 16 * SPACING) * SPACING;
        int minZ = Math.floorDiv((int) Math.floor(z - RANGE), 16 * SPACING) * SPACING;
        int maxX = Math.floorDiv((int) Math.floor(x + RANGE), 16 * SPACING) * SPACING;
        int maxZ = Math.floorDiv((int) Math.floor(z + RANGE), 16 * SPACING) * SPACING;
        double closest = (double) RANGE * RANGE;
        BlockPos result = null;
        for (int cx = minX; cx <= maxX; cx += SPACING) {
            for (int cz = minZ; cz <= maxZ; cz += SPACING) {
                BlockPos relay = relays.get(new ChunkPos(cx, cz));
                if (relay == null) continue;
                double distance = horizontalDistanceSquared(x, z, relay.getX() + 0.5, relay.getZ() + 0.5);
                if (distance < closest) {
                    closest = distance;
                    result = relay;
                }
            }
        }
        return result;
    }

    private static int nearestGridChunk(int chunk) {
        return Math.floorDiv(chunk + SPACING / 2, SPACING) * SPACING;
    }

    private static double horizontalDistanceSquared(double x, double z, double targetX, double targetZ) {
        double dx = x - targetX;
        double dz = z - targetZ;
        return dx * dx + dz * dz;
    }

    static final class RelayBlock extends Block {
        private static final VoxelShape SHAPE = Shapes.or(
                Block.box(3, 3, 3, 13, 13, 13),
                Block.box(0, 7, 5, 16, 9, 11),
                Block.box(3, 4, 0, 13, 14, 3),
                Block.box(7, 12, 8, 9, 16, 10));

        RelayBlock(Properties properties) {
            super(properties);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPE;
        }

        @Override
        protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
            if (!newState.is(this) && level instanceof ServerLevel serverLevel && level.dimension() == Level.OVERWORLD) {
                get(serverLevel).removeRelay(pos);
            }
            super.onRemove(state, level, pos, newState, movedByPiston);
        }
    }
}
