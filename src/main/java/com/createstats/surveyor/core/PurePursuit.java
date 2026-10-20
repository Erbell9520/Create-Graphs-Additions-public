package com.createstats.surveyor.core;

public final class PurePursuit {
    private PurePursuit() {
    }

    public static double curvature(double px, double pz, double hx, double hz, double tx, double tz) {
        double dx = tx - px;
        double dz = tz - pz;
        double l2 = dx * dx + dz * dz;
        if (l2 < 1.0e-9) return 0.0;

        double lat = dx * -hz + dz * hx;
        return 2.0 * lat / l2;
    }
}
