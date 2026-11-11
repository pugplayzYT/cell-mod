package com.example.intertnet;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

import java.util.Optional;

final class ServiceSiteStructure extends Structure {
    static final ResourceKey<Structure> RESTAURANT = key("restaurant");
    static final ResourceKey<Structure> RIDESHARE = key("rideshare_center");
    static final MapCodec<ServiceSiteStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            settingsCodec(instance), Codec.BOOL.fieldOf("restaurant").forGetter(site -> site.restaurant),
            EmergencyService.CODEC.optionalFieldOf("emergency").forGetter(site -> site.emergency)
    ).apply(instance, ServiceSiteStructure::new));

    private final boolean restaurant;
    private final Optional<EmergencyService> emergency;

    private ServiceSiteStructure(StructureSettings settings, boolean restaurant, Optional<EmergencyService> emergency) {
        super(settings);
        this.restaurant = restaurant;
        this.emergency = emergency;
    }

    static ResourceKey<Structure> key(String path) {
        return ResourceKey.create(Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath(IntertnetMod.MODID, path));
    }

    Optional<EmergencyService> emergency() {
        return emergency;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (isReserved(context, "public_cell_towers") || (emergency.isPresent() && isReserved(context, "service_sites"))) {
            return Optional.empty();
        }
        int x = context.chunkPos().getMinBlockX();
        int z = context.chunkPos().getMinBlockZ();
        int y = ground(context, x + 7, z + 14);
        if (y < context.chunkGenerator().getSeaLevel()
                || y + ServiceSitePiece.HEIGHT >= context.heightAccessor().getMaxBuildHeight()) {
            return Optional.empty();
        }
        for (int dx : restaurant ? new int[]{1, 7, 13} : new int[]{2, 7, 12}) {
            for (int dz : new int[]{2, 7, 12}) {
                if (Math.abs(ground(context, x + dx, z + dz) - y) > 4) {
                    return Optional.empty();
                }
            }
        }
        boolean rooftop = restaurant && context.random().nextInt(3) == 0;
        return Optional.of(new GenerationStub(new BlockPos(x + 7, y + 1, z + 7),
                builder -> builder.addPiece(new ServiceSitePiece(x, y, z, restaurant, rooftop, emergency.orElse(null)))));
    }

    static boolean isReserved(GenerationContext context, String setName) {
        var set = context.registryAccess().registryOrThrow(Registries.STRUCTURE_SET)
                .get(ResourceLocation.fromNamespaceAndPath(IntertnetMod.MODID, setName));
        var chunk = context.chunkPos();
        return set != null && set.placement() instanceof RandomSpreadStructurePlacement grid
                && grid.getPotentialStructureChunk(context.seed(), chunk.x, chunk.z).equals(chunk)
                && grid.applyAdditionalChunkRestrictions(chunk.x, chunk.z, context.seed());
    }

    private static int ground(GenerationContext context, int x, int z) {
        return context.chunkGenerator().getFirstFreeHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(), context.randomState()) - 1;
    }

    @Override
    public StructureType<?> type() {
        return IntertnetMod.SERVICE_SITE_STRUCTURE.get();
    }
}
