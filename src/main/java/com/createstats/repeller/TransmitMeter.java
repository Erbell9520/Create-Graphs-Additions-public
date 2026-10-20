package com.createstats.repeller;

public final class TransmitMeter {
    public static final double ALPHA = 0.05;

    public static final double HYSTERESIS = 2.0;

    public static final double SETTLE_MIN = 1.0;

    public static final int SETTLE_TICKS = 100;

    public static final double SNAP = 0.5;

    public static final double MIN_RADIUS = 1.0;

    private double smoothed;
    private double published;

    private int side;
    private int sideTicks;

    public void reset() {
        smoothed = 0.0;
        published = 0.0;
        side = 0;
        sideTicks = 0;
    }

    public boolean tick(int setRadius, double spent) {
        smoothed += (Math.max(0.0, spent) - smoothed) * ALPHA;
        final double raw = RepellerDraw.effectiveRadius(setRadius, smoothed);
        final double target;
        if (raw < MIN_RADIUS) {
            target = 0.0;
        } else if (raw >= setRadius - SNAP) {
            target = setRadius;
        } else {
            target = raw;
        }
        final double d = target - published;
        final int now = d >= SETTLE_MIN ? 1 : d <= -SETTLE_MIN ? -1 : 0;
        if (now != 0 && now == side) {
            sideTicks++;
        } else {
            side = now;
            sideTicks = now != 0 ? 1 : 0;
        }
        if (target == published) {
            return false;
        }
        final boolean end = target == 0.0 || target == setRadius;
        if (end || Math.abs(d) >= HYSTERESIS || (side != 0 && sideTicks >= SETTLE_TICKS)) {
            published = target;
            side = 0;
            sideTicks = 0;
            return true;
        }
        return false;
    }

    public double publishedRadius() {
        return published;
    }

    public double smoothedFePerTick() {
        return smoothed;
    }

    public double powerFraction(int setRadius) {
        final int full = RepellerDraw.drawFor(setRadius);
        return full <= 0 ? 0.0 : Math.max(0.0, Math.min(1.0, smoothed / full));
    }

    public void restore(double smoothedFePerTick, double publishedRadius) {
        this.smoothed = Math.max(0.0, smoothedFePerTick);
        this.published = Math.max(0.0, publishedRadius);
        this.side = 0;
        this.sideTicks = 0;
    }
}
