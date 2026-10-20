package com.createstats.ground;

import java.util.Locale;

public final class GroundCost {
    public enum Phase {
        SAMPLE("tick"), BUILD("snapshot");

        public final String label;

        Phase(String label) {
            this.label = label;
        }
    }

    public static final int MEAN_TICKS = 20;

    public static final int WORST_TICKS = 100;

    private static final Phase[] PHASES = Phase.values();

    private final long[] current = new long[PHASES.length];
    private final long[][] ring = new long[PHASES.length][WORST_TICKS];
    private int head;
    private int filled;

    public void add(Phase phase, long nanos) {
        if (phase != null && nanos > 0L) {
            this.current[phase.ordinal()] += nanos;
        }
    }

    public void endTick() {
        for (int p = 0; p < PHASES.length; p++) {
            this.ring[p][this.head] = this.current[p];
            this.current[p] = 0L;
        }
        this.head = (this.head + 1) % WORST_TICKS;
        this.filled = Math.min(WORST_TICKS, this.filled + 1);
    }

    public int ticks() {
        return this.filled;
    }

    public double meanMicros(Phase phase) {
        final int n = Math.min(MEAN_TICKS, this.filled);
        if (n == 0) {
            return 0.0;
        }
        long sum = 0L;
        for (int i = 1; i <= n; i++) {
            sum += this.ring[phase.ordinal()][Math.floorMod(this.head - i, WORST_TICKS)];
        }
        return sum / (double) n / 1000.0;
    }

    public double worstMicros(Phase phase) {
        long worst = 0L;
        for (int i = 0; i < this.filled; i++) {
            worst = Math.max(worst, this.ring[phase.ordinal()][i]);
        }
        return worst / 1000.0;
    }

    public String summary() {
        final double mean = meanMicros(Phase.SAMPLE) + meanMicros(Phase.BUILD);
        final double worst = Math.max(worstMicros(Phase.SAMPLE), worstMicros(Phase.BUILD));
        return String.format(Locale.ROOT, "Telemetry: %.0f us a tick (worst %.0f)", mean, worst);
    }
}
