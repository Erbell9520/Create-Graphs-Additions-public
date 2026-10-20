package com.createstats.power;

public record FeDeviceDemand(int steady, int fill, int reportedDraw, int stored, int capacity,
                             boolean metered) {
    public static final FeDeviceDemand NONE = new FeDeviceDemand(0, 0, 0, 0, 0, true);

    public static final int FILL_TICKS = 40;

    public FeDeviceDemand {
        steady = Math.max(0, steady);
        fill = Math.max(0, fill);
        reportedDraw = Math.max(0, reportedDraw);
        stored = Math.max(0, stored);
        capacity = Math.max(0, capacity);
    }

    public int total() {
        return steady + fill;
    }

    public float fillFraction() {
        return capacity <= 0 ? 1.0f : Math.min(1.0f, stored / (float) capacity);
    }

    public boolean isCold(float mark) {
        return capacity > 0 && fillFraction() < mark;
    }

    public FeLoadCost cost() {
        return new FeLoadCost(steady, reportedDraw);
    }

    public FeDeviceDemand withSteady(int newSteady, boolean nowMetered) {
        return new FeDeviceDemand(newSteady, fill, reportedDraw, stored, capacity, nowMetered);
    }

    public FeDeviceDemand withoutFill() {
        return fill == 0 ? this : new FeDeviceDemand(steady, 0, reportedDraw, stored, capacity, metered);
    }

    public static FeDeviceDemand rated(int ratedDraw, int measuredDraw, int stored, int capacity,
                                       int fillTicks) {
        return new FeDeviceDemand(ratedDraw, fillRate(stored, capacity, fillTicks), measuredDraw,
                stored, capacity, true);
    }

    public static FeDeviceDemand consumer(int measuredDraw, int activeDrain, boolean running,
                                          int stored, int capacity, int fillTicks) {
        final int steady = running && measuredDraw > 0
                ? measuredDraw : Math.max(measuredDraw, activeDrain);
        return new FeDeviceDemand(steady, fillRate(stored, capacity, fillTicks), measuredDraw,
                stored, capacity, true);
    }

    public static FeDeviceDemand opaque(int headroom, int stored, int capacity, int fillTicks) {
        return new FeDeviceDemand(headroom, 0, headroom, stored, capacity, false);
    }

    public static FeDeviceDemand observed(int observedDraw, int reportedDraw, int stored,
                                          int capacity, int fillTicks) {
        return new FeDeviceDemand(observedDraw, fillRate(stored, capacity, fillTicks), reportedDraw,
                stored, capacity, true);
    }

    public static FeDeviceDemand learned(int learned, int deliverable, int stored, int capacity,
                                         int fillTicks) {
        return observed(Math.min(Math.max(0, learned), Math.max(0, deliverable)), 0, stored,
                capacity, fillTicks);
    }

    public static int fillRate(int stored, int capacity, int fillTicks) {
        final int room = capacity - stored;
        if (room <= 0 || capacity <= 0) {
            return 0;
        }
        final int paced = Math.max(1, capacity / Math.max(1, fillTicks));
        return Math.min(room, paced);
    }
}
