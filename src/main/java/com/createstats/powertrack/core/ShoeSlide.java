package com.createstats.powertrack.core;

public final class ShoeSlide {
    public static final double MAX = 1.5;

    public static final double CENTRE_REACH = 0.75;

    public static final double MIN_ABOVE = 1.0;
    public static final double MAX_ABOVE = 2.0;

    @FunctionalInterface
    public interface Locator {
        RailLayout.Hit nearest(double x, double y, double z, double maxLateral, double minAbove, double maxAbove);
    }

    public record Result(double slide, RailLayout.Hit hit) {
        public boolean found() {
            return hit != null;
        }
    }

    private ShoeSlide() {
    }

    public static Result onCar(Locator rail, double[] c, double[] trail, double[] side, double centreOffset,
                               double reachAlong) {
        final double ax = c[0] + trail[0] * reachAlong;
        final double az = c[2] + trail[1] * reachAlong;

        final double qx = ax - side[0] * centreOffset;
        final double qz = az - side[1] * centreOffset;
        final RailLayout.Hit hit = rail.nearest(qx, c[1], qz, CENTRE_REACH, MIN_ABOVE, MAX_ABOVE);
        if (hit == null) {
            return new Result(clamp(-centreOffset), null);
        }
        return new Result(clamp((hit.x() - ax) * side[0] + (hit.z() - az) * side[1]), hit);
    }

    public static Result free(Locator rail, double[] c, double[] trail, double[] side, double reachAlong) {
        final double ax = c[0] + trail[0] * reachAlong;
        final double az = c[2] + trail[1] * reachAlong;
        final RailLayout.Hit hit = rail.nearest(ax, c[1], az, MAX, MIN_ABOVE, MAX_ABOVE);
        if (hit == null) {
            return new Result(0.0, null);
        }
        return new Result(clamp((hit.x() - ax) * side[0] + (hit.z() - az) * side[1]), hit);
    }

    public static Boolean alongX(Locator rail, double[] c) {
        final RailLayout.Hit hit = rail.nearest(c[0], c[1], c[2], MAX, MIN_ABOVE, MAX_ABOVE);
        return hit == null ? null : Math.abs(hit.tx()) >= Math.abs(hit.tz());
    }

    private static double clamp(double v) {
        return Math.max(-MAX, Math.min(MAX, v));
    }
}
