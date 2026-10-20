package com.createstats.cinecam.core;

public final class CineRig {
    public static final double HT = 14.0;
    public static final double HEAD_BASE = 24.0;

    public static final double HINGE_RADIUS = 4.6;
    public static final double HINGE_Y = HT - 3.5;
    public static final double FOOT_RADIUS = 8.8;
    public static final double ANKLE_Y = 1.4;
    public static final double[] BEARINGS = {0.0, 120.0, 240.0};
    public static final double LEG_SPLAY = Math.toDegrees(Math.atan2(FOOT_RADIUS - HINGE_RADIUS, HINGE_Y - ANKLE_Y));
    public static final double LEG_LEN = Math.hypot(FOOT_RADIUS - HINGE_RADIUS, HINGE_Y - ANKLE_Y);
    public static final double BRACE_Y = HT - 8.6;
    public static final double BRACE_R = 1.4;
    public static final double BRACE_AT = 0.5;
    private static final double BRACE_END_R = HINGE_RADIUS + BRACE_AT * LEG_LEN * Math.sin(Math.toRadians(LEG_SPLAY)) - 1.0;
    private static final double BRACE_END_Y = HINGE_Y - BRACE_AT * LEG_LEN * Math.cos(Math.toRadians(LEG_SPLAY));
    public static final double BRACE_TILT = Math.toDegrees(Math.atan2(BRACE_END_Y - BRACE_Y, BRACE_END_R - BRACE_R));

    public static final double[] P_HEAD = {8.0, 2.0, 8.0};
    public static final double[] P_AZ_WHEEL = {2.4, 0.0, 8.0};
    public static final double[] P_EL_WHEEL = {1.0, 8.0, 8.0};
    public static final double[] P_BODY = {8.0, 11.5, 8.0};
    public static final double[] P_REEL_F = {6.15, 18.3, 4.4};
    public static final double[] P_REEL_R = {6.15, 18.3, 11.4};
    public static final double[] P_WHEEL = {6.8, 11.0, 14.5};
    public static final double[] P_DRIVER = {9.9, 11.0, 14.15};

    public static final double AZ_WHEEL_TURN = 90.0;

    public static final double TRUNNION_Y = HT + P_BODY[1];

    public static final double LENS_AHEAD = 12.5;

    public static final double GENEVA_A = 2.2;
    public static final double GENEVA_C = 3.1;

    private CineRig() {
    }

    public static double geneva(double driverDeg) {
        double turns = Math.floor(driverDeg / 360.0);
        double psi = driverDeg - 360.0 * turns;
        double off;
        if (psi < 135.0) {
            off = 0.0;
        } else if (psi > 225.0) {
            off = -90.0;
        } else {
            double phi = Math.toRadians(psi - 180.0);
            double beta = Math.toDegrees(Math.atan2(GENEVA_A * Math.sin(phi), GENEVA_C - GENEVA_A * Math.cos(phi)));

            off = Math.max(-90.0, Math.min(0.0, -45.0 - beta));
        }
        return -90.0 * turns + off;
    }
}
