package com.example.intertnet;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

final class HotelStructure extends Structure {
    static final ResourceKey<Structure> WILD = ServiceSiteStructure.key("wild_hotel");
    static final ResourceKey<Structure> PLAINS = ServiceSiteStructure.key("plains_hotel");
    static final MapCodec<HotelStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            settingsCodec(instance), Codec.BOOL.fieldOf("wild").forGetter(hotel -> hotel.wild)
    ).apply(instance, HotelStructure::new));
    private final boolean wild;

    private HotelStructure(StructureSettings settings, boolean wild) {
        super(settings);
        this.wild = wild;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (ServiceSiteStructure.isReserved(context, "public_cell_towers")
                || ServiceSiteStructure.isReserved(context, "service_sites")
                || ServiceSiteStructure.isReserved(context, "emergency_stations")
                || ServiceSiteStructure.isReserved(context, "ranger_lookouts")) return Optional.empty();
        int x = context.chunkPos().getMinBlockX();
        int z = context.chunkPos().getMinBlockZ();
        int ground = height(context, x + 8, z + 15);
        int floor = ground - (wild ? HotelPiece.DEPTH : 0);
        if (ground < context.chunkGenerator().getSeaLevel() || floor <= context.heightAccessor().getMinBuildHeight()
                || ground + 7 >= context.heightAccessor().getMaxBuildHeight()) return Optional.empty();
        for (int dx : new int[]{2, 13}) {
            for (int dz : new int[]{2, 12}) {
                if (Math.abs(height(context, x + dx, z + dz) - ground) > 3) return Optional.empty();
            }
        }
        return Optional.of(new GenerationStub(new BlockPos(x + 8, ground + 1, z + 15),
                builder -> builder.addPiece(new HotelPiece(x, floor, z, wild))));
    }

    private static int height(GenerationContext context, int x, int z) {
        return context.chunkGenerator().getFirstFreeHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG,
                context.heightAccessor(), context.randomState()) - 1;
    }

    @Override
    public StructureType<?> type() {
        return IntertnetMod.HOTEL_STRUCTURE.get();
    }
}
