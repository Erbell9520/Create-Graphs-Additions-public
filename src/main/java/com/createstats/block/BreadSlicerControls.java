package com.createstats.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class BreadSlicerControls {
    private BreadSlicerControls() {
    }

    public static final double CLICK_MARGIN = 0.3;

    public enum Zone {
        START(8.60, 4.47, 0.64, 11.10, 6.97, 1.42),

        STOP(11.20, 4.32, 0.52, 14.00, 7.12, 1.42),

        LEVER(2.10, 4.70, 0.80, 7.30, 6.90, 1.42),

        DRAWER(2.30, 2.30, 0.84, 13.70, 4.05, 1.44),

        DECK(1.70, 7.85, 1.90, 14.30, 13.00, 11.40),

        HOOD(0.85, 8.15, 0.60, 15.15, 14.35, 14.90);

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
    public static Zone zoneAt(Direction facing, BlockPos pos, Vec3 hit, boolean hoodOpen) {
        final Vec3 p = toAuthoredFrame(facing, pos, hit);
        for (final Zone zone : Zone.values()) {
            if (zone == Zone.DECK && !hoodOpen) {
                continue;
            }
            if (zone.contains(p.x, p.y, p.z)) {
                return zone;
            }
        }
        return null;
    }
}
