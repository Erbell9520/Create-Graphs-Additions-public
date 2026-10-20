package com.createstats.power;

import com.createstats.ModConfiguration;

// count^0.85, wide panels plateau
public final class FeLoadCurve {
    private FeLoadCurve() {
    }

    public static float multiplier(int sourceCount) {
        return multiplier(sourceCount, exponent());
    }

    public static float multiplier(int sourceCount, double exponent) {
        if (sourceCount <= 1) {
            return 1.0f;
        }
        return (float) Math.pow(sourceCount, exponent);
    }

    public static double exponent() {
        try {
            return ModConfiguration.COMMON.feSourceLoadExponent.get();
        } catch (RuntimeException ignored) {
            return 0.85;
        }
    }
}
