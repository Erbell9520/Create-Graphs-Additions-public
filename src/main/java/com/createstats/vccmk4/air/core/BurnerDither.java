package com.createstats.vccmk4.air.core;

// sigma delta per heater, whole steps bob the ship
public final class BurnerDither {
    static final double RELIGHT_SHARE = 0.9;

    static final double ALL_OUT_SHARE = 0.5;

    private double[] acc = new double[0];
    private int lit = -1;

    public int lit() {
        return Math.max(0, lit);
    }

    public void reset() {
        acc = new double[0];
        lit = -1;
    }

    public int[] signals(double wanted, double[] dials) {
        int n = dials.length;
        int[] out = new int[n];
        if (n == 0) return out;
        if (acc.length != n) {
            acc = new double[n];
            for (int i = 0; i < n; i++) acc[i] = (i + 0.5) / n;
            lit = n;
        }
        if (lit < 0 || lit > n) lit = n;
        double want = Double.isFinite(wanted) ? Math.max(0.0, wanted) : 0.0;

        while (lit > 1 && minOutput(dials, lit) > want) lit--;
        if (lit == 1 && minOutput(dials, 1) * ALL_OUT_SHARE > want) lit = 0;
        if (lit == 0 && minOutput(dials, 1) * RELIGHT_SHARE <= want) lit = 1;
        while (lit >= 1 && lit < n && (maxOutput(dials, lit) < want || minOutput(dials, lit + 1) <= want * RELIGHT_SHARE)) {
            lit++;
        }

        double full = maxOutput(dials, lit);
        double share = full > 1.0e-9 ? Math.min(1.0, want / full) : 0.0;
        double level = Math.max(1.0, Math.min(GasLaw.STEPS, share * GasLaw.STEPS));
        for (int i = 0; i < n; i++) {
            if (i >= lit || !(dials[i] > 0.0)) {
                out[i] = 0;
                acc[i] = (i + 0.5) / n;
                continue;
            }
            acc[i] += level;
            int s = (int) Math.floor(acc[i]);
            acc[i] -= s;
            out[i] = Math.max(1, Math.min(GasLaw.STEPS, s));
        }
        return out;
    }

    static double minOutput(double[] dials, int k) {
        double s = 0.0;
        for (int i = 0; i < k && i < dials.length; i++) s += Math.max(0.0, dials[i]) / GasLaw.STEPS;
        return s;
    }

    static double maxOutput(double[] dials, int k) {
        double s = 0.0;
        for (int i = 0; i < k && i < dials.length; i++) s += Math.max(0.0, dials[i]);
        return s;
    }

    public static double output(int[] signals, double[] dials) {
        double s = 0.0;
        for (int i = 0; i < signals.length && i < dials.length; i++) s += dials[i] * signals[i] / GasLaw.STEPS;
        return s;
    }
}
