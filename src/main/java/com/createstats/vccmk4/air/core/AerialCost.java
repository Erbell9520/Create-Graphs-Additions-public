package com.createstats.vccmk4.air.core;

import java.util.Locale;

public final class AerialCost {
    public enum Phase {
        SCAN("scan"), READ("read"), PILOT("pilot"), ACT("act"), TERRAIN("terrain"), SNAPSHOT("snapshot"),
        CONSOLE("console"), FORCES("forces");

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
    private final long[] totals = new long[WORST_TICKS];
    private final int[] changes = new int[MEAN_TICKS];
    private int currentChanges;
    private int head;
    private int filled;

    public void add(Phase phase, long nanos) {
        if (phase == null || nanos <= 0L) return;
        current[phase.ordinal()] += nanos;
    }

    public void changes(int n) {
        if (n > 0) currentChanges += n;
    }

    public void endTick() {
        long total = 0L;
        for (int i = 0; i < PHASES.length; i++) {
            ring[i][head] = current[i];
            total += current[i];
            current[i] = 0L;
        }
        totals[head] = total;
        changes[head % MEAN_TICKS] = currentChanges;
        currentChanges = 0;
        head = (head + 1) % WORST_TICKS;
        if (filled < WORST_TICKS) filled++;
    }

    public int ticks() {
        return filled;
    }

    private long mean(long[] values) {
        int n = Math.min(filled, MEAN_TICKS);
        if (n == 0) return 0L;
        long sum = 0L;
        for (int k = 1; k <= n; k++) sum += values[Math.floorMod(head - k, WORST_TICKS)];
        return sum / n;
    }

    private long worst(long[] values) {
        long w = 0L;
        for (int k = 1; k <= filled; k++) w = Math.max(w, values[Math.floorMod(head - k, WORST_TICKS)]);
        return w;
    }

    public double meanMicros(Phase phase) {
        return mean(ring[phase.ordinal()]) / 1000.0;
    }

    public double worstMicros(Phase phase) {
        return worst(ring[phase.ordinal()]) / 1000.0;
    }

    public double meanMicros() {
        return mean(totals) / 1000.0;
    }

    public double worstMicros() {
        return worst(totals) / 1000.0;
    }

    public int changesPerSecond() {
        int n = Math.min(filled, MEAN_TICKS);
        int sum = 0;
        for (int k = 1; k <= n; k++) sum += changes[Math.floorMod(head - k, MEAN_TICKS)];
        return sum;
    }

    public Phase heaviest() {
        if (filled == 0) return null;
        Phase best = null;
        long most = -1L;
        for (Phase p : PHASES) {
            long m = mean(ring[p.ordinal()]);
            if (m > most) {
                most = m;
                best = p;
            }
        }
        return best;
    }

    public String summary() {
        if (filled == 0) return "Cost: not measured yet";
        Phase h = heaviest();
        return String.format(Locale.ROOT, "Cost %.2f ms a tick (worst %.2f), most in %s; governors %d change%s a second",
                meanMicros() / 1000.0, worstMicros() / 1000.0, h == null ? "nothing" : h.label, changesPerSecond(),
                changesPerSecond() == 1 ? "" : "s");
    }

    public String phases() {
        StringBuilder s = new StringBuilder();
        for (Phase p : PHASES) {
            if (s.length() > 0) s.append(' ');
            s.append(p.label).append(' ').append(Math.round(meanMicros(p)));
        }
        return s.toString();
    }
}
