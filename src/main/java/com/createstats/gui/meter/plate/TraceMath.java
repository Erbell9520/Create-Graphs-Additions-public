package com.createstats.gui.meter.plate;

public final class TraceMath {
    public static final int EMPTY = Integer.MIN_VALUE;

    private TraceMath() {
    }

    public static int[] columns(int[] chrono, int size, int cols) {
        final int n = chrono.length;
        final int[] out = new int[cols];
        for (int c = 0; c < cols; c++) {
            final int a = (int) ((long) c * size / cols) - (size - n);
            final int b = (int) ((long) (c + 1) * size / cols) - (size - n);
            if (b <= 0 || n == 0) {
                out[c] = EMPTY;
                continue;
            }
            int v = 0;
            final int end = Math.max(a + 1, b);
            for (int i = Math.max(0, a); i < end; i++) {
                v = Math.max(v, chrono[Math.min(n - 1, i)]);
            }
            out[c] = v;
        }
        return out;
    }

    public static int niceCeil(double v) {
        final double target = Math.max(10.0, v);
        final double p = Math.pow(10, Math.floor(Math.log10(target)));
        for (final double k : new double[]{1, 1.2, 1.5, 2, 2.5, 3, 4, 5, 6, 8, 10}) {
            if (k * p >= target - 1e-9) {
                return (int) Math.round(k * p);
            }
        }
        return (int) Math.round(10 * p);
    }

    public static int axisFor(int[] columns, int lifetimePeak) {
        int max = 0;
        for (final int v : columns) {
            if (v != EMPTY) {
                max = Math.max(max, v);
            }
        }
        return niceCeil(max > 0 ? max : lifetimePeak);
    }

    public static int rowOf(int value, int axis, int bottom, int plotH) {
        final int clamped = Math.max(0, Math.min(value, axis));
        return bottom - Math.round((float) clamped / axis * plotH);
    }

    public static int[][] envelope(int[] rows, int radius) {
        final int n = rows.length;
        final int[][] out = new int[n][2];
        for (int c = 0; c < n; c++) {
            if (rows[c] == EMPTY) {
                out[c][0] = EMPTY;
                out[c][1] = EMPTY;
                continue;
            }
            int top = rows[c];
            int low = rows[c];
            for (int k = Math.max(0, c - radius); k <= Math.min(n - 1, c + radius); k++) {
                if (rows[k] != EMPTY) {
                    top = Math.min(top, rows[k]);
                    low = Math.max(low, rows[k]);
                }
            }
            out[c][0] = top;
            out[c][1] = low;
        }
        return out;
    }

    public static int ageTicks(int c, int cols, int size, int cadence) {
        return Math.round((float) (cols - 1 - c) * size / cols * cadence);
    }
}
