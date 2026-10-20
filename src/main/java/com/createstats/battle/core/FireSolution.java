package com.createstats.battle.core;

// yaw is minecraft's atan2(-dx, dz). 0 is +Z. don't fight it
public final class FireSolution {
    public enum Kind { OK, OUT_OF_RANGE, OUT_OF_ARC }

    public record Answer(Kind kind, double yaw, double pitch, double flightTicks, double[] intercept,
                         double range, double leadDeg, double needed) {}

    public static final int MAX_PASSES = 8;

    public static final double T_TOLERANCE = 0.05;

    public static final double WARM_BRACKET_DEG = 1.0;

    private static final double WARM_BRACKET = Math.toRadians(WARM_BRACKET_DEG);

    private static final double ROOT_TOLERANCE = 1.0E-10;

    private static final int MAX_ROOT_STEPS = 64;

    private static final double MIN_REACH = 1.0E-6;

    private static final double DRAG_FREE = 1.0E-10;

    private static final double STEPPED_TOP = Math.toRadians(89.9);

    private static final double SCAN_STEP = Math.toRadians(1.0);

    private static final double RISE_PROBE = 1.0E-6;

    private static final double GOLDEN = 0.6180339887498949;

    private FireSolution() {
    }

    public static Answer solve(Trajectory shell, double v0, double[] muzzle, double[] targetPos, double[] targetVel,
                               double minPitch, double maxPitch, Answer warm) {
        return solve(shell, v0, muzzle, targetPos, targetVel, minPitch, maxPitch, warm, null);
    }

    static Answer solve(Trajectory shell, double v0, double[] muzzle, double[] targetPos, double[] targetVel,
                        double minPitch, double maxPitch, Answer warm, int[] passesOut) {
        double lo = Math.min(minPitch, maxPitch);
        double hi = Math.max(minPitch, maxPitch);
        double rx = targetPos[0] - muzzle[0];
        double ry = targetPos[1] - muzzle[1];
        double rz = targetPos[2] - muzzle[2];
        double vx = targetVel == null ? 0.0 : targetVel[0];
        double vy = targetVel == null ? 0.0 : targetVel[1];
        double vz = targetVel == null ? 0.0 : targetVel[2];
        double dist = Math.sqrt(rx * rx + ry * ry + rz * rz);
        Shot shot = new Shot(shell, v0);
        if (!(v0 > 0.0) || !Double.isFinite(dist)) {
            if (passesOut != null) passesOut[0] = 0;
            return outOfRange(shot, lo, hi, muzzle, rx, ry, rz, rx, ry, rz, dist);
        }

        double t = dist / v0;
        double hint = Double.NaN;
        if (warm != null && warm.kind() == Kind.OK && warm.flightTicks() >= 0.0
                && Double.isFinite(warm.flightTicks()) && Double.isFinite(warm.pitch())) {
            t = warm.flightTicks();
            hint = Math.toRadians(warm.pitch());
        } else if (warm != null && warm.kind() == Kind.OUT_OF_ARC && Double.isFinite(warm.needed())) {
            hint = Math.toRadians(warm.needed());
            double flat = Math.max(Math.sqrt(rx * rx + rz * rz), MIN_REACH);
            if (Double.isFinite(shot.height(hint, flat)) && Double.isFinite(shot.ticks)) t = shot.ticks;
        }

        double px = rx;
        double py = ry;
        double pz = rz;
        double theta = Double.NaN;
        int passes = 0;
        while (passes < MAX_PASSES) {
            passes++;
            px = rx + vx * t;
            py = ry + vy * t;
            pz = rz + vz * t;
            double d = Math.max(Math.sqrt(px * px + pz * pz), MIN_REACH);
            theta = shot.lowElevation(d, py, hint);
            if (Double.isNaN(theta)) break;
            double next = shot.ticks;
            boolean settled = Math.abs(next - t) < T_TOLERANCE;
            t = next;
            hint = theta;
            if (settled) break;
        }
        if (passesOut != null) passesOut[0] = passes;

        if (Double.isNaN(theta)) {
            return outOfRange(shot, lo, hi, muzzle, px, py, pz, rx, ry, rz, dist);
        }
        double yaw = yawOf(px, pz);
        double pitch = Math.toDegrees(theta);
        double[] intercept = {muzzle[0] + px, muzzle[1] + py, muzzle[2] + pz};
        if (pitch < lo || pitch > hi) {
            double limit = pitch < lo ? lo : hi;
            return new Answer(Kind.OUT_OF_ARC, yaw, limit, Double.NaN, intercept, dist,
                    lead(rx, ry, rz, yaw, limit), pitch);
        }
        double range = Math.sqrt(px * px + py * py + pz * pz);
        return new Answer(Kind.OK, yaw, pitch, t, intercept, range, lead(rx, ry, rz, yaw, pitch), 0.0);
    }

    // best allowed elevation is the best one clamped into the limits
    public static double maxRange(Trajectory shell, double v0, double minPitch, double maxPitch) {
        Shot shot = new Shot(shell, v0);
        shot.bestPitch(Math.min(minPitch, maxPitch), Math.max(minPitch, maxPitch));
        return shot.bestReach;
    }

    public static double maxRangePitch(Trajectory shell, double v0, double minPitch, double maxPitch) {
        return new Shot(shell, v0).bestPitch(Math.min(minPitch, maxPitch), Math.max(minPitch, maxPitch));
    }

    public static double[] direction(double yawDeg, double pitchDeg) {
        double yr = Math.toRadians(yawDeg);
        double pr = Math.toRadians(pitchDeg);
        double cp = Math.cos(pr);
        return new double[]{-Math.sin(yr) * cp, Math.sin(pr), Math.cos(yr) * cp};
    }

    public static double yawOf(double dx, double dz) {
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        if (yaw <= -180.0) yaw += 360.0;
        return yaw + 0.0;
    }

    public static double pitchOf(double dx, double dy, double dz) {
        return Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
    }

    public static double angleDeg(double[] a, double[] b) {
        return angle(a[0], a[1], a[2], b[0], b[1], b[2]);
    }

    public static double wrap(double a, double b) {
        double d = (a - b) % 360.0;
        if (d > 180.0) {
            d -= 360.0;
        } else if (d <= -180.0) {
            d += 360.0;
        }
        return d;
    }

    private static Answer outOfRange(Shot shot, double lo, double hi, double[] muzzle, double px, double py,
                                     double pz, double rx, double ry, double rz, double dist) {
        double yaw = yawOf(px, pz);
        double pitch = shot.bestPitch(lo, hi);
        double[] point = {muzzle[0] + px, muzzle[1] + py, muzzle[2] + pz};
        return new Answer(Kind.OUT_OF_RANGE, yaw, pitch, Double.NaN, point, dist, lead(rx, ry, rz, yaw, pitch),
                shot.bestReach);
    }

    private static double lead(double rx, double ry, double rz, double yaw, double pitch) {
        double yr = Math.toRadians(yaw);
        double pr = Math.toRadians(pitch);
        double cp = Math.cos(pr);
        return angle(rx, ry, rz, -Math.sin(yr) * cp, Math.sin(pr), Math.cos(yr) * cp);
    }

    private static double angle(double ax, double ay, double az, double bx, double by, double bz) {
        if ((ax == 0.0 && ay == 0.0 && az == 0.0) || (bx == 0.0 && by == 0.0 && bz == 0.0)) return 0.0;
        double cx = ay * bz - az * by;
        double cy = az * bx - ax * bz;
        double cz = ax * by - ay * bx;
        double cross = Math.sqrt(cx * cx + cy * cy + cz * cz);
        double dot = ax * bx + ay * by + az * bz;
        return Math.toDegrees(Math.atan2(cross, dot));
    }

    static final class Shot {
        private final double g;
        private final double k;
        private final double v0;
        private final boolean quadratic;
        private final boolean closed;
        private final boolean dragFree;

        private final double c;

        private final double lnq;
        private final double gOverK;

        private final double gKm1;
        private final double[] x = new double[3];
        private final double[] v = new double[3];

        double ticks;

        double bestReach;

        Shot(Trajectory shell, double v0) {
            this.g = shell.gravity();
            this.k = shell.drag();
            this.v0 = v0;
            this.quadratic = shell.quadratic();
            this.closed = shell.closedForm();
            this.dragFree = closed && k < DRAG_FREE;
            this.c = 1.0 - 0.5 * k;
            boolean drag = closed && !dragFree;
            this.lnq = drag ? Math.log1p(-k) : 0.0;
            this.gOverK = drag ? g / k : 0.0;
            double kappa = drag ? k / (c * -lnq) : 1.0;
            this.gKm1 = drag ? g * (kappa - 1.0) / (k * v0) : 0.0;
        }

        double lowElevation(double d, double h, double hint) {
            if (g > 0.0) return Double.NaN;
            double los = Math.atan2(h, d);
            if (g == 0.0) {
                return Double.isFinite(height(los, d)) && Double.isFinite(ticks) ? los : Double.NaN;
            }
            return closed ? closedLow(d, h, hint, los) : steppedLow(d, h, hint, los);
        }

        double height(double th, double d) {
            if (!closed) return steppedHeight(th, d);
            ticks = Double.NaN;
            double cs = Math.cos(th);
            if (!(cs > 0.0)) return Double.NEGATIVE_INFINITY;
            double v0h = v0 * cs;
            double tan = Math.sin(th) / cs;
            if (dragFree) {
                double n = d / v0h;
                ticks = n;
                return d * tan + 0.5 * g * n * n;
            }

            double a = d * k / (c * v0h);
            if (!(a < 1.0)) return Double.NEGATIVE_INFINITY;
            double n = Math.log1p(-a) / lnq;
            ticks = n;

            return d * tan + gOverK * (n - d / v0h);
        }

        double slope(double th, double d) {
            double cs = Math.cos(th);
            double sn = Math.sin(th);
            if (dragFree) return d / (cs * cs) * (1.0 + g * d * sn / (cs * v0 * v0));
            double a0 = d * k / (c * v0);
            return d * phi(cs, sn, d, a0) / (cs * cs * (cs - a0));
        }

        private double phi(double cs, double sn, double d, double a0) {
            return (cs - a0) + sn * (gKm1 * cs + g * d / (c * v0 * v0));
        }

        private boolean closedRising(double th, double d) {
            double cs = Math.cos(th);
            double sn = Math.sin(th);
            if (dragFree) return 1.0 + g * d * sn / (cs * v0 * v0) > 0.0;
            return phi(cs, sn, d, d * k / (c * v0)) > 0.0;
        }

        private double closedLow(double d, double h, double hint, double los) {
            double top;
            if (dragFree) {
                top = 0.5 * Math.PI;
            } else {
                double a0 = d * k / (c * v0);
                if (!(a0 < 1.0)) return Double.NaN;
                top = Math.acos(a0);
            }
            if (Double.isFinite(hint)) {
                double lo = hint - WARM_BRACKET;
                double hi = hint + WARM_BRACKET;
                if (lo > -top && hi < top && height(lo, d) - h < 0.0 && height(hi, d) - h >= 0.0
                        && closedRising(hi, d)) {
                    return newton(lo, hi, d, h);
                }
            }
            double peak = closedPeak(d, top);
            if (!(height(peak, d) - h >= 0.0)) return Double.NaN;

            double bottom = los < peak ? Math.max(los, -top) : -top;
            return newton(bottom, peak, d, h);
        }

        private double closedPeak(double d, double top) {
            if (dragFree) return Math.atan2(v0 * v0, -g * d);
            double a0 = d * k / (c * v0);
            double lo = 0.0;
            double hi = top;
            for (int i = 0; i < MAX_ROOT_STEPS && hi - lo > ROOT_TOLERANCE; i++) {
                double m = 0.5 * (lo + hi);
                if (phi(Math.cos(m), Math.sin(m), d, a0) > 0.0) {
                    lo = m;
                } else {
                    hi = m;
                }
            }
            return 0.5 * (lo + hi);
        }

        private double newton(double lo, double hi, double d, double h) {
            double th = 0.5 * (lo + hi);
            double f = height(th, d) - h;
            double df = slope(th, d);
            double dxOld = hi - lo;
            double dx = dxOld;
            for (int i = 0; i < MAX_ROOT_STEPS; i++) {
                boolean bisect = !(Double.isFinite(f) && Double.isFinite(df) && df > 0.0)
                        || ((th - hi) * df - f) * ((th - lo) * df - f) > 0.0
                        || Math.abs(2.0 * f) > Math.abs(dxOld * df);
                dxOld = dx;
                if (bisect) {
                    dx = 0.5 * (hi - lo);
                    th = lo + dx;
                } else {
                    dx = f / df;
                    th -= dx;
                }
                f = height(th, d) - h;
                if (Math.abs(dx) < ROOT_TOLERANCE) break;
                df = slope(th, d);
                if (f < 0.0) {
                    lo = th;
                } else {
                    hi = th;
                }
            }
            return th;
        }

        private double steppedHeight(double th, double d) {
            ticks = Double.NaN;
            x[0] = 0.0;
            x[1] = 0.0;
            x[2] = 0.0;
            v[0] = v0 * Math.cos(th);
            v[1] = v0 * Math.sin(th);
            v[2] = 0.0;
            if (!(v[0] > 0.0)) return Double.NEGATIVE_INFINITY;
            for (int i = 0; i < Trajectory.MAX_STEPS; i++) {
                double h0 = x[0];
                double y0 = x[1];
                Trajectory.step(x, v, g, k, quadratic);
                if (x[0] >= d) {
                    double f = (d - h0) / (x[0] - h0);
                    ticks = i + f;
                    return y0 + f * (x[1] - y0);
                }
                if (!(v[0] > 0.0)) break;
            }
            return Double.NEGATIVE_INFINITY;
        }

        private double steppedLow(double d, double h, double hint, double los) {
            if (Double.isFinite(hint)) {
                double lo = hint - WARM_BRACKET;
                double hi = hint + WARM_BRACKET;
                if (lo > -STEPPED_TOP && hi < STEPPED_TOP && steppedHeight(lo, d) - h < 0.0) {
                    double fhi = steppedHeight(hi, d) - h;
                    if (fhi >= 0.0 && steppedHeight(hi + RISE_PROBE, d) - h > fhi) return bisect(lo, hi, d, h);
                }
            }

            double before = los;
            double prev = los;
            double fPrev = steppedHeight(los, d) - h;
            for (double th = los + SCAN_STEP; th < STEPPED_TOP + SCAN_STEP; th += SCAN_STEP) {
                double at = Math.min(th, STEPPED_TOP);
                double f = steppedHeight(at, d) - h;
                if (f >= 0.0) return bisect(prev, at, d, h);
                if (f < fPrev) {
                    double top = golden(before, at, d);
                    return steppedHeight(top, d) - h >= 0.0 ? bisect(before, top, d, h) : Double.NaN;
                }
                before = prev;
                prev = at;
                fPrev = f;
                if (at >= STEPPED_TOP) break;
            }
            return Double.NaN;
        }

        private double bisect(double lo, double hi, double d, double h) {
            for (int i = 0; i < MAX_ROOT_STEPS && hi - lo > ROOT_TOLERANCE; i++) {
                double m = 0.5 * (lo + hi);
                if (steppedHeight(m, d) - h < 0.0) {
                    lo = m;
                } else {
                    hi = m;
                }
            }
            steppedHeight(hi, d);
            return hi;
        }

        private double golden(double a, double b, double d) {
            double x1 = b - GOLDEN * (b - a);
            double x2 = a + GOLDEN * (b - a);
            double f1 = steppedHeight(x1, d);
            double f2 = steppedHeight(x2, d);
            for (int i = 0; i < MAX_ROOT_STEPS && b - a > ROOT_TOLERANCE; i++) {
                if (f1 < f2) {
                    a = x1;
                    x1 = x2;
                    f1 = f2;
                    x2 = a + GOLDEN * (b - a);
                    f2 = steppedHeight(x2, d);
                } else {
                    b = x2;
                    x2 = x1;
                    f2 = f1;
                    x1 = b - GOLDEN * (b - a);
                    f1 = steppedHeight(x1, d);
                }
            }
            return 0.5 * (a + b);
        }

        double bestPitch(double lo, double hi) {
            if (!(v0 > 0.0)) {
                bestReach = 0.0;
                return clamp(0.0, lo, hi);
            }
            if (!(g < 0.0)) {
                bestReach = pathLength();
                return clamp(0.0, lo, hi);
            }
            double best;
            if (dragFree) {
                best = clamp(45.0, lo, hi);
            } else {
                double a = 0.0;
                double b = 0.5 * Math.PI;
                double x1 = b - GOLDEN * (b - a);
                double x2 = a + GOLDEN * (b - a);
                double f1 = levelReach(x1);
                double f2 = levelReach(x2);

                for (int i = 0; i < MAX_ROOT_STEPS && b - a > 1.0E-5; i++) {
                    if (f1 < f2) {
                        a = x1;
                        x1 = x2;
                        f1 = f2;
                        x2 = a + GOLDEN * (b - a);
                        f2 = levelReach(x2);
                    } else {
                        b = x2;
                        x2 = x1;
                        f2 = f1;
                        x1 = b - GOLDEN * (b - a);
                        f1 = levelReach(x1);
                    }
                }
                best = clamp(Math.toDegrees(0.5 * (a + b)), lo, hi);
            }
            bestReach = levelReach(Math.toRadians(best));
            return best;
        }

        private double levelReach(double th) {
            if (!(th > 0.0)) return 0.0;
            double cs = Math.cos(th);
            double sn = Math.sin(th);
            if (!closed) return steppedLevelReach(cs, sn);
            double v0h = v0 * cs;
            double v0y = v0 * sn;
            if (dragFree) return v0h * 2.0 * v0y / -g;
            double w = v0y - gOverK;
            if (!(-c * w * lnq + g > 0.0)) return 0.0;
            double n = c * w / -g;
            for (int i = 0; i < MAX_ROOT_STEPS; i++) {
                double qm1 = Math.expm1(n * lnq);
                double f = -c * w * qm1 + g * n;
                double df = -c * w * lnq * (1.0 + qm1) + g;
                double step = f / df;
                n -= step;
                if (Math.abs(step) < 1.0E-12 * (1.0 + n)) break;
            }
            return n > 0.0 ? c * v0h * -Math.expm1(n * lnq) / k : 0.0;
        }

        private double steppedLevelReach(double cs, double sn) {
            x[0] = 0.0;
            x[1] = 0.0;
            x[2] = 0.0;
            v[0] = v0 * cs;
            v[1] = v0 * sn;
            v[2] = 0.0;
            for (int i = 0; i < Trajectory.MAX_STEPS; i++) {
                double h0 = x[0];
                double y0 = x[1];
                Trajectory.step(x, v, g, k, quadratic);
                if (x[1] < 0.0) {
                    double f = y0 / (y0 - x[1]);
                    return h0 + f * (x[0] - h0);
                }
            }

            return x[0];
        }

        private double pathLength() {
            if (closed) return dragFree ? Double.POSITIVE_INFINITY : c * v0 / k;
            x[0] = 0.0;
            x[1] = 0.0;
            x[2] = 0.0;
            v[0] = v0;
            v[1] = 0.0;
            v[2] = 0.0;
            for (int i = 0; i < Trajectory.MAX_STEPS; i++) {
                Trajectory.step(x, v, g, k, quadratic);
            }
            return Math.sqrt(x[0] * x[0] + x[1] * x[1]);
        }

        private static double clamp(double value, double lo, double hi) {
            return Math.max(lo, Math.min(hi, value));
        }
    }
}
