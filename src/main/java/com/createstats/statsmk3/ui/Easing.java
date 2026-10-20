package com.createstats.statsmk3.ui;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class Easing {
    private Easing() {
    }

    public static float clamp01(float t) {
        return t < 0 ? 0 : t > 1 ? 1 : t;
    }

    public static float expoOut(float t) {
        final float k = clamp01(t);
        return k >= 1.0f ? 1.0f : 1.0f - (float) Math.pow(2.0, -10.0 * k);
    }

    public static float expoIn(float t) {
        final float k = clamp01(t);
        return k <= 0.0f ? 0.0f : (float) Math.pow(2.0, 10.0 * (k - 1.0));
    }

    public static float easeOut(float t) {
        final float k = clamp01(t);
        return 1.0f - (1.0f - k) * (1.0f - k);
    }

    public static float easeInOut(float t) {
        final float k = clamp01(t);
        return k < 0.5f ? 2.0f * k * k : 1.0f - (float) Math.pow(-2.0 * k + 2.0, 2.0) / 2.0f;
    }

    public static float linear(float t) {
        return clamp01(t);
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    // use this, not lerp(a, b, 0.1f). that runs at a different speed on every machine
    public static float approach(float current, float target, float halfLife, float dt) {
        if (halfLife <= 0.0f) {
            return target;
        }
        final float k = (float) Math.pow(0.5, dt / halfLife);
        return target + (current - target) * k;
    }
}
