package com.createstats.vccmk4.air.core;

import java.util.List;

public final class AltitudeLaw {
    public static final double LOOKAHEAD_S = GasLaw.nearTicks() / 20.0;

    public static final double CLOSE_TICKS = 10.0;

    public static final double KV = 1.6;

    public static final double BRAKE_G = 2.0;

    public static final double OVERSPEED = 4.0;

    public static final double CEILING_RESERVE = 0.9;

    private AltitudeLaw() {
    }

    public record Limits(double climb, double accel) {
    }

    public static final double TRIM_SHIFT = 0.5;

    public record Cell(double gas, double capacity, double strength, double pressure, double maxGas, double lon) {
        public Cell(double gas, double capacity, double strength, double pressure, double maxGas) {
            this(gas, capacity, strength, pressure, maxGas, 0.0);
        }

        public Cell {
            lon = Double.isFinite(lon) ? lon : 0.0;
        }

        public double forcePerGas(double g) {
            return g * strength * pressure;
        }
    }

    public record Plan(double vRef, double accel, double force, double[] targets, boolean short_, boolean excess) {
    }

    public static double lookaheadError(double y, double vy, double targetY) {
        return targetY - (y + vy * LOOKAHEAD_S);
    }

    public static double referenceSpeed(double error, Limits limits) {
        return Math.signum(error) * Math.min(limits.climb(), Math.sqrt(2.0 * limits.accel() * Math.abs(error)));
    }

    public static double accel(double vRef, double vy, double g, Limits limits, double dragRate) {
        double want = KV * (vRef - vy);
        double limit = limits.accel();
        if (want * vy < 0.0) {
            double speed = Math.abs(vy);
            double air = dragRate > 0.0 && Double.isFinite(dragRate) ? dragRate * speed : 0.0;
            double over = OVERSPEED * Math.max(0.0, speed - limits.climb());
            limit = Math.min(limit + air + over, Math.max(limit, BRAKE_G * (g > 0.0 ? g : 11.0)));
        }
        return clamp(want, -limit, limit);
    }

    public static Plan plan(double y, double vy, double targetY, double mass, double g, double hidden,
                            List<Cell> cells, Limits limits) {
        return plan(y, vy, targetY, mass, g, hidden, cells, limits, 0.0, 0.0);
    }

    public static Plan plan(double y, double vy, double targetY, double mass, double g, double hidden,
                            List<Cell> cells, Limits limits, double trim, double dragRate) {
        double e = lookaheadError(y, vy, targetY);
        double vRef = referenceSpeed(e, limits);
        double accel = accel(vRef, vy, g, limits, dragRate);
        double force = mass * (g + accel) - (Double.isFinite(hidden) ? hidden : 0.0);

        double maxLift = maxLift(cells, g);
        double[] shares = shares(cells, g, trim);
        double[] targets = new double[cells.size()];
        for (int i = 0; i < cells.size(); i++) {
            Cell c = cells.get(i);
            double per = c.forcePerGas(g);
            if (!(per > 0.0) || !(c.maxGas() > 0.0)) continue;
            double want = Math.max(0.0, force * shares[i] / per);
            double step = (want - c.gas()) / CLOSE_TICKS;
            double gap = GasLaw.gapFor(step, c.capacity());
            targets[i] = clamp(c.gas() + gap, 0.0, c.maxGas());
        }
        return new Plan(vRef, accel, force, targets, force > maxLift, force < 0.0);
    }

    static double[] shares(List<Cell> cells, double g, double trim) {
        double[] out = new double[cells.size()];
        double span = 0.0;
        for (Cell c : cells) span = Math.max(span, Math.abs(c.lon()));
        double t = Double.isFinite(trim) ? clamp(trim, -1.0, 1.0) : 0.0;
        double sum = 0.0;
        for (int i = 0; i < cells.size(); i++) {
            Cell c = cells.get(i);
            double base = Math.max(0.0, c.maxGas()) * Math.max(0.0, c.forcePerGas(g));
            double tilt = span > 0.5 ? 1.0 + t * TRIM_SHIFT * c.lon() / span : 1.0;
            out[i] = base * Math.max(0.0, tilt);
            sum += out[i];
        }
        for (int i = 0; i < out.length; i++) out[i] = sum > 0.0 ? out[i] / sum : 0.0;
        return out;
    }

    public static double maxLift(List<Cell> cells, double g) {
        double s = 0.0;
        for (Cell c : cells) s += Math.max(0.0, c.maxGas()) * Math.max(0.0, c.forcePerGas(g));
        return s;
    }

    public static double lift(List<Cell> cells, double g) {
        double s = 0.0;
        for (Cell c : cells) s += Math.max(0.0, c.gas()) * Math.max(0.0, c.forcePerGas(g));
        return s;
    }

    public static double ceiling(double mass, double g, double hidden, List<Cell> cells, PressureModel air) {
        double held = 0.0;
        for (Cell c : cells) held += Math.max(0.0, c.maxGas()) * Math.max(0.0, c.strength());
        double need = mass * g - (Double.isFinite(hidden) ? hidden : 0.0);
        if (need <= 0.0) return Double.POSITIVE_INFINITY;
        if (!(held > 0.0) || !(g > 0.0)) return Double.NaN;
        double p = need / (CEILING_RESERVE * held * g);
        if (p > PressureModel.MAX_PRESSURE) return Double.NEGATIVE_INFINITY;
        return air.altitudeOf(p);
    }

    static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
