package com.createstats.surveyor.core;

public final class LateralControl {
    public static final double T_REACH = 1.1;

    public static final double L_MIN = 3.0;
    public static final double ZETA = 0.85;

    public static final double LAG = 0.15;

    private LateralControl() {
    }

    public static double curvature(double lineKappa, double lateral, double headingErr, double v) {
        double reach = Math.max(L_MIN, T_REACH * Math.max(0.0, v));
        return lineKappa - lateral / (reach * reach) - 2.0 * ZETA * headingErr / reach;
    }
}
