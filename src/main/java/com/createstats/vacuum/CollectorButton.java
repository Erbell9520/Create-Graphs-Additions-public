package com.createstats.vacuum;

public final class CollectorButton {
    private CollectorButton() {
    }

    public static final double X0 = 3.5;
    public static final double X1 = 6.5;
    public static final double Y0 = 0.5;
    public static final double Y1 = 3.5;

    public static final double Z0 = 0.2;

    public static final double Z1 = 1.0;

    public static final double LATCHED = 0.35;

    private static final double EPS = 0.05;

    public static boolean hit(double mx, double my, double mz) {
        return mx >= X0 - EPS && mx <= X1 + EPS && my >= Y0 - EPS && my <= Y1 + EPS
                && mz >= Z0 - EPS && mz <= Z1 + EPS;
    }

    public static double lensFront(boolean switchedOn) {
        return switchedOn ? LATCHED : Z0;
    }
}
