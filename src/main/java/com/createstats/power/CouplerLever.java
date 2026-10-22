package com.createstats.power;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class CouplerLever {
    private static final AABB NORTH = new AABB(0.0, 0.125, 0.171875, 0.09375, 0.553125, 0.828125);

    private static final double SLACK = 1.0 / 64;

    private CouplerLever() {
    }

    public static AABB local(Direction facing) {
        return turn(NORTH, facing);
    }

    public static boolean hit(@Nullable Direction facing, @Nullable Vec3 localHit) {
        if (facing == null || localHit == null || !facing.getAxis().isHorizontal()) {
            return false;
        }
        return local(facing).inflate(SLACK).contains(localHit);
    }

    static AABB turn(AABB box, Direction facing) {
        return CouplerSockets.turn(box, facing);
    }
}
