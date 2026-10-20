package com.createstats.vacuum;

// in front, not a sphere. pulling round the housing is just a magnet
public final class SuctionReach {
    private SuctionReach() {
    }

    public static final double CLEANER_SPREAD = 1.0;

    public static final int SNIFFER_REACH = 6;

    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        public boolean contains(double x, double y, double z) {
            return x >= minX && x < maxX && y >= minY && y < maxY && z >= minZ && z < maxZ;
        }

        public double volume() {
            return (maxX - minX) * (maxY - minY) * (maxZ - minZ);
        }
    }

    public static Box inFront(int bx, int by, int bz, int dx, int dy, int dz, int forward, int side) {
        final double[] lo = {bx - side, by - side, bz - side};
        final double[] hi = {bx + 1 + side, by + 1 + side, bz + 1 + side};
        final int[] d = {dx, dy, dz};
        final int[] b = {bx, by, bz};
        for (int axis = 0; axis < 3; axis++) {
            if (d[axis] > 0) {
                lo[axis] = b[axis];
                hi[axis] = b[axis] + 1 + forward;
            } else if (d[axis] < 0) {
                lo[axis] = b[axis] - forward;
                hi[axis] = b[axis] + 1;
            }
        }
        return new Box(lo[0], lo[1], lo[2], hi[0], hi[1], hi[2]);
    }

    public static Box collector(int bx, int by, int bz, int fx, int fz, int radius) {
        final int r = Math.max(VacuumDraw.COLLECTOR_MIN_RADIUS, Math.min(VacuumDraw.COLLECTOR_MAX_RADIUS, radius));
        final Box flat = inFront(bx, by, bz, fx, 0, fz, r, r);
        return new Box(flat.minX(), by - 1, flat.minZ(), flat.maxX(), by + 1 + r, flat.maxZ());
    }

    public static Box sniffer(int bx, int by, int bz) {
        final int r = SNIFFER_REACH;
        return new Box(bx - r, by - r, bz - r, bx + 1 + r, by + 1 + r, bz + 1 + r);
    }

    public static double exitFraction(double fx, double fy, double fz, double tx, double ty, double tz,
                                      int bx, int by, int bz) {
        final double[] f = {fx, fy, fz};
        final double[] d = {tx - fx, ty - fy, tz - fz};
        final int[] b = {bx, by, bz};
        for (int axis = 0; axis < 3; axis++) {
            if (f[axis] < b[axis] || f[axis] > b[axis] + 1) {
                return 0.0;
            }
        }
        double exit = Double.POSITIVE_INFINITY;
        for (int axis = 0; axis < 3; axis++) {
            if (d[axis] > 0) {
                exit = Math.min(exit, (b[axis] + 1 - f[axis]) / d[axis]);
            } else if (d[axis] < 0) {
                exit = Math.min(exit, (b[axis] - f[axis]) / d[axis]);
            }
        }
        return exit;
    }

    public static Box cleaner(double px, double py, double pz) {
        final double s = CLEANER_SPREAD;
        return new Box(px - s, py - s, pz - s, px + s, py + s, pz + s);
    }
}
