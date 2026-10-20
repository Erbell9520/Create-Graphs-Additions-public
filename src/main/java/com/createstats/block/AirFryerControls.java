package com.createstats.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class AirFryerControls {
    private AirFryerControls() {
    }

    // client raytrace lands a hair short of the face, server says hit. slop fixes it
    public static final double CLICK_MARGIN = 0.3;

    public enum Zone {
        POWER(11.0, 14.4, 2.2, 14.0, 15.5, 4.6),

        START(3.85, 10.6, 2.2, 6.55, 13.35, 3.4),

        DRAWER(3.0, 2.2, 0.8, 13.0, 8.3, 9.6);

        final double x0, y0, z0, x1, y1, z1;

        Zone(double x0, double y0, double z0, double x1, double y1, double z1) {
            this.x0 = x0;
            this.y0 = y0;
            this.z0 = z0;
            this.x1 = x1;
            this.y1 = y1;
            this.z1 = z1;
        }

        boolean contains(double x, double y, double z) {
            return x >= x0 - CLICK_MARGIN && x <= x1 + CLICK_MARGIN
                    && y >= y0 - CLICK_MARGIN && y <= y1 + CLICK_MARGIN
                    && z >= z0 - CLICK_MARGIN && z <= z1 + CLICK_MARGIN;
        }
    }

    public static Vec3 toAuthoredFrame(Direction facing, BlockPos pos, Vec3 hit) {
        final double x = hit.x - pos.getX();
        final double y = hit.y - pos.getY();
        final double z = hit.z - pos.getZ();
        final Vec3 local = switch (facing) {
            case EAST -> new Vec3(z, y, 1.0 - x);
            case SOUTH -> new Vec3(1.0 - x, y, 1.0 - z);
            case WEST -> new Vec3(1.0 - z, y, x);
            default -> new Vec3(x, y, z);
        };
        return local.scale(16.0);
    }

    @Nullable
    public static Zone zoneAt(Direction facing, BlockPos pos, Vec3 hit) {
        final Vec3 p = toAuthoredFrame(facing, pos, hit);
        for (final Zone zone : Zone.values()) {
            if (zone.contains(p.x, p.y, p.z)) {
                return zone;
            }
        }
        return null;
    }
}
