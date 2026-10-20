package com.createstats.client.ponder.powertrack;

import java.util.ArrayList;
import java.util.List;

public final class CarRun {
    public static final double CRUISE = 0.2;

    public static final int RAMP = 30;

    public record Glide(int start, double distance, int ramp, int cruiseTicks) {
        public int ticks() {
            return 2 * ramp + cruiseTicks;
        }

        public int end() {
            return start + ticks();
        }

        double speed() {
            final double span = ramp + cruiseTicks;
            return span <= 0 ? 0.0 : distance / span;
        }

        double covered(double t) {
            if (t <= 0) {
                return 0.0;
            }
            if (t >= ticks()) {
                return distance;
            }
            final double v = speed();
            if (t < ramp) {
                final double u = t / ramp;
                return v * ramp * (u * u * u - u * u * u * u / 2.0);
            }
            if (t < ramp + cruiseTicks) {
                return v * ramp / 2.0 + v * (t - ramp);
            }
            final double u = (t - ramp - cruiseTicks) / ramp;
            return v * ramp / 2.0 + v * cruiseTicks + v * ramp * (u - u * u * u + u * u * u * u / 2.0);
        }
    }

    private final double rest;
    private final List<Glide> glides = new ArrayList<>();

    public CarRun(double rest) {
        this.rest = rest;
    }

    public CarRun glide(int start, double distance) {
        final Glide last = glides.isEmpty() ? null : glides.get(glides.size() - 1);
        if (last != null && start < last.end()) {
            throw new IllegalArgumentException("glide at " + start + " overlaps the one ending at " + last.end());
        }
        final double d = Math.abs(distance);
        if (d >= CRUISE * RAMP) {
            final int cruise = (int) Math.round(d / CRUISE - RAMP);
            glides.add(new Glide(start, distance, RAMP, cruise));
        } else {
            final int ramp = Math.max(1, (int) Math.round(d / CRUISE));
            glides.add(new Glide(start, distance, ramp, 0));
        }
        return this;
    }

    public List<Glide> glides() {
        return List.copyOf(glides);
    }

    public Glide glideAt(double tick) {
        for (Glide g : glides) {
            if (tick >= g.start() && tick < g.end()) {
                return g;
            }
        }
        return null;
    }

    public int settled() {
        return glides.isEmpty() ? 0 : glides.get(glides.size() - 1).end();
    }

    public double at(double tick) {
        double x = rest;
        for (Glide g : glides) {
            if (tick <= g.start()) {
                break;
            }
            x += g.covered(tick - g.start());
        }
        return x;
    }

    public boolean moving(int tick) {
        return Math.abs(at(tick) - at(tick - 1)) > 1.0e-9;
    }
}
