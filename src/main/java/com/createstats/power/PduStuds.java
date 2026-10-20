package com.createstats.power;

import net.minecraft.core.Direction;

public final class PduStuds {
    private PduStuds() {
    }

    public static final int COUNT = 8;

    private static final double[] COLUMN_X = {3.5 / 16.0, 6.5 / 16.0, 9.5 / 16.0, 12.5 / 16.0};

    private static final double[] ROW_Z = {7.0 / 16.0, 10.0 / 16.0};

    private static final double PICK_RADIUS = 1.6 / 16.0;

    public static double localX(int stud) {
        return COLUMN_X[Math.floorMod(stud, 4)];
    }

    public static double localZ(int stud) {
        return ROW_Z[Math.floorMod(stud / 4, 2)];
    }

    public static double[] toModelSpace(Direction facing, double x, double z) {
        final double cx = x - 0.5;
        final double cz = z - 0.5;
        final double mx;
        final double mz;
        switch (facing == null ? Direction.NORTH : facing) {
            case EAST -> {
                mx = cz;
                mz = -cx;
            }
            case SOUTH -> {
                mx = -cx;
                mz = -cz;
            }
            case WEST -> {
                mx = -cz;
                mz = cx;
            }
            default -> {
                mx = cx;
                mz = cz;
            }
        }
        return new double[]{mx + 0.5, mz + 0.5};
    }

    public static double[] toBlockSpace(Direction facing, double mx, double mz) {
        final double cx = mx - 0.5;
        final double cz = mz - 0.5;
        final double x;
        final double z;
        switch (facing == null ? Direction.NORTH : facing) {
            case EAST -> {
                x = -cz;
                z = cx;
            }
            case SOUTH -> {
                x = -cx;
                z = -cz;
            }
            case WEST -> {
                x = cz;
                z = -cx;
            }
            default -> {
                x = cx;
                z = cz;
            }
        }
        return new double[]{x + 0.5, z + 0.5};
    }

    public static int pick(Direction facing, double x, double z) {
        final double[] local = toModelSpace(facing, x, z);
        int best = -1;
        double bestDistance = PICK_RADIUS * PICK_RADIUS;
        for (int stud = 0; stud < COUNT; stud++) {
            final double dx = local[0] - localX(stud);
            final double dz = local[1] - localZ(stud);
            final double distance = dx * dx + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = stud;
            }
        }
        return best;
    }

    public static double[] boxOf(Direction facing, int stud) {
        final double half = 0.75 / 16.0;
        final double[] a = toBlockSpace(facing, localX(stud) - half, localZ(stud) - half);
        final double[] b = toBlockSpace(facing, localX(stud) + half, localZ(stud) + half);
        return new double[]{Math.min(a[0], b[0]), Math.min(a[1], b[1]),
                Math.max(a[0], b[0]), Math.max(a[1], b[1])};
    }

    public static double topY() {
        return 19.0 / 16.0;
    }
}
