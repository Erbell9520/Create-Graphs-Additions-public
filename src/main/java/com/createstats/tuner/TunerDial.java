package com.createstats.tuner;

// a radio dial, sixteen text panels don't fit round a hub
public final class TunerDial {
    public static final double START = Math.toRadians(120.0);

    public static final double SWEEP = Math.toRadians(300.0);

    private static final double TAU = Math.PI * 2.0;

    public record Chip(int ring, double angle) {
    }

    private TunerDial() {
    }

    public static double angleOf(double fraction) {
        return START + Math.max(0.0, Math.min(1.0, fraction)) * SWEEP;
    }

    public static Chip[] place(double[] fractions, double gapOuter, double gapInner) {
        final int n = fractions.length;
        final Chip[] out = new Chip[n];
        if (n == 0) {
            return out;
        }
        final double[] want = new double[n];
        for (int i = 0; i < n; i++) {
            want[i] = angleOf(fractions[i]);
        }
        if ((n - 1) * gapOuter <= SWEEP) {
            final double[] at = spread(want, START, START + SWEEP, gapOuter);
            for (int i = 0; i < n; i++) {
                out[i] = new Chip(0, at[i]);
            }
            return out;
        }
        final int outerCount = (n + 1) / 2;
        final double[] outer = new double[outerCount];
        final double[] inner = new double[n / 2];
        for (int i = 0; i < n; i++) {
            if (i % 2 == 0) {
                outer[i / 2] = want[i];
            } else {
                inner[i / 2] = want[i];
            }
        }
        final double[] o = spread(outer, START, START + SWEEP, gapOuter);
        final double[] in = spread(inner, START, START + SWEEP, gapInner);
        for (int i = 0; i < n; i++) {
            out[i] = i % 2 == 0 ? new Chip(0, o[i / 2]) : new Chip(1, in[i / 2]);
        }
        return out;
    }

    public static double[] spread(double[] desired, double lo, double hi, double gap) {
        final int n = desired.length;
        final double[] y = new double[n];
        if (n == 0) {
            return y;
        }
        if (n == 1) {
            y[0] = Math.max(lo, Math.min(hi, desired[0]));
            return y;
        }
        final double g = (n - 1) * gap > hi - lo ? (hi - lo) / (n - 1) : gap;
        final double[] sum = new double[n];
        final int[] count = new int[n];
        int top = 0;
        for (int i = 0; i < n; i++) {
            sum[top] = desired[i] - i * g;
            count[top] = 1;
            top++;
            while (top > 1 && sum[top - 2] / count[top - 2] > sum[top - 1] / count[top - 1]) {
                sum[top - 2] += sum[top - 1];
                count[top - 2] += count[top - 1];
                top--;
            }
        }
        final double zHi = hi - (n - 1) * g;
        int i = 0;
        for (int b = 0; b < top; b++) {
            final double mean = Math.max(lo, Math.min(zHi, sum[b] / count[b]));
            for (int c = 0; c < count[b]; c++, i++) {
                y[i] = mean + i * g;
            }
        }
        return y;
    }

    public static int pick(Chip[] chips, int ring, double at, double tolerance) {
        int best = -1;
        double bestGap = Double.MAX_VALUE;
        for (int k = 0; k < chips.length; k++) {
            if (chips[k].ring() != ring) {
                continue;
            }
            double d = Math.abs(wrap(at - chips[k].angle()));
            d = Math.min(d, TAU - d);
            if (d < bestGap) {
                bestGap = d;
                best = k;
            }
        }
        return bestGap <= tolerance ? best : -1;
    }

    public static double wrapAngle(double a) {
        return wrap(a);
    }

    static double wrap(double a) {
        final double r = a % TAU;
        return r < 0 ? r + TAU : r;
    }
}
