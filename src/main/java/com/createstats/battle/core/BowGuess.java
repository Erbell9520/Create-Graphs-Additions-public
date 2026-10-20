package com.createstats.battle.core;

import java.util.List;

public final class BowGuess {
    public static final double SHARE = 0.70;

    public static final int MIN_PROPELLERS = 2;

    public static final double HORIZONTAL_FRACTION = 0.5;

    public record Thrust(double x, double y, double z, double weight) {
    }

    private BowGuess() {
    }

    public static int guess(List<Thrust> thrusts) {
        double[] sum = new double[4];
        int[] count = new int[4];
        double total = 0.0;
        for (Thrust t : thrusts) {
            double len = Math.sqrt(t.x() * t.x() + t.y() * t.y() + t.z() * t.z());
            double w = Math.max(0.0, t.weight());
            if (len < 1.0e-9 || w <= 0.0) continue;
            double hx = t.x() / len;
            double hz = t.z() / len;
            double h = Math.sqrt(hx * hx + hz * hz);
            if (h < 1.0e-9) continue;
            int q = ShipFrame.quarterOf(hx, hz);
            sum[q] += h * w;
            total += h * w;
            if (h >= HORIZONTAL_FRACTION) count[q]++;
        }
        if (total <= 0.0) return -1;
        int best = 0;
        for (int q = 1; q < 4; q++) {
            if (sum[q] > sum[best]) best = q;
        }
        return sum[best] >= SHARE * total && count[best] >= MIN_PROPELLERS ? best : -1;
    }
}
