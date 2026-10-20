package com.createstats.cinecam.core;

// thin lens, circle of confusion
public final class FocusPull {
    public static final double K = 0.06;

    public static final double MAX_SHARE = 0.012;

    public static final double OFF = 0.0;

    public static final double[] LADDER = {OFF, 22.0, 16.0, 11.0, 8.0, 5.6, 4.0, 2.8, 2.0, 1.4, 1.0};

    public static final double DEFAULT_STOP = 2.8;

    public static final double TAU_FOCUS = 0.25;

    public static final double TAU_ZOOM = 0.12;

    public static final double NEAREST = 0.05;

    private FocusPull() {
    }

    public static double circleScale(double tStop, double m11, double heightPx) {
        double t = clampStop(tStop);
        return t == OFF ? 0.0 : K / t * m11 * m11 * heightPx;
    }

    public static boolean on(double tStop) {
        return clampStop(tStop) != OFF;
    }

    public static double maxCircle(double heightPx) {
        return MAX_SHARE * heightPx;
    }

    public static double circle(double focusDiopters, double z, double tStop, double m11, double heightPx) {
        double c = (focusDiopters - 1.0 / Math.max(z, NEAREST)) * circleScale(tStop, m11, heightPx);
        double max = maxCircle(heightPx);
        return Math.max(-max, Math.min(max, c));
    }

    public static double viewDistance(double depth01, double m22, double m32) {
        return m32 / ((depth01 * 2.0 - 1.0) + m22);
    }

    public static double ease(double dt, double tau) {
        if (dt <= 0.0) return 0.0;
        return tau <= 0.0 ? 1.0 : 1.0 - Math.exp(-dt / tau);
    }

    public static double clampStop(double t) {
        if (!(t > 0.0)) return OFF;
        double best = LADDER[1];
        for (int i = 1; i < LADDER.length; i++) {
            if (Math.abs(Math.log(LADDER[i] / t)) < Math.abs(Math.log(best / t))) best = LADDER[i];
        }
        return best;
    }

    public static int rung(double tStop) {
        double at = clampStop(tStop);
        int i = 0;
        while (LADDER[i] != at) i++;
        return i;
    }

    public static double stepStop(double tStop, int clicks) {
        return LADDER[Math.max(0, Math.min(LADDER.length - 1, rung(tStop) + clicks))];
    }

    public static double zoomFov(double fovDeg, double zoom) {
        return Math.toDegrees(2.0 * Math.atan(Math.tan(Math.toRadians(fovDeg) / 2.0) / Math.max(1e-3, zoom)));
    }

    public static double easeZoom(double zoom, double want, double dt, double tau) {
        double a = Math.log(Math.max(1e-3, zoom));
        double b = Math.log(Math.max(1e-3, want));
        return Math.exp(a + (b - a) * ease(dt, tau));
    }
}
