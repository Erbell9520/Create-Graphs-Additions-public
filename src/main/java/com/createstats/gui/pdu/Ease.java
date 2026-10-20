package com.createstats.gui.pdu;

import com.createstats.ModConfiguration;

public final class Ease {
    private Ease() {
    }

    private static boolean still;

    public static void still(Runnable draw) {
        final boolean was = still;
        still = true;
        try {
            draw.run();
        } finally {
            still = was;
        }
    }

    public static float ease(float current, float target, float rate, float partialTick) {
        if (still || reduceMotion()) {
            return target;
        }
        final float dt = partialTick <= 0.0f ? 1.0f : partialTick;
        float alpha = rate * dt;
        if (alpha > 1.0f) {
            alpha = 1.0f;
        }
        final float next = current + (target - current) * alpha;

        return Math.abs(target - next) < 0.001f ? target : next;
    }

    public static boolean reduceMotion() {
        if (still) {
            return true;
        }
        try {
            return ModConfiguration.CLIENT.pduReduceMotion.get();
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
