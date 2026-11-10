package com.example.intertnet;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

final class PublicTowerStructure extends Structure {
    static final MapCodec<PublicTowerStructure> CODEC = simpleCodec(PublicTowerStructure::new);
    private static final int FOREST_EDGE_RADIUS = 64;

    private PublicTowerStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        int x = context.chunkPos().getMinBlockX();
        int z = context.chunkPos().getMinBlockZ();
        int floorY = surfaceHeight(context, x + 7, z + 14) - 1;
        if (floorY < context.chunkGenerator().getSeaLevel()
                || floorY + PublicTowerPiece.HEIGHT >= context.heightAccessor().getMaxBuildHeight()) {
            return Optional.empty();
        }

        BlockPos center = new BlockPos(x + 7, floorY + 1, z + 7);
        Holder<Biome> biome = biomeAt(context, center.getX(), center.getY(), center.getZ());
        if (!context.validBiome().test(biome)
                || (biome.is(BiomeTags.IS_FOREST)
                && (context.random().nextInt(3) != 0 || !isNearPlains(context, center)))) {
            return Optional.empty();
        }

        // Use the approach as the floor level and skip sites that would need major grading.
        for (int dx = 2; dx <= 12; dx += 5) {
            for (int dz = 2; dz <= 12; dz += 5) {
                if (Math.abs(surfaceHeight(context, x + dx, z + dz) - 1 - floorY) > 3) {
                    return Optional.empty();
                }
            }
        }

        return Optional.of(new GenerationStub(center, builder -> builder.addPiece(new PublicTowerPiece(x, floorY, z))));
    }

    private static boolean isNearPlains(GenerationContext context, BlockPos center) {
        for (int dx = -FOREST_EDGE_RADIUS; dx <= FOREST_EDGE_RADIUS; dx += 32) {
            for (int dz = -FOREST_EDGE_RADIUS; dz <= FOREST_EDGE_RADIUS; dz += 32) {
                if ((dx == 0 && dz == 0) || dx * dx + dz * dz > FOREST_EDGE_RADIUS * FOREST_EDGE_RADIUS) {
                    continue;
                }
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                Holder<Biome> neighbor = biomeAt(context, x, surfaceHeight(context, x, z), z);
                if (neighbor.is(Biomes.PLAINS) || neighbor.is(Biomes.SUNFLOWER_PLAINS)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Holder<Biome> biomeAt(GenerationContext context, int x, int y, int z) {
        return context.biomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z),
                context.randomState().sampler());
    }

    private static int surfaceHeight(GenerationContext context, int x, int z) {
        return context.chunkGenerator().getFirstFreeHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(), context.randomState());
    }

    @Override
    public StructureType<?> type() {
        return IntertnetMod.PUBLIC_TOWER_STRUCTURE.get();
    }
}
