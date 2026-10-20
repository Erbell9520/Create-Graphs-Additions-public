package com.createstats.client.ponder.powertrack;

public final class FeederLook {
    public static final float PULL = 0.32f;
    public static final float DAMPING = 0.62f;

    public static final float STOP = 1.06f;

    public static final float BOUNCE = -0.4f;

    public float needle;
    public float needleOld;
    private float velocity;

    public void tick(float target) {
        needleOld = needle;
        final float t = Math.max(-1f, Math.min(1f, target));
        velocity += (t - needle) * PULL;
        velocity *= DAMPING;
        needle += velocity;
        if (Math.abs(needle) > STOP) {
            needle = Math.copySign(STOP, needle);
            if (velocity * needle > 0) {
                velocity *= BOUNCE;
            }
        }
    }

    public float needle(float partialTicks) {
        return needleOld + (needle - needleOld) * partialTicks;
    }
}
