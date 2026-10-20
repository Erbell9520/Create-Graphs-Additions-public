package com.createstats.vccmk4.air.core;

import java.util.ArrayList;
import java.util.List;

// look ahead, otherwise go-to behind a hill flies into the hill
public final class TerrainGuard {
    public static final double CLEARANCE = 4.0;

    public static final double STEP = 4.0;

    public static final double MIN_REACH = 64.0;
    public static final double REACH_SECONDS = 16.0;
    public static final double MAX_REACH = 160.0;

    public static final double SAFETY = 1.5;

    public static final double ARM_PAD_S = 4.0;

    public static final double BRAKE_SHARE = 0.5;

    public static final double STOP_MARGIN = 8.0;

    static final double MIN_SPEED = 0.25;

    static final double SLOW_TOLERANCE = 0.5;

    public static final int STALE_TICKS = 40;

    private TerrainGuard() {
    }

    public record Sample(double along, double ground) {
    }

    public record Profile(double x0, double z0, double ux, double uz, double keelDrop, double halfLength,
                          List<Sample> samples) {
        public Profile {
            samples = samples == null ? List.of() : List.copyOf(samples);
            keelDrop = Double.isFinite(keelDrop) ? Math.max(0.0, keelDrop) : 0.0;
            halfLength = Double.isFinite(halfLength) ? Math.max(0.0, halfLength) : 0.0;
        }

        public List<Sample> ahead(double x, double z) {
            double moved = (x - x0) * ux + (z - z0) * uz;
            if (!Double.isFinite(moved)) moved = 0.0;
            List<Sample> out = new ArrayList<>(samples.size());
            for (Sample s : samples) {
                double d = s.along() - moved - halfLength;

                if (d < -2.0 * halfLength) continue;
                out.add(new Sample(d, s.ground()));
            }
            return out;
        }

        public double speedAlong(double vx, double vz) {
            double v = vx * ux + vz * uz;
            return Double.isFinite(v) ? Math.max(0.0, v) : 0.0;
        }
    }

    public enum Verdict {
        CLEAR,

        CLIMB,

        SLOW,

        WALL,

        BLIND
    }

    public record Result(Verdict verdict, double floor, double speedCap, double groundY, double distance,
                         double peakY, double peakAt, double seen) {
        public static final Result NONE = new Result(Verdict.CLEAR, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.POSITIVE_INFINITY);

        public double fly(double held) {
            return floor > held ? floor : held;
        }

        public boolean acting() {
            return verdict != Verdict.CLEAR;
        }

        public String note() {
            return switch (verdict) {
                case CLEAR -> null;
                case CLIMB -> String.format("Terrain ahead: climbing to y %.0f to clear the ground at y %.0f, %.0f blocks on.",
                        floor, groundY, distance);
                case SLOW -> String.format("Terrain ahead: slowed to %.1f blocks a second to climb over the ground at y %.0f in time.",
                        speedCap, groundY);
                case WALL -> String.format("Terrain ahead above the ceiling: stopping short of the ground at y %.0f, %.0f blocks on."
                        + " Turn the ship or raise the cap.", groundY, distance);
                case BLIND -> String.format("Unloaded ground ahead: slowed to stop inside the %.0f blocks it can see.", seen);
            };
        }

        public String helmNote() {
            return switch (verdict) {
                case CLEAR, BLIND -> null;
                case WALL -> String.format("Terrain ahead above the ceiling: the ground at y %.0f, %.0f blocks on. Turn or stop.",
                        groundY, distance);
                case CLIMB, SLOW -> String.format("Terrain ahead: the ground at y %.0f, %.0f blocks on, is above the height held."
                        + " Climb or turn.", groundY, distance);
            };
        }
    }

    public static Profile read(double x, double z, double ux, double uz, double keelDrop, double halfLength,
                               double halfWidth, double reach, java.util.function.DoubleBinaryOperator ground) {
        List<Sample> out = new ArrayList<>();
        double side = Math.max(0.0, halfWidth);
        double back = Math.max(0.0, halfLength);
        for (double d = -back; d <= reach + back + 1.0e-9; d += STEP) {
            double top = Double.NEGATIVE_INFINITY;
            for (int k = -1; k <= 1; k++) {
                double h = ground.applyAsDouble(x + ux * d - uz * k * side, z + uz * d + ux * k * side);
                if (!Double.isFinite(h)) {
                    top = Double.NaN;
                    break;
                }
                top = Math.max(top, h);
            }
            out.add(new Sample(d, top));
        }
        return new Profile(x, z, ux, uz, keelDrop, back, out);
    }

    public static double reach(double speed) {
        double v = Double.isFinite(speed) ? Math.max(0.0, speed) : 0.0;
        return Math.min(MAX_REACH, Math.max(MIN_REACH, REACH_SECONDS * v));
    }

    public static double[] track(boolean goTo, double[] destination, double x, double z, double vx, double vz,
                                 double heading) {
        if (goTo && destination != null && destination.length >= 2 && Double.isFinite(destination[0])
                && Double.isFinite(destination[1])) {
            double dx = destination[0] - x;
            double dz = destination[1] - z;
            double d = Math.hypot(dx, dz);
            if (d > 1.0e-6) return new double[] {dx / d, dz / d};
        }
        double v = Math.hypot(vx, vz);
        if (v > 1.0) return new double[] {vx / v, vz / v};
        double h = Math.toRadians(heading);
        return new double[] {Math.sin(h), -Math.cos(h)};
    }

    public static double climbTime(double rise, Feel feel) {
        if (!(rise > 0.0)) return 0.0;
        double a = Math.max(1.0e-3, feel.lift);
        double c = Math.max(1.0e-3, feel.climb);
        double tAcc = c / a;
        double hAcc = 0.5 * c * tAcc;
        double t = rise <= hAcc ? Math.sqrt(2.0 * rise / a) : tAcc + (rise - hAcc) / c;
        return AltitudeLaw.LOOKAHEAD_S + t;
    }

    public static double stopSpeed(double room, Feel feel) {
        return Math.sqrt(2.0 * BRAKE_SHARE * feel.accel * Math.max(0.0, room));
    }

    public static Result assess(List<Sample> ahead, double y, double held, double keelDrop, double speed, Feel feel,
                                double ceiling) {
        if (ahead == null || ahead.isEmpty() || !Double.isFinite(y)) return Result.NONE;
        Feel f = feel == null ? Feel.STANDARD : feel;
        double hold = Double.isFinite(held) ? held : y;
        double top = Double.isNaN(ceiling) ? Double.POSITIVE_INFINITY : ceiling;
        double drop = Double.isFinite(keelDrop) ? Math.max(0.0, keelDrop) : 0.0;
        double v = Math.max(MIN_SPEED, Double.isFinite(speed) ? speed : 0.0);

        double floor = Double.NEGATIVE_INFINITY;
        double floorGround = Double.NaN;
        double floorAt = Double.NaN;
        double cap = Double.POSITIVE_INFINITY;
        Verdict capVerdict = Verdict.CLEAR;
        double capGround = Double.NaN;
        double capAt = Double.NaN;
        double peak = Double.NaN;
        double peakAt = Double.NaN;
        double seen = Double.POSITIVE_INFINITY;

        for (Sample s : ahead) {
            double d = Math.max(0.0, s.along());
            if (!Double.isFinite(s.ground())) {
                seen = Math.min(seen, d);
                continue;
            }
            if (d > seen) continue;
            double g = s.ground();
            if (Double.isNaN(peak) || g > peak) {
                peak = g;
                peakAt = d;
            }
            double need = g + CLEARANCE + drop;
            if (need <= y && need <= hold) continue;
            double t = d / v;
            if (need > top) {
                double c = d > 0.0 ? stopSpeed(d - STOP_MARGIN, f) : Double.POSITIVE_INFINITY;
                if (c < cap) {
                    cap = c;
                    capVerdict = Verdict.WALL;
                    capGround = g;
                    capAt = d;
                }
                continue;
            }
            if (need > hold) {
                double armAt = SAFETY * climbTime(need - hold, f) + ARM_PAD_S;
                if (t > armAt && d > 0.0) continue;
                if (need > floor) {
                    floor = need;
                    floorGround = g;
                    floorAt = d;
                }
            }

            double rise = need - y;
            if (d > 0.0 && rise > SLOW_TOLERANCE * CLEARANCE) {
                double needT = SAFETY * climbTime(rise, f);
                if (t < needT) {
                    double c = d / needT;
                    if (c < cap) {
                        cap = c;
                        capVerdict = Verdict.SLOW;
                        capGround = g;
                        capAt = d;
                    }
                }
            }
        }
        if (Double.isFinite(seen)) {
            double c = stopSpeed(seen - STOP_MARGIN, f);
            if (c < cap) {
                cap = c;
                capVerdict = Verdict.BLIND;
                capGround = Double.NaN;
                capAt = seen;
            }
        }

        if (capVerdict != Verdict.CLEAR && cap < speed + 1.0) {
            return new Result(capVerdict, floor, cap, capGround, capAt, peak, peakAt, seen);
        }
        if (floor > hold) {
            return new Result(Verdict.CLIMB, floor, cap, floorGround, floorAt, peak, peakAt, seen);
        }
        return new Result(Verdict.CLEAR, floor, cap, Double.NaN, Double.NaN, peak, peakAt, seen);
    }
}
