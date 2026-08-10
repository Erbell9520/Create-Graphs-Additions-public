package com.createstats.gui.pdu;

import com.createstats.ModConfiguration;

/** [1] */
public final class Ease {

    private Ease() {
    }

    /**
     * Moves {@code current} toward {@code target}, frame rate independent through the frame delta.
     *
     * @param rate        how fast to converge, in per tick units. 0.18 is a slow glide, 0.35 a snappy one.
     * @param partialTick the frame time in ticks (Minecraft's realtime delta), so a long frame steps
     *                    proportionally further. Clamped, so a hitch cannot carry the value past its
     *                    target and back.
     */
    public static float ease(float current, float target, float rate, float partialTick) {
        if (reduceMotion()) {
            return target;
        }
        final float dt = partialTick <= 0.0f ? 1.0f : partialTick;
        float alpha = rate * dt;
        if (alpha > 1.0f) {
            // A stalled frame must not overshoot: the most a single step may do is arrive exactly.
            alpha = 1.0f;
        }
        final float next = current + (target - current) * alpha;
        // Snap inside a hair, so a bar settles cleanly instead of crawling its last thousandth forever.
        return Math.abs(target - next) < 0.001f ? target : next;
    }

    /** [2] */
    public static boolean reduceMotion() {
        try {
            return ModConfiguration.CLIENT.pduReduceMotion.get();
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
