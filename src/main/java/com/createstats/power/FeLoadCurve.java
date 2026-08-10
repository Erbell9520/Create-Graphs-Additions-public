package com.createstats.power;

import com.createstats.ModConfiguration;

/** [1] */
public final class FeLoadCurve {

    private FeLoadCurve() {
    }

    /** Draw multiplier for the given number of linked sources. Never below 1.0. */
    public static float multiplier(int sourceCount) {
        return multiplier(sourceCount, exponent());
    }

    /** Testable variant with an explicit exponent, so tests need no config bootstrap. */
    public static float multiplier(int sourceCount, double exponent) {
        if (sourceCount <= 1) {
            return 1.0f;
        }
        return (float) Math.pow(sourceCount, exponent);
    }

    /** Configured exponent, falling back to the default if the config has not loaded yet. */
    public static double exponent() {
        try {
            return ModConfiguration.COMMON.feSourceLoadExponent.get();
        } catch (RuntimeException ignored) {
            return 0.85;
        }
    }
}
