package com.createstats.vccmk4.core;

import com.createstats.vehiclelight.VehicleLightKind;

// lamps get a job the moment they're placed, like wheels
public final class LampProposal {
    public static final double SIDE = 0.3;

    public static final double POINTING = 0.5;

    private LampProposal() {
    }

    public static LinkSource propose(VehicleLightKind kind, double forward, double facingSide, double side) {
        if (kind == null) return LinkSource.OFF;
        return switch (kind) {
            case WHITE -> forward >= POINTING ? LinkSource.HEADLIGHT : (forward <= -POINTING ? LinkSource.REVERSE : LinkSource.OFF);
            case RED -> LinkSource.TAIL_BRAKE;
            case AMBER -> {
                if (side <= -SIDE) yield LinkSource.LEFT;
                if (side >= SIDE) yield LinkSource.RIGHT;
                if (facingSide <= -POINTING) yield LinkSource.LEFT;
                if (facingSide >= POINTING) yield LinkSource.RIGHT;
                yield LinkSource.OFF;
            }
        };
    }

    public static double centreLine(double[] wheelLaterals, double[] lampLaterals) {
        if (wheelLaterals != null && wheelLaterals.length > 0) return midRange(wheelLaterals);
        if (lampLaterals != null && lampLaterals.length > 0) return midRange(lampLaterals);
        return 0.0;
    }

    private static double midRange(double[] values) {
        double lo = Double.POSITIVE_INFINITY;
        double hi = Double.NEGATIVE_INFINITY;
        for (double v : values) {
            if (!Double.isFinite(v)) continue;
            lo = Math.min(lo, v);
            hi = Math.max(hi, v);
        }
        return lo <= hi ? (lo + hi) / 2.0 : 0.0;
    }
}
