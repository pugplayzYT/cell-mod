package com.example.intertnet;

import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.structure.Structure;

enum EmergencyService implements StringRepresentable {
    MEDIC("medic", "Medics"),
    POLICE("police", "Police");

    static final StringRepresentable.EnumCodec<EmergencyService> CODEC = StringRepresentable.fromEnum(EmergencyService::values);
    final String label;
    private final String id;

    EmergencyService(String id, String label) {
        this.id = id;
        this.label = label;
    }

    ResourceKey<Structure> station() {
        return ServiceSiteStructure.key(id + "_station");
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
