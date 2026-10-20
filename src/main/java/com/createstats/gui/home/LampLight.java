package com.createstats.gui.home;

import com.createstats.power.HomeSegment;

// (mode + 1) / modes. mode / modes drew a full lamp at 90%
public final class LampLight {
    private LampLight() {
    }

    public static boolean pools(HomeSegment.State state) {
        return state == HomeSegment.State.ON;
    }

    public static float level(int mode, int modes) {
        if (modes <= 1) {
            return 1f;
        }
        return Math.max(0f, Math.min(1f, (mode + 1f) / modes));
    }
}
