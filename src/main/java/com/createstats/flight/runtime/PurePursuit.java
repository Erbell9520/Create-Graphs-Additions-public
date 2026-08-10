package com.createstats.flight.runtime;

/** [1] */
public final class PurePursuit {

    private PurePursuit() {}

    /**
     * @param headingErrorDeg signed angle from the vehicle heading to the lookahead point (degrees)
     * @param lookahead       distance to the lookahead point (blocks)
     * @param minTurnRadius   tightest turn radius the vehicle can hold (blocks)
     * @param deadzoneDeg     heading error below which no steering is commanded
     * @return normalized steering command in [-1, 1], same sign convention as the heading error
     */
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
        double steering = curvature * radius; // curvature / (1 / radius)
        return Math.max(-1.0, Math.min(1.0, steering));
    }
}
