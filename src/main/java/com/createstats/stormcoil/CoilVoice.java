package com.createstats.stormcoil;

public final class CoilVoice {
    public static final float HUM_VOLUME = 0.77f;

    public static final float RUN_VOLUME = 0.60f;

    public static final int HUM_FADE = 20;

    public static final int RUN_IN = 5;

    public static final int RUN_OUT = 40;

    public static final int RUN_HOLD = 30;

    private boolean alive;
    private boolean running;

    public void tick(long now, int charge, boolean drawing, boolean aiming, long lastZap, boolean standing) {
        alive = charge > 0 || drawing;
        final boolean fighting = !standing && (aiming
                || (lastZap != Long.MIN_VALUE && lastZap <= now && now - lastZap < RUN_HOLD));
        running = drawing || fighting;
    }

    public float hum() {
        return alive ? HUM_VOLUME : 0f;
    }

    public float run() {
        return running ? RUN_VOLUME : 0f;
    }

    public static float ease(float current, float want, float full, int in, int out) {
        if (want > current) {
            return Math.min(want, current + full / Math.max(1, in));
        }
        return Math.max(want, current - full / Math.max(1, out));
    }
}
