package com.example.intertnet;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

final class RangerLookoutStructure extends Structure {
    static final ResourceKey<Structure> KEY = ServiceSiteStructure.key("ranger_lookout");
    static final TagKey<Biome> FORESTS = TagKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(IntertnetMod.MODID, "has_structure/ranger_lookout"));
    static final MapCodec<RangerLookoutStructure> CODEC = simpleCodec(RangerLookoutStructure::new);

    private RangerLookoutStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (ServiceSiteStructure.isReserved(context, "public_cell_towers")
                || ServiceSiteStructure.isReserved(context, "service_sites")
                || ServiceSiteStructure.isReserved(context, "emergency_stations")) {
            return Optional.empty();
        }
        int x = context.chunkPos().getMinBlockX();
        int z = context.chunkPos().getMinBlockZ();
        int y = ground(context, x + 8, z + 9);
        if (y < context.chunkGenerator().getSeaLevel()
                || y + RangerLookoutPiece.HEIGHT >= context.heightAccessor().getMaxBuildHeight()) {
            return Optional.empty();
        }
        for (int dx : new int[]{4, 12}) {
            for (int dz : new int[]{4, 12}) {
                if (Math.abs(ground(context, x + dx, z + dz) - y) > 3) return Optional.empty();
            }
        }
        if (Math.abs(ground(context, x + 8, z + 15) - y) > 1) return Optional.empty();
        BlockPos origin = new BlockPos(x, y, z);
        return Optional.of(new GenerationStub(origin.offset(RangerLookoutPiece.FOOT),
                builder -> builder.addPiece(new RangerLookoutPiece(origin))));
    }

    private static int ground(GenerationContext context, int x, int z) {
        return context.chunkGenerator().getFirstFreeHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG,
                context.heightAccessor(), context.randomState()) - 1;
    }

    @Override
    public StructureType<?> type() {
        return IntertnetMod.RANGER_LOOKOUT_STRUCTURE.get();
    }
}
