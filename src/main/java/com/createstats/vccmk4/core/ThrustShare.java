package com.createstats.vccmk4.core;

public final class ThrustShare {
    private ThrustShare() {
    }

    public static double[] share(double wanted, double[] ceiling, double[] clear, double[] along, boolean[] twoWay) {
        int n = ceiling.length;
        double[] out = new double[n];
        if (!Double.isFinite(wanted) || wanted == 0.0) return out;
        double sign = Math.signum(wanted);
        double sum = 0.0;
        double[] capacity = new double[n];
        for (int i = 0; i < n; i++) {
            double c = capacityOf(i, sign, ceiling, clear, along, twoWay);
            capacity[i] = c;
            sum += c;
        }
        if (sum <= 0.0) return out;
        double fraction = Math.min(1.0, Math.abs(wanted) / sum);
        for (int i = 0; i < n; i++) {
            if (capacity[i] <= 0.0) continue;

            double way = along[i] * sign > 0.0 ? 1.0 : -1.0;
            out[i] = clamp(clear[i], 0.0, 1.0) * fraction * way;
        }
        return out;
    }

    public static double capacity(double wanted, double[] ceiling, double[] clear, double[] along, boolean[] twoWay) {
        double sign = wanted == 0.0 ? 1.0 : Math.signum(wanted);
        double sum = 0.0;
        for (int i = 0; i < ceiling.length; i++) sum += capacityOf(i, sign, ceiling, clear, along, twoWay);
        return sum;
    }

    public static double delivered(double[] command, double[] ceiling, double[] along) {
        double sum = 0.0;
        for (int i = 0; i < command.length; i++) sum += command[i] * ceiling[i] * along[i];
        return sum;
    }

    private static double capacityOf(int i, double sign, double[] ceiling, double[] clear, double[] along, boolean[] twoWay) {
        double c = ceiling[i];
        double a = along[i];
        if (!(c > 0.0) || !Double.isFinite(c) || !Double.isFinite(a) || a == 0.0) return 0.0;
        boolean can = twoWay != null && i < twoWay.length && twoWay[i] || a * sign > 0.0;
        if (!can) return 0.0;
        return c * clamp(clear[i], 0.0, 1.0) * Math.abs(a);
    }

    private static double clamp(double v, double lo, double hi) {
        return Double.isNaN(v) ? lo : (v < lo ? lo : (v > hi ? hi : v));
    }
}
