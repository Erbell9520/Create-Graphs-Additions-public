package com.createstats.powertrack.core;

public final class RailProfile {
    public static final double SLEEPER_TOP = 2.08;

    public static final double RUNNING_RAIL_TOP = 5.48;

    public static final double CHAIR_W = 3.2;
    public static final double CHAIR_TOP = 3.3;
    public static final double SADDLE_W = 3.6;
    public static final double SADDLE_L = 3.6;
    public static final double SADDLE_TOP = 3.7;
    public static final double BODY_W = 2.8;
    public static final double BODY_TOP = 5.0;
    public static final double CAP_W = 2.0;
    public static final double CAP_TOP = 5.6;

    public static final double RAIL_DEPTH = CAP_TOP - SADDLE_TOP;

    public static final double RAMP_LEN = SectionGraph.RAMP * 16.0;

    public static final double RAMP_DROP = 3.0;

    public static final double SLICE = 4.0;

    public static final double CHAIR_SPACING = 16.0;

    public static final double CHAIR_PHASE = 4.0;

    private RailProfile() {
    }

    public static double topPx(SectionGraph.Piece piece, double at) {
        double top = CAP_TOP;
        final double sPx = at * 16.0;
        final double fromPx = piece.railFrom() * 16.0;
        final double toPx = piece.railTo() * 16.0;
        if (piece.fromEnd().ramped() && sPx < fromPx + RAMP_LEN) {
            top -= RAMP_DROP * (1.0 - clamp01((sPx - fromPx) / RAMP_LEN));
        }
        if (piece.toEnd().ramped() && sPx > toPx - RAMP_LEN) {
            top -= RAMP_DROP * (1.0 - clamp01((toPx - sPx) / RAMP_LEN));
        }
        return top;
    }

    public static double bottomPx(double topPx) {
        return Math.max(SLEEPER_TOP + 0.12, topPx - RAIL_DEPTH);
    }

    public static double bodyTopPx(double topPx) {
        return Math.max(bottomPx(topPx), topPx - (CAP_TOP - BODY_TOP));
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}
