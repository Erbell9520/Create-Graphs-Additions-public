package com.createstats.battle.core;

// cbc shells don't inherit ship velocity (sable only does fireballs) so check the path relative to the hull
public final class LineOfFire {
    public interface Occupancy {
        boolean solid(int x, int y, int z);
    }

    public interface ToLocal {
        void apply(double[] world, double[] outLocal);
    }

    public static final int MAX_TICKS = 64;

    public static final int MAX_CELLS = 512;

    public static final int[] UNSURE = new int[0];

    public static final double MUZZLE_CLEAR = 1.5;

    public static final double GROW = 2.0;

    private static final int GO = 0;
    private static final int HIT = 1;
    private static final int STOP = -1;

    private LineOfFire() {
    }

    public static int[] ownShip(Trajectory shell, double[] muzzleWorld, double[] v0vec, double[] shipVel,
                                ToLocal toLocal, Occupancy occ, int[] boundsMin, int[] boundsMax,
                                int[] skipMin, int[] skipMax) {
        double g = shell.gravity();
        double k = shell.drag();
        boolean quadratic = shell.quadratic();
        double svx = shipVel == null ? 0.0 : shipVel[0];
        double svy = shipVel == null ? 0.0 : shipVel[1];
        double svz = shipVel == null ? 0.0 : shipVel[2];
        double[] world = {muzzleWorld[0], muzzleWorld[1], muzzleWorld[2]};
        double[] a = new double[3];
        double[] b = new double[3];
        toLocal.apply(world, a);
        Walk walk = new Walk(occ, boundsMin, boundsMax, skipMin, skipMax, a[0], a[1], a[2]);

        double[] x = new double[3];
        double[] v = {v0vec[0], v0vec[1], v0vec[2]};
        for (int n = 1; n <= MAX_TICKS; n++) {
            Trajectory.step(x, v, g, k, quadratic);
            world[0] = muzzleWorld[0] + x[0] - svx * n;
            world[1] = muzzleWorld[1] + x[1] - svy * n;
            world[2] = muzzleWorld[2] + x[2] - svz * n;
            toLocal.apply(world, b);
            int result = walk.segment(a, b);
            if (result == HIT) return new int[]{walk.hitX, walk.hitY, walk.hitZ};
            if (result == STOP) return walk.cells > MAX_CELLS ? UNSURE : null;
            double[] swap = a;
            a = b;
            b = swap;
        }
        return null;
    }

    public static int firstBox(Trajectory shell, double[] muzzleWorld, double[] v0vec, double maxPathLen,
                               double[][] boxes) {
        if (boxes == null || boxes.length == 0) return -1;
        double g = shell.gravity();
        double k = shell.drag();
        boolean quadratic = shell.quadratic();
        double[] x = new double[3];
        double[] v = {v0vec[0], v0vec[1], v0vec[2]};
        double[] p = {muzzleWorld[0], muzzleWorld[1], muzzleWorld[2]};
        double[] d = new double[3];
        double walked = 0.0;
        for (int n = 0; n < Trajectory.MAX_STEPS; n++) {
            Trajectory.step(x, v, g, k, quadratic);
            d[0] = muzzleWorld[0] + x[0] - p[0];
            d[1] = muzzleWorld[1] + x[1] - p[1];
            d[2] = muzzleWorld[2] + x[2] - p[2];
            double len = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
            double end = 1.0;
            boolean last = walked + len >= maxPathLen;
            if (last) end = len > 0.0 ? Math.max(0.0, (maxPathLen - walked) / len) : 0.0;
            int best = -1;
            double bestT = Double.POSITIVE_INFINITY;
            for (int i = 0; i < boxes.length; i++) {
                double t = entry(p, d, end, boxes[i]);
                if (t < bestT) {
                    bestT = t;
                    best = i;
                }
            }
            if (best >= 0) return best;
            if (last) return -1;
            walked += len;
            p[0] = muzzleWorld[0] + x[0];
            p[1] = muzzleWorld[1] + x[1];
            p[2] = muzzleWorld[2] + x[2];
        }
        return -1;
    }

    private static double entry(double[] p, double[] d, double end, double[] box) {
        double t0 = 0.0;
        double t1 = end;
        for (int axis = 0; axis < 3; axis++) {
            double lo = box[axis];
            double hi = box[axis + 3];
            if (d[axis] == 0.0) {
                if (p[axis] < lo || p[axis] > hi) return Double.POSITIVE_INFINITY;
                continue;
            }
            double ta = (lo - p[axis]) / d[axis];
            double tb = (hi - p[axis]) / d[axis];
            if (ta > tb) {
                double swap = ta;
                ta = tb;
                tb = swap;
            }
            if (ta > t0) t0 = ta;
            if (tb < t1) t1 = tb;
            if (t0 > t1) return Double.POSITIVE_INFINITY;
        }
        return t0;
    }

    private static final class Walk {
        private final Occupancy occ;
        private final double minX;
        private final double minY;
        private final double minZ;
        private final double maxX;
        private final double maxY;
        private final double maxZ;
        private final boolean skip;
        private final int skipMinX;
        private final int skipMinY;
        private final int skipMinZ;
        private final int skipMaxX;
        private final int skipMaxY;
        private final int skipMaxZ;
        private final double muzzleX;
        private final double muzzleY;
        private final double muzzleZ;
        private final double[] from = new double[3];
        private final double[] to = new double[3];
        private int lastX = Integer.MIN_VALUE;
        private int lastY = Integer.MIN_VALUE;
        private int lastZ = Integer.MIN_VALUE;
        private int cells;
        int hitX;
        int hitY;
        int hitZ;

        Walk(Occupancy occ, int[] boundsMin, int[] boundsMax, int[] skipMin, int[] skipMax, double muzzleX,
             double muzzleY, double muzzleZ) {
            this.occ = occ;
            this.minX = boundsMin[0] - GROW;
            this.minY = boundsMin[1] - GROW;
            this.minZ = boundsMin[2] - GROW;
            this.maxX = boundsMax[0] + 1 + GROW;
            this.maxY = boundsMax[1] + 1 + GROW;
            this.maxZ = boundsMax[2] + 1 + GROW;
            this.skip = skipMin != null && skipMax != null;
            this.skipMinX = skip ? skipMin[0] : 0;
            this.skipMinY = skip ? skipMin[1] : 0;
            this.skipMinZ = skip ? skipMin[2] : 0;
            this.skipMaxX = skip ? skipMax[0] : 0;
            this.skipMaxY = skip ? skipMax[1] : 0;
            this.skipMaxZ = skip ? skipMax[2] : 0;
            this.muzzleX = muzzleX;
            this.muzzleY = muzzleY;
            this.muzzleZ = muzzleZ;
        }

        int segment(double[] a, double[] b) {
            for (int i = 0; i < 3; i++) {
                if (!Double.isFinite(a[i]) || !Double.isFinite(b[i])) return STOP;
            }

            double t0 = 0.0;
            double t1 = 1.0;
            for (int i = 0; i < 3 && t0 <= t1; i++) {
                double p = a[i];
                double d = b[i] - p;
                double lo = i == 0 ? minX : (i == 1 ? minY : minZ);
                double hi = i == 0 ? maxX : (i == 1 ? maxY : maxZ);
                if (d == 0.0) {
                    if (p < lo || p > hi) t0 = 2.0;
                    continue;
                }
                double ta = (lo - p) / d;
                double tb = (hi - p) / d;
                if (ta > tb) {
                    double swap = ta;
                    ta = tb;
                    tb = swap;
                }
                if (ta > t0) t0 = ta;
                if (tb < t1) t1 = tb;
            }
            if (t0 > t1) return GO;
            for (int i = 0; i < 3; i++) {
                double d = b[i] - a[i];
                from[i] = a[i] + t0 * d;
                to[i] = t1 >= 1.0 ? b[i] : a[i] + t1 * d;
            }
            int result = walk(from, to);
            return result == GO && t1 < 1.0 ? STOP : result;
        }

        private int walk(double[] a, double[] b) {
            double ax = a[0];
            double ay = a[1];
            double az = a[2];
            double dx = b[0] - ax;
            double dy = b[1] - ay;
            double dz = b[2] - az;
            int cx = (int) Math.floor(ax);
            int cy = (int) Math.floor(ay);
            int cz = (int) Math.floor(az);
            int sx = dx > 0.0 ? 1 : (dx < 0.0 ? -1 : 0);
            int sy = dy > 0.0 ? 1 : (dy < 0.0 ? -1 : 0);
            int sz = dz > 0.0 ? 1 : (dz < 0.0 ? -1 : 0);

            double tDeltaX = sx != 0 ? 1.0 / Math.abs(dx) : Double.POSITIVE_INFINITY;
            double tDeltaY = sy != 0 ? 1.0 / Math.abs(dy) : Double.POSITIVE_INFINITY;
            double tDeltaZ = sz != 0 ? 1.0 / Math.abs(dz) : Double.POSITIVE_INFINITY;
            double tMaxX = sx > 0 ? (cx + 1 - ax) / dx : (sx < 0 ? (cx - ax) / dx : Double.POSITIVE_INFINITY);
            double tMaxY = sy > 0 ? (cy + 1 - ay) / dy : (sy < 0 ? (cy - ay) / dy : Double.POSITIVE_INFINITY);
            double tMaxZ = sz > 0 ? (cz + 1 - az) / dz : (sz < 0 ? (cz - az) / dz : Double.POSITIVE_INFINITY);
            int result = visit(cx, cy, cz);
            while (result == GO) {
                if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                    if (tMaxX > 1.0) break;
                    cx += sx;
                    tMaxX += tDeltaX;
                } else if (tMaxY <= tMaxZ) {
                    if (tMaxY > 1.0) break;
                    cy += sy;
                    tMaxY += tDeltaY;
                } else {
                    if (tMaxZ > 1.0) break;
                    cz += sz;
                    tMaxZ += tDeltaZ;
                }
                result = visit(cx, cy, cz);
            }
            return result;
        }

        private int visit(int cx, int cy, int cz) {
            if (cx == lastX && cy == lastY && cz == lastZ) return GO;
            lastX = cx;
            lastY = cy;
            lastZ = cz;

            if (cx + 1 <= minX || cx >= maxX || cy + 1 <= minY || cy >= maxY || cz + 1 <= minZ || cz >= maxZ) {
                return GO;
            }
            if (++cells > MAX_CELLS) return STOP;
            double ox = cx + 0.5 - muzzleX;
            double oy = cy + 0.5 - muzzleY;
            double oz = cz + 0.5 - muzzleZ;
            if (ox * ox + oy * oy + oz * oz <= MUZZLE_CLEAR * MUZZLE_CLEAR) return GO;
            if (skip && cx >= skipMinX && cx <= skipMaxX && cy >= skipMinY && cy <= skipMaxY
                    && cz >= skipMinZ && cz <= skipMaxZ) {
                return GO;
            }
            if (occ.solid(cx, cy, cz)) {
                hitX = cx;
                hitY = cy;
                hitZ = cz;
                return HIT;
            }
            return GO;
        }
    }
}
