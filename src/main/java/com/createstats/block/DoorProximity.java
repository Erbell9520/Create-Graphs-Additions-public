package com.createstats.block;

public final class DoorProximity {
    private DoorProximity() {
    }

    public static double axisCentre(int self, int partner) {
        return (self + partner) / 2.0 + 0.5;
    }

    public static boolean inRange(double px, double py, double pz,
                                  double cx, double cy, double cz, int radius) {
        if (radius <= 0) {
            return false;
        }
        final double dx = px - cx;
        final double dy = py - cy;
        final double dz = pz - cz;
        return dx * dx + dy * dy + dz * dz <= (double) radius * radius;
    }

    public static boolean nearBox(double px, double py, double pz,
                                  double minX, double minY, double minZ,
                                  double maxX, double maxY, double maxZ, int radius) {
        if (radius <= 0) {
            return false;
        }
        final double dx = axisGap(px, minX, maxX);
        final double dy = axisGap(py, minY, maxY);
        final double dz = axisGap(pz, minZ, maxZ);
        return dx * dx + dy * dy + dz * dz <= (double) radius * radius;
    }

    private static double axisGap(double p, double min, double max) {
        if (p < min) {
            return min - p;
        }
        return p > max ? p - max : 0.0;
    }
}
