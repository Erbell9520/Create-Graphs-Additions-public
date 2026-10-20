package com.createstats.aerial;

public final class FlightCharts {
    public static final double RESERVE = 0.9;

    private FlightCharts() {
    }

    public static double usableAt(double y, double balloonCeiling, double lapse) {
        if (!Double.isFinite(y) || !Double.isFinite(balloonCeiling) || !(lapse > 0.0)) {
            return Double.NaN;
        }
        return Math.exp(-lapse * (y - balloonCeiling));
    }

    public static double mustHold(AirframeReadout r) {
        if (!Double.isFinite(r.carried) || !(r.carried > 0.0)) {
            return Double.NaN;
        }
        final double hidden = r.snapshot.parts().learned().hidden();
        final double lift = Double.isFinite(hidden) && r.gravity > 0.0 ? Math.max(0.0, hidden) / r.gravity : 0.0;
        return Math.max(0.0, r.carried - lift);
    }

    public static double holdAt(double y, double balloonCeiling, double lapse, double mustHold) {
        final double u = usableAt(y, balloonCeiling, lapse);
        return Double.isFinite(u) && Double.isFinite(mustHold) ? mustHold * u : Double.NaN;
    }

    public static double spareAt(double y, double balloonCeiling, double lapse, double mustHold) {
        final double hold = holdAt(y, balloonCeiling, lapse, mustHold);
        return Double.isFinite(hold) ? hold - mustHold : Double.NaN;
    }

    public enum DragSource {
        MEASURED,

        LEARNED,

        NONE
    }

    public record Drag(double slope, DragSource source, double top) {
        public static final Drag NONE = new Drag(Double.NaN, DragSource.NONE, Double.NaN);
    }

    public static double pushAt(AirframeReadout r, double u) {
        final double p = Double.isFinite(r.pressure) ? r.pressure : 1.0;
        return AirframeMath.forwardThrustAt(r.snapshot, r.snapshot.body().nose(), Math.max(0.0, u), p);
    }

    public static Drag drag(AirframeReadout r) {
        if (Double.isFinite(r.topSpeed) && r.topSpeed > 0.1) {
            final double push = pushAt(r, r.topSpeed);
            if (push > 0.0) {
                return new Drag(push / r.topSpeed, DragSource.MEASURED, r.topSpeed);
            }
        }
        final AerialSnapshot.Helm h = r.snapshot.helm();
        if (h.known() && Double.isFinite(h.fullAhead()) && h.fullAhead() > 0.1) {
            final double push = pushAt(r, h.fullAhead());
            if (push > 0.0) {
                return new Drag(push / h.fullAhead(), DragSource.LEARNED, h.fullAhead());
            }
        }
        return Drag.NONE;
    }

    public static boolean propsLimit(double top, double propsOut) {
        return Double.isFinite(top) && Double.isFinite(propsOut) && top >= propsOut * 0.9;
    }
}
