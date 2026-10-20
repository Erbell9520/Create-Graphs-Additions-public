package com.createstats.surveyor.core;

// windowed projection, a figure eight has two stretches near the crossing
public final class PathProjection {
    public record Result(double s, double distance, double lateral, double dy, boolean found) {
        public static final Result NONE = new Result(0.0, Double.POSITIVE_INFINITY, 0.0, 0.0, false);
    }

    private PathProjection() {
    }

    public static Result window(PathSpline sp, double x, double y, double z, double near, double back, double ahead) {
        int n = sp.sampleCount();
        if (n == 0) return Result.NONE;
        if (n == 1) return result(sp, 0, 0.0, x, y, z);
        double step = sp.step();
        int from = (int) Math.floor((near - back) / step);
        int to = (int) Math.ceil((near + ahead) / step);
        if (!sp.closed()) {
            from = Math.max(0, from);
            to = Math.min(n - 1, to);
        } else if (to - from >= n) {
            from = 0;
            to = n - 1;
        }
        int best = -1;
        double bestD = Double.POSITIVE_INFINITY;
        for (int k = from; k <= to; k++) {
            int i = sp.closed() ? Math.floorMod(k, n) : k;
            double d = sq(sp.sampleX(i) - x) + sq(sp.sampleZ(i) - z);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best < 0 ? Result.NONE : refine(sp, best, x, y, z);
    }

    public static Result global(PathSpline sp, double x, double y, double z, double hx, double hz, double maxDistance,
                                double maxDy, double maxAngleDeg) {
        int n = sp.sampleCount();
        if (n == 0) return Result.NONE;
        double hl = Math.sqrt(hx * hx + hz * hz);
        boolean anyWay = hl < 1.0e-9;
        double minDot = Math.cos(Math.toRadians(maxAngleDeg));
        int best = -1;
        double bestD = maxDistance * maxDistance;
        for (int i = 0; i < n; i++) {
            if (Math.abs(sp.sampleY(i) - y) > maxDy) continue;
            if (!anyWay && (sp.tangentX(i) * hx + sp.tangentZ(i) * hz) / hl < minDot) continue;
            double d = sq(sp.sampleX(i) - x) + sq(sp.sampleZ(i) - z);
            if (d <= bestD) {
                bestD = d;
                best = i;
            }
        }
        return best < 0 ? Result.NONE : refine(sp, best, x, y, z);
    }

    private static Result refine(PathSpline sp, int i, double x, double y, double z) {
        int n = sp.sampleCount();
        double bestS = sp.sampleS(i);
        double bestD = sq(sp.sampleX(i) - x) + sq(sp.sampleZ(i) - z);
        for (int side = -1; side <= 1; side += 2) {
            int j = i + side;
            if (sp.closed()) j = Math.floorMod(j, n);
            else if (j < 0 || j >= n) continue;
            int a = side < 0 ? j : i;
            int b = side < 0 ? i : j;
            double ax = sp.sampleX(a);
            double az = sp.sampleZ(a);
            double ex = sp.sampleX(b) - ax;
            double ez = sp.sampleZ(b) - az;
            double len2 = ex * ex + ez * ez;
            if (len2 < 1.0e-12) continue;
            double t = Math.max(0.0, Math.min(1.0, ((x - ax) * ex + (z - az) * ez) / len2));
            double d = sq(ax + ex * t - x) + sq(az + ez * t - z);
            if (d < bestD) {
                bestD = d;
                bestS = sp.sampleS(a) + t * sp.step();
            }
        }
        return result(sp, 0, sp.wrap(bestS), x, y, z);
    }

    private static Result result(PathSpline sp, int unused, double s, double x, double y, double z) {
        double px = sp.x(s);
        double pz = sp.z(s);
        double[] t = sp.tangent(s);
        double ox = x - px;
        double oz = z - pz;

        double lateral = ox * -t[1] + oz * t[0];
        return new Result(s, Math.sqrt(ox * ox + oz * oz), lateral, y - sp.y(s), true);
    }

    private static double sq(double v) {
        return v * v;
    }
}
