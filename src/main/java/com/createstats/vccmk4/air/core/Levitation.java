package com.createstats.vccmk4.air.core;

import java.util.List;

public record Levitation(double capped, double free, double drag) {
    public static final Levitation NONE = new Levitation(0.0, 0.0, 0.0);

    public Levitation {
        capped = positive(capped);
        free = positive(free);
        drag = positive(drag);
    }

    public record Cluster(double strength, double scale, double pressure, boolean selfLimited, boolean withPressure,
                          boolean frictionWithGravity, double slowVertical, double fastVertical, double transition) {
    }

    public static Levitation of(List<Cluster> clusters, double g) {
        if (clusters == null || clusters.isEmpty()) return NONE;
        double gravity = g > 0.0 && Double.isFinite(g) ? g : 11.0;
        double capped = 0.0;
        double free = 0.0;
        double drag = 0.0;
        for (Cluster c : clusters) {
            if (c == null || !(c.scale() > 0.0) || !Double.isFinite(c.strength()) || c.strength() == 0.0) continue;
            double p = c.withPressure() ? (Double.isFinite(c.pressure()) ? Math.max(0.0, c.pressure()) : 1.0) : 1.0;
            double lift = c.strength() * c.scale() * p;
            if (c.selfLimited()) capped += lift;
            else free += lift;
            double slow = c.transition() > 0.0 && Double.isFinite(c.slowVertical()) ? c.slowVertical() : 0.0;
            double fast = Double.isFinite(c.fastVertical()) ? c.fastVertical() : 0.0;
            drag += c.scale() * Math.max(0.0, slow + fast) * (c.frictionWithGravity() ? gravity : 1.0) * p;
        }
        return new Levitation(capped, free, drag);
    }

    public double lift(double mass, double g) {
        if (!(g > 0.0) || !Double.isFinite(g)) return 0.0;
        double m = mass > 0.0 && Double.isFinite(mass) ? mass : 0.0;
        return g * (free + Math.min(capped, m));
    }

    public boolean any() {
        return capped > 0.0 || free > 0.0;
    }

    private static double positive(double v) {
        return v > 0.0 && Double.isFinite(v) ? v : 0.0;
    }
}
