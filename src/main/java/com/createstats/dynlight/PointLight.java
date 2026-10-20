package com.createstats.dynlight;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public record PointLight(double x, double y, double z, int level) implements Light {
    public PointLight {
        level = Math.max(0, Math.min(EmissionPlan.MAX_LEVEL, level));
    }

    public static PointLight at(Vec3 pos, int level) {
        return new PointLight(pos.x, pos.y, pos.z, level);
    }

    public static PointLight at(BlockPos pos, int level) {
        return new PointLight(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, level);
    }
}
