package com.createstats.inventory.core;

public final class FlowClock {
    public enum Kind {
        NONE,

        STEADY,
        FULL,
        EMPTY,
        LOW,
        HIGH,

        RUNTIME
    }

    public record Projection(Kind kind, double seconds) {
        public static final Projection NONE = new Projection(Kind.NONE, 0.0);
        public static final Projection STEADY = new Projection(Kind.STEADY, 0.0);

        public boolean counting() {
            return kind != Kind.NONE && kind != Kind.STEADY;
        }
    }

    public static final double HORIZON_SECONDS = 30.0 * 86_400.0;

    private boolean steady = true;

    public static double band(double capacity) {
        return Math.max(0.5, 0.001 * Math.max(0.0, capacity));
    }

    public Projection update(double quantity, double capacity, double netPerMinute, Double low, Double high,
                             boolean energy) {
        return update(quantity, capacity, netPerMinute, low, high, energy, capacity);
    }

    public Projection update(double quantity, double capacity, double netPerMinute, Double low, Double high,
                             boolean energy, double bandScale) {
        if (capacity <= 0.0) {
            this.steady = true;
            return Projection.NONE;
        }
        final double band = band(bandScale);
        final double magnitude = Math.abs(netPerMinute);
        if (this.steady) {
            if (magnitude > 2.0 * band) {
                this.steady = false;
            }
        } else if (magnitude < band) {
            this.steady = true;
        }
        if (this.steady) {
            return Projection.STEADY;
        }
        final double perSecond = netPerMinute / 60.0;
        final Projection p;
        if (netPerMinute > 0.0) {
            if (high != null && quantity < high) {
                p = new Projection(Kind.HIGH, (high - quantity) / perSecond);
            } else if (quantity < capacity) {
                p = new Projection(Kind.FULL, (capacity - quantity) / perSecond);
            } else {
                return Projection.STEADY;
            }
        } else {
            if (energy) {
                p = quantity > 0.0 ? new Projection(Kind.RUNTIME, quantity / -perSecond) : Projection.STEADY;
            } else if (low != null && quantity > low) {
                p = new Projection(Kind.LOW, (quantity - low) / -perSecond);
            } else if (quantity > 0.0) {
                p = new Projection(Kind.EMPTY, quantity / -perSecond);
            } else {
                return Projection.STEADY;
            }
        }
        return p.seconds() > HORIZON_SECONDS ? Projection.STEADY : p;
    }

    public boolean isSteady() {
        return this.steady;
    }

    public void reset() {
        this.steady = true;
    }
}
