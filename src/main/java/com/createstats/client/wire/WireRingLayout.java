package com.createstats.client.wire;

import java.util.Arrays;
import java.util.Comparator;

public final class WireRingLayout {
    private static final double TAU = Math.PI * 2.0;

    private WireRingLayout() {
    }

    // gap capped under an even share, 6 panels can't all keep more than a sixth. cut at the widest empty sector
    public static double[] spread(double[] angles, double minGap) {
        final int n = angles.length;
        final double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            out[i] = wrap(angles[i]);
        }
        if (n < 2) {
            return out;
        }
        final double gap = Math.min(minGap, TAU / n * 0.98);
        final Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        Arrays.sort(order, Comparator.<Integer>comparingDouble(i -> out[i]).thenComparingInt(i -> i));

        int start = 0;
        double widest = -1.0;
        for (int k = 0; k < n; k++) {
            final double sector = k + 1 < n
                    ? out[order[k + 1]] - out[order[k]]
                    : out[order[0]] + TAU - out[order[n - 1]];
            if (sector > widest) {
                widest = sector;
                start = (k + 1) % n;
            }
        }
        final double base = out[order[start]];
        final double[] x = new double[n];
        for (int m = 0; m < n; m++) {
            final double v = out[order[(start + m) % n]];
            x[m] = v < base ? v + TAU : v;
        }

        final double[] sum = new double[n];
        final int[] count = new int[n];
        int top = 0;
        for (int m = 0; m < n; m++) {
            sum[top] = x[m] - m * gap;
            count[top] = 1;
            top++;
            while (top > 1 && sum[top - 2] / count[top - 2] > sum[top - 1] / count[top - 1]) {
                sum[top - 2] += sum[top - 1];
                count[top - 2] += count[top - 1];
                top--;
            }
        }
        final double[] y = new double[n];
        int m = 0;
        for (int b = 0; b < top; b++) {
            final double mean = sum[b] / count[b];
            for (int c = 0; c < count[b]; c++, m++) {
                y[m] = mean + m * gap;
            }
        }

        if (y[n - 1] - y[0] > TAU - gap + 1.0e-12) {
            final double step = TAU / n;
            double offset = 0.0;
            for (int k = 0; k < n; k++) {
                offset += x[k] - k * step;
            }
            offset /= n;
            for (int k = 0; k < n; k++) {
                y[k] = offset + k * step;
            }
        }
        for (int k = 0; k < n; k++) {
            out[order[(start + k) % n]] = wrap(y[k]);
        }
        return out;
    }

    public static double between(double a, double b) {
        final double d = wrap(a - b);
        return d > Math.PI ? TAU - d : d;
    }

    public static int nextPick(int selected, int wires, int direction) {
        final int n = Math.max(0, wires) + 1;
        final int from = selected < -1 || selected >= wires ? 0 : selected + 1;
        return Math.floorMod(from - Integer.signum(direction), n) - 1;
    }

    static double wrap(double a) {
        final double r = a % TAU;
        return r < 0 ? r + TAU : r;
    }
}
