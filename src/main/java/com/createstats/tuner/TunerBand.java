package com.createstats.tuner;

public record TunerBand(int lo, int hi, int minor, int major, int divisor, String unit) {
    public TunerBand {
        if (hi <= lo || minor <= 0 || major <= 0 || divisor <= 0) {
            throw new IllegalArgumentException("a band needs hi > lo and positive steps");
        }
    }

    public double fraction(int value) {
        return Math.max(0.0, Math.min(1.0, (value - lo) / (double) (hi - lo)));
    }

    public int[] ticks(int step) {
        final int first = Math.floorDiv(lo + step - 1, step) * step;
        if (first > hi) {
            return new int[0];
        }
        final int[] out = new int[(hi - first) / step + 1];
        for (int i = 0; i < out.length; i++) {
            out[i] = first + i * step;
        }
        return out;
    }

    public String label(int value) {
        if (value % divisor == 0) {
            return Integer.toString(value / divisor);
        }
        return String.format(java.util.Locale.ROOT, "%.1f", value / (double) divisor);
    }
}
