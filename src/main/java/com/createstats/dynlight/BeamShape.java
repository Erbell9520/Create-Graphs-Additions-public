package com.createstats.dynlight;

public final class BeamShape {
    public static final double RING_MIN_RADIUS = 2.5;

    public static final double RING_WIDE_RADIUS = 6.0;

    public static final double RING_REACH = 0.6;

    public static final double SPINE_SPACING = 4.0;

    public static final double SPINE_MIN_ELONGATION = 2.0;

    public static final double SPINE_INSET = 0.5;

    private BeamShape() {
    }

    public static double[][] spine(double lx, double ly, double lz, double ax, double ay, double az, double centre,
                                   double nx, double ny, double nz, double poolRadius, double reach, double spacing) {
        final double al = Math.sqrt(ax * ax + ay * ay + az * az);
        final double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (!(al > 1.0e-9) || !(nl > 1.0e-9) || !(centre > 0.0) || !(poolRadius > 0.0) || !(reach > centre)
                || !(spacing > 0.0)) {
            return new double[0][];
        }
        ax /= al;
        ay /= al;
        az /= al;
        nx /= nl;
        ny /= nl;
        nz /= nl;

        final double an = ax * nx + ay * ny + az * nz;
        if (an >= -1.0e-6) {
            return new double[0][];
        }

        double mx = nx - an * ax;
        double my = ny - an * ay;
        double mz = nz - an * az;
        final double ml = Math.sqrt(mx * mx + my * my + mz * mz);
        if (ml < 1.0e-6) {
            return new double[0][];
        }
        mx /= ml;
        my /= ml;
        mz /= ml;

        double gx = ax - an * nx;
        double gy = ay - an * ny;
        double gz = az - an * nz;
        final double gl = Math.sqrt(gx * gx + gy * gy + gz * gz);
        if (gl < 1.0e-6) {
            return new double[0][];
        }
        gx /= gl;
        gy /= gl;
        gz /= gl;
        final double theta = Math.atan2(poolRadius, centre);
        final double ct = Math.cos(theta);
        final double st = Math.sin(theta);

        final double px = lx + ax * centre;
        final double py = ly + ay * centre;
        final double pz = lz + az * centre;
        final double sNear = edgeAlong(lx, ly, lz, ct * ax - st * mx, ct * ay - st * my, ct * az - st * mz,
                nx, ny, nz, centre * an, px, py, pz, gx, gy, gz);
        double sFar = edgeAlong(lx, ly, lz, ct * ax + st * mx, ct * ay + st * my, ct * az + st * mz,
                nx, ny, nz, centre * an, px, py, pz, gx, gy, gz);

        final double wg = centre * (ax * gx + ay * gy + az * gz);
        final double sReach = -wg + Math.sqrt(Math.max(0.0, wg * wg - centre * centre + reach * reach)) - SPINE_INSET;
        if (Double.isNaN(sNear)) {
            return new double[0][];
        }
        sFar = Double.isNaN(sFar) ? sReach : Math.min(sFar, sReach);
        final double length = sFar - sNear;
        if (!(length > 0.0) || length < SPINE_MIN_ELONGATION * 2.0 * poolRadius) {
            return new double[0][];
        }
        final int n = Math.max(1, (int) Math.ceil(length / spacing - 1.0e-9));
        final double[][] points = new double[n + 1][];
        for (int i = 0; i <= n; i++) {
            final double s = sNear + length * i / n;
            points[i] = new double[] {px + gx * s, py + gy * s, pz + gz * s};
        }
        return points;
    }

    private static double edgeAlong(double lx, double ly, double lz, double ex, double ey, double ez,
                                    double nx, double ny, double nz, double wn,
                                    double px, double py, double pz, double gx, double gy, double gz) {
        final double en = ex * nx + ey * ny + ez * nz;
        if (en >= -1.0e-9) {
            return Double.NaN;
        }
        final double t = wn / en;
        if (!(t > 0.0)) {
            return Double.NaN;
        }
        return (lx + ex * t - px) * gx + (ly + ey * t - py) * gy + (lz + ez * t - pz) * gz;
    }

    public static int levelAt(double distance, int level, double fullDistance, double fadeDistance,
                              int farLevel, double reach) {
        if (!(distance >= 0.0) || distance > reach || level <= 0) {
            return 0;
        }
        final int top = Math.min(level, EmissionPlan.MAX_LEVEL);
        if (distance <= fullDistance || fadeDistance <= fullDistance) {
            return top;
        }
        final double t = Math.min(1.0, (distance - fullDistance) / (fadeDistance - fullDistance));
        final int far = Math.max(0, Math.min(top, farLevel));
        return (int) Math.round(top + (far - top) * t);
    }

    public static double poolRadius(double distance, double baseRadius, double spread, double maxRadius) {
        final double r = baseRadius + spread * Math.max(0.0, distance);
        return Math.max(0.0, Math.min(maxRadius, r));
    }

    public static int ringRays(double poolRadius) {
        if (!(poolRadius >= RING_MIN_RADIUS)) {
            return 0;
        }
        return poolRadius < RING_WIDE_RADIUS ? 6 : 8;
    }

    public static double ringHalfAngle(double poolRadius, double distance) {
        return Math.atan2(RING_REACH * Math.max(0.0, poolRadius), Math.max(1.0, distance));
    }

    public static double[][] ring(double dx, double dy, double dz, double halfAngle, int count) {
        final double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (count <= 0 || !(len > 1.0e-9) || !Double.isFinite(len)) {
            return new double[0][];
        }
        final double ax = dx / len;
        final double ay = dy / len;
        final double az = dz / len;

        final double hx = Math.abs(ay) < 0.9 ? 0.0 : 1.0;
        final double hy = Math.abs(ay) < 0.9 ? 1.0 : 0.0;

        double ux = -az * hy;
        double uy = az * hx;
        double uz = ax * hy - ay * hx;
        final double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= ul;
        uy /= ul;
        uz /= ul;
        final double vx = ay * uz - az * uy;
        final double vy = az * ux - ax * uz;
        final double vz = ax * uy - ay * ux;
        final double cos = Math.cos(halfAngle);
        final double sin = Math.sin(halfAngle);
        final double[][] rays = new double[count][];
        for (int k = 0; k < count; k++) {
            final double phi = 2.0 * Math.PI * k / count;
            final double c = Math.cos(phi);
            final double s = Math.sin(phi);
            rays[k] = new double[] {
                    cos * ax + sin * (c * ux + s * vx),
                    cos * ay + sin * (c * uy + s * vy),
                    cos * az + sin * (c * uz + s * vz)};
        }
        return rays;
    }
}
