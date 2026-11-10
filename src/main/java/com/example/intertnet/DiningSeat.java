package com.example.intertnet;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.level.Level;

final class DiningSeat extends Minecart {
    DiningSeat(EntityType<? extends DiningSeat> type, Level level) {
        super(type, level);
        setNoGravity(true);
        setInvulnerable(true);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void tick() {
        baseTick();
    }
}
