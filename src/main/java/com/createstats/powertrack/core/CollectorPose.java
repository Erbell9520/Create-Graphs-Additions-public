package com.createstats.powertrack.core;

public final class CollectorPose {
    public static final double PIVOT_X = 8.0;
    public static final double PIVOT_Y = 3.0;
    public static final double PIVOT_Z = 3.0;
    public static final double ARM = 15.5;

    public static final double PIN_TO_SOLE = 1.8;

    public static final double STOP_DEG = 22.7;

    public static final double MAX_DEG = 80.0;

    public static final double CONTACT_HALF_WIDTH_PX = 3.5 + RailProfile.CAP_W / 2.0;

    private CollectorPose() {
    }

    public static double angleFor(double soleY) {
        final double pinY = soleY + PIN_TO_SOLE;
        final double c = (PIVOT_Y - pinY) / ARM;
        if (c >= Math.cos(Math.toRadians(STOP_DEG))) {
            return STOP_DEG;
        }
        return Math.min(MAX_DEG, Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, c)))));
    }

    public static double angleOnRail(double railTopPx) {
        return angleFor(railTopPx - 16.0);
    }

    public static double soleAt(double angleDeg) {
        return PIVOT_Y - ARM * Math.cos(Math.toRadians(angleDeg)) - PIN_TO_SOLE;
    }

    public static double shoeAlong(double angleDeg) {
        return PIVOT_Z + ARM * Math.sin(Math.toRadians(angleDeg));
    }

    public static double shoeOffsetBlocks(double angleDeg) {
        return (shoeAlong(angleDeg) - 8.0) / 16.0;
    }

    public static double lowestTouchPx() {
        return soleAt(STOP_DEG) + 16.0;
    }

    public static final double TOUCH_PX = 0.05;

    @FunctionalInterface
    public interface RailUnder {
        double topPx(double offsetBlocks);
    }

    public record Rest(double angleDeg, boolean touching) {
    }

    // solve the angle, the shoe slides 4.3px along the beam as the arm rises
    public static Rest rest(RailUnder under) {
        final double stopSole = soleAt(STOP_DEG);
        final double atStop = under.topPx(shoeOffsetBlocks(STOP_DEG));
        if (Double.isNaN(atStop) || atStop <= stopSole) {
            return new Rest(STOP_DEG, !Double.isNaN(atStop) && atStop >= stopSole - TOUCH_PX);
        }
        double lo = STOP_DEG;
        double hi = MAX_DEG;
        double angle = angleFor(atStop);
        for (int i = 0; i < 8; i++) {
            final double top = under.topPx(shoeOffsetBlocks(angle));
            final double clear = Double.isNaN(top) ? Double.POSITIVE_INFINITY : soleAt(angle) - top;
            if (Math.abs(clear) <= 1e-3) {
                return new Rest(angle, true);
            }
            if (clear < 0) {
                lo = Math.max(lo, angle);
            } else {
                hi = Math.min(hi, angle);
            }
            final double next = Double.isNaN(top) ? (lo + hi) / 2.0 : angleFor(top);
            angle = next > lo && next < hi ? next : (lo + hi) / 2.0;
        }
        for (int i = 0; i < 40 && hi - lo > 1e-5; i++) {
            final double mid = (lo + hi) / 2.0;
            final double top = under.topPx(shoeOffsetBlocks(mid));
            if (Double.isNaN(top) || soleAt(mid) >= top) {
                hi = mid;
            } else {
                lo = mid;
            }
        }
        return new Rest(hi, true);
    }
}
