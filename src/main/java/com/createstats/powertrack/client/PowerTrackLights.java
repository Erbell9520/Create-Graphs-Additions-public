package com.createstats.powertrack.client;

import com.createstats.client.dynlight.DynamicLights;
import com.createstats.dynlight.PointLight;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class PowerTrackLights {
    public static final int LAMP = 7;

    public static final float LIT = 0.5f;

    private PowerTrackLights() {
    }

    public static void lamp(BlockEntity owner, BlockPos at) {
        if (owner.getLevel() instanceof ClientLevel) {
            DynamicLights.submit(owner, PointLight.at(at, LAMP));
        }
    }
}
