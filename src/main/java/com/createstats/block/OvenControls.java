package com.createstats.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class OvenControls {
    private OvenControls() {
    }

    public static final double DEPTH_MARGIN = 0.3;

    public enum Zone {
        DIAL(11.18, 12.03, 0.55, 14.22, 15.07, 2.40),

        START(9.02, 12.87, 0.90, 10.38, 14.23, 2.40),

        LAMP(7.42, 12.87, 0.90, 8.78, 14.23, 2.40),

        SHELF(2.00, 2.60, 1.00, 14.00, 11.00, 13.60),

        DOOR(1.00, 1.55, 0.00, 15.00, 11.95, 2.60);

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
            return x >= x0 && x <= x1
                    && y >= y0 && y <= y1
                    && z >= z0 - DEPTH_MARGIN && z <= z1 + DEPTH_MARGIN;
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
    public static Zone zoneAt(Direction facing, BlockPos pos, Vec3 hit, boolean doorOpen) {
        final Vec3 p = toAuthoredFrame(facing, pos, hit);
        for (final Zone zone : Zone.values()) {
            if (zone == Zone.SHELF && !doorOpen) {
                continue;
            }
            if (zone.contains(p.x, p.y, p.z)) {
                return zone;
            }
        }
        return null;
    }
}
