package com.createstats.flight.runtime;

// kappa = 2 sin(alpha) / Ld
public final class PurePursuit {
    private PurePursuit() {}

    public static double steer(double headingErrorDeg, double lookahead, double minTurnRadius, double deadzoneDeg) {
        double magnitude = Math.abs(headingErrorDeg);
        if (magnitude <= deadzoneDeg) {
            return 0.0;
        }
        if (magnitude >= 90.0) {
            return Math.copySign(1.0, headingErrorDeg);
        }
        double radius = Math.max(2.5, minTurnRadius);
        double ld = Math.max(2.5, lookahead);
        double curvature = 2.0 * Math.sin(Math.toRadians(headingErrorDeg)) / ld;
        double steering = curvature * radius;
        return Math.max(-1.0, Math.min(1.0, steering));
    }
}
