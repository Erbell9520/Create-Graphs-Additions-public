package com.createstats.powertrack.core;

// warm up and cool down. a popping lamp got called "lame and basic", fair
public final class LampGlow {
    public static final float RISE = 0.34f;

    public static final float FALL = 0.25f;

    public static final float DARK = 0.04f;

    private float glow;
    private float glowPrev;

    private int colour;

    public void tick(int want) {
        glowPrev = glow;
        if (want != 0 && (want == colour || colour == 0 || glow < DARK)) {
            colour = want;
            glow = Math.min(1f, glow + RISE);
        } else {
            glow = Math.max(0f, glow - FALL);
            if (glow < DARK && want != colour) {
                colour = want;
            }
        }
    }

    public float glow(float partialTicks) {
        return glowPrev + (glow - glowPrev) * partialTicks;
    }

    public int colour() {
        return colour;
    }
}
