package com.createstats.pducabinet;

public final class CabinetGeometry {
    public static final double[] DOOR_PIVOT = {2.85 / 16, 11.325 / 16, 3.15 / 16};
    public static final double OPEN_ANGLE = 105;

    private CabinetGeometry() {}

    public static double[] toModel(int turn, double x, double y, double z) {
        return switch (Math.floorMod(turn, 4)) {
            case 1 -> new double[]{z, y, 1 - x};
            case 2 -> new double[]{1 - x, y, 1 - z};
            case 3 -> new double[]{1 - z, y, x};
            default -> new double[]{x, y, z};
        };
    }

    public static double[] toWorld(int turn, double x, double y, double z) {
        return toModel(-turn, x, y, z);
    }

    public static double[] stud(int index, int turn) {
        if (index < 0 || index >= 8) throw new IllegalArgumentException("Invalid output " + index);
        return toWorld(turn, 5.8 / 16, (28.25 - index * 3.2) / 16, 15.55 / 16);
    }

    public static int pick(int turn, double x, double y, double z) {
        double[] p = toModel(turn, x, y, z);
        if (Math.abs(p[0] - 5.8 / 16) > 1.7 / 16) return -1;
        for (int i = 0; i < 8; i++) {
            if (Math.abs(p[1] - (28.25 - i * 3.2) / 16) <= 1.45 / 16) return i;
        }
        return -1;
    }

    public static double[] doorPoint(double[] p, double degrees) {
        double a = Math.toRadians(degrees), c = Math.cos(a), s = Math.sin(a);
        double x = p[0] - DOOR_PIVOT[0], z = p[2] - DOOR_PIVOT[2];
        return new double[]{DOOR_PIVOT[0] + x * c + z * s, p[1],
                DOOR_PIVOT[2] - x * s + z * c};
    }

    public static double doorAngle(double from, boolean open, double elapsed, double duration) {
        double t = Math.max(0, Math.min(1, elapsed / Math.max(1, duration)));
        double eased = t * t * (3 - 2 * t);
        return from + ((open ? OPEN_ANGLE : 0) - from) * eased;
    }
}
