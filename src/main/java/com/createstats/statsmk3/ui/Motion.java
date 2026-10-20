package com.createstats.statsmk3.ui;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class Motion {
    private Motion() {
    }

    public static final float UI_STIFFNESS = 210.0f;
    public static final float UI_DAMPING = 26.0f;

    public static final float NEEDLE_STIFFNESS = 150.0f;
    public static final float NEEDLE_DAMPING = 24.5f;

    public static final float VIEW_TRANSITION = 0.280f;

    public static final float CHILD_STAGGER = 0.026f;

    public static final float HOVER = 0.120f;

    public static final float RAIL_INDICATOR = 0.380f;

    public static final float SERIES_REVEAL = 0.520f;

    public static final float TELEGRAPH_RING = 1.6f;

    public static final float VIEW_RISE = 9.0f;

    private static boolean reduced;

    public static boolean reducedMotion() {
        return reduced;
    }

    public static void setReducedMotion(boolean value) {
        reduced = value;
    }

    public static final String FROZEN_CLOCK = "create_stats.frozenAnimationClock";

    private static final Long FROZEN_MILLIS = Long.getLong(FROZEN_CLOCK);

    public static long wallMillis() {
        return FROZEN_MILLIS != null ? FROZEN_MILLIS : System.currentTimeMillis();
    }

    public static float stagger(float elapsed, int index) {
        if (reduced) {
            return 1.0f;
        }
        final float delay = index * CHILD_STAGGER + 0.040f;
        return Easing.expoOut(Easing.clamp01((elapsed - delay) / VIEW_TRANSITION));
    }
}
