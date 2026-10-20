package com.createstats.efficiency;

public final class EfficiencyKernels {
    public static final float LOAD_DEAD = 0.05f;

    public static final float LOAD_BAND_LO = 0.55f;

    public static final float LOAD_BAND_HI = 0.90f;

    public static final float LOAD_FALL = 0.12f;

    public static final float RESERVE_SCALE = 0.10f;

    public static final float RHYTHM_SIGMA = 0.020f;

    public static final float SMOOTHNESS_SIGMA = 0.060f;

    public static final float RESILIENCE_TAU = 30.0f;

    private EfficiencyKernels() {
    }

    public static float load(float u) {
        if (u <= LOAD_DEAD) {
            return 0.0f;
        }
        if (u < LOAD_BAND_LO) {
            return smootherstep((u - LOAD_DEAD) / (LOAD_BAND_LO - LOAD_DEAD));
        }
        if (u <= LOAD_BAND_HI) {
            return 1.0f;
        }
        final float d = (u - LOAD_BAND_HI) / LOAD_FALL;
        return (float) Math.exp(-d * d);
    }

    public static float reserve(float peakU) {
        final float h = Math.max(0.0f, 1.0f - peakU);
        return 1.0f - (float) Math.exp(-h / RESERVE_SCALE);
    }

    public static float rhythm(float rpmCv) {
        return gaussian(rpmCv, RHYTHM_SIGMA);
    }

    public static float smoothness(float stressCv) {
        return gaussian(stressCv, SMOOTHNESS_SIGMA);
    }

    public static float resilience(float longestRunSeconds) {
        return (float) Math.exp(-Math.max(0.0f, longestRunSeconds) / RESILIENCE_TAU);
    }

    public static float overstressSeverity(float u) {
        final float over = Math.max(0.0f, u - 1.0f) / 0.5f;
        return 0.5f + 0.5f * Math.min(1.0f, over);
    }

    private static float gaussian(float cv, float sigmaSq) {
        if (!(cv > 0.0f)) {
            return 1.0f;
        }
        return (float) Math.exp(-cv * cv / sigmaSq);
    }

    public static float smootherstep(float x) {
        final float t = Math.max(0.0f, Math.min(1.0f, x));
        return t * t * t * (t * (t * 6.0f - 15.0f) + 10.0f);
    }
}
