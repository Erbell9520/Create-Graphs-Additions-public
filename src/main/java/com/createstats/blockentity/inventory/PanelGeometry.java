package com.createstats.blockentity.inventory;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public final class PanelGeometry {
    private PanelGeometry() {
    }

    public static final double TILT_DEGREES = -22.5;
    public static final double PIVOT_Y = 16.5;
    public static final double PIVOT_Z = 15.0;
    public static final double FACE_Z = 15.0;

    public static final double GLASS_X0 = 1.0;
    public static final double GLASS_X1 = 15.0;
    public static final double GLASS_Y0 = 17.5;
    public static final double GLASS_Y1 = 29.5;

    public static final double LAMP_X0 = 1.0;
    public static final double LAMP_W = 3.05;
    public static final double LAMP_Y0 = 30.0;
    public static final double LAMP_Y1 = 31.6;

    public static final double TEST_X0 = LAMP_X0 + 4 * LAMP_W;
    public static final double TEST_X1 = 15.0;
    public static final double TEST_Y0 = 30.0;
    public static final double TEST_Y1 = 31.6;

    public static final double PAPER_X0 = 3.7;
    public static final double PAPER_X1 = 12.3;
    public static final double PAPER_Y0 = 9.1;
    public static final double PAPER_Y1 = 13.5;
    public static final double PAPER_Z = 12.4;

    public static final double DIAL_Y = 8.0;
    public static final double DIAL_Z = 8.0;
    public static final double DIAL_LOW_X = 0.8;
    public static final double DIAL_HIGH_X = 15.2;

    public static final double CUP_X = 8.0;
    public static final double CUP_Y = 6.0;
    public static final double CUP_Z = 12.95;

    public static int turnDegrees(Direction facing) {
        return switch (facing) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
    }

    public static Vec3 toWorld(Direction facing, double x, double y, double z) {
        final double dx = x - 8.0;
        final double dz = z - 8.0;
        final double rx;
        final double rz;
        switch (facing) {
            case EAST -> {
                rx = -dz;
                rz = dx;
            }
            case SOUTH -> {
                rx = -dx;
                rz = -dz;
            }
            case WEST -> {
                rx = dz;
                rz = -dx;
            }
            default -> {
                rx = dx;
                rz = dz;
            }
        }
        return new Vec3((rx + 8.0) / 16.0, y / 16.0, (rz + 8.0) / 16.0);
    }

    public static Vec3 toModel(Direction facing, Vec3 local) {
        final double dx = local.x * 16.0 - 8.0;
        final double dz = local.z * 16.0 - 8.0;
        final double mx;
        final double mz;
        switch (facing) {
            case EAST -> {
                mx = dz;
                mz = -dx;
            }
            case SOUTH -> {
                mx = -dx;
                mz = -dz;
            }
            case WEST -> {
                mx = -dz;
                mz = dx;
            }
            default -> {
                mx = dx;
                mz = dz;
            }
        }
        return new Vec3(mx + 8.0, local.y * 16.0, mz + 8.0);
    }

    public static Vec3 dirToWorld(Direction facing, double x, double y, double z) {
        final Vec3 a = toWorld(facing, 8.0 + x, 8.0 + y, 8.0 + z);
        final Vec3 o = toWorld(facing, 8.0, 8.0, 8.0);
        return a.subtract(o).scale(16.0);
    }

    public static Vec3 dirToModel(Direction facing, Vec3 d) {
        final Vec3 a = toModel(facing, new Vec3(0.5 + d.x / 16.0, 0.5 + d.y / 16.0, 0.5 + d.z / 16.0));
        return a.subtract(8.0, 8.0, 8.0);
    }

    public static Vec3 panelToModel(double px, double py, double lift) {
        final double a = Math.toRadians(TILT_DEGREES);
        final double c = Math.cos(a);
        final double s = Math.sin(a);
        final double y = py - PIVOT_Y;
        final double z = FACE_Z + lift - PIVOT_Z;
        return new Vec3(px, y * c - z * s + PIVOT_Y, y * s + z * c + PIVOT_Z);
    }

    public static double[] rayOnPanel(Vec3 origin, Vec3 direction) {
        final double a = -Math.toRadians(TILT_DEGREES);
        final double c = Math.cos(a);
        final double s = Math.sin(a);

        final double oy = origin.y - PIVOT_Y;
        final double oz = origin.z - PIVOT_Z;
        final double ly = oy * c - oz * s + PIVOT_Y;
        final double lz = oy * s + oz * c + PIVOT_Z;
        final double dy = direction.y * c - direction.z * s;
        final double dz = direction.y * s + direction.z * c;
        if (Math.abs(dz) < 1e-9) {
            return null;
        }
        final double t = (FACE_Z - lz) / dz;
        if (t < -1e-6) {
            return null;
        }
        return new double[]{origin.x + direction.x * t, ly + dy * t};
    }

    public static boolean onTest(double[] panel) {
        return panel != null && panel[0] >= TEST_X0 - 0.15 && panel[0] <= TEST_X1 + 0.15
                && panel[1] >= TEST_Y0 - 0.15 && panel[1] <= TEST_Y1 + 0.3;
    }
}
