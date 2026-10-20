package com.createstats.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class ToasterControls {
    private ToasterControls() {
    }

    public static final double CLICK_MARGIN = 0.3;

    public enum Zone {
        BAGEL(7.40, 3.35, 1.60, 9.40, 5.35, 2.45),

        CANCEL(4.90, 3.35, 1.60, 6.90, 5.35, 2.45),

        DIAL(8.95, 5.55, 1.20, 13.65, 10.25, 2.55),

        LEVER(2.55, 4.15, 1.10, 4.70, 10.35, 2.55),

        TRAY(3.20, 1.35, 1.70, 12.80, 3.25, 2.70),

        SLOT_A(8.55, 10.50, 4.00, 11.85, 14.60, 11.60),

        SLOT_B(4.15, 10.50, 4.00, 7.45, 14.60, 11.60);

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

        public int slot() {
            return this == SLOT_A ? 0 : this == SLOT_B ? 1 : -1;
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
