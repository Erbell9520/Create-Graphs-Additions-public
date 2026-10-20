package com.createstats.power;

public final class SolarCurve {
    private SolarCurve() {
    }

    public static final int BASE_FE_PER_TICK = 32;

    public static final int DAY_TICKS = 24_000;

    public static final int DAYLIGHT_TICKS = 12_000;

    public static final double ARRAY_STEP = 0.25;

    public static final double ARRAY_CAP = 1.50;

    public static final int ARRAY_REFERENCE = 9;

    public static final int ARRAY_CAP_PANELS = (int) Math.round(
            Math.pow(ARRAY_REFERENCE, (ARRAY_CAP - 1.0) / ARRAY_STEP));

    public static final int FULL_SKY_LIGHT = 15;

    public enum Sky {
        CLEAR(1.00),

        RAIN(0.35),

        THUNDER(0.15);

        private final double factor;

        Sky(double factor) {
            this.factor = factor;
        }

        public double factor() {
            return factor;
        }
    }

    public static double arrayMultiplier(int panels) {
        if (panels <= 1) {
            return 1.0;
        }
        final double bonus = ARRAY_STEP * Math.log(panels) / Math.log(ARRAY_REFERENCE);
        return Math.min(ARRAY_CAP, 1.0 + bonus);
    }

    public static double sunFactor(long dayTime) {
        final long t = Math.floorMod(dayTime, (long) DAY_TICKS);
        if (t >= DAYLIGHT_TICKS) {
            return 0.0;
        }
        return Math.max(0.0, Math.sin(Math.PI * t / DAYLIGHT_TICKS));
    }

    public static double skyLightFactor(int skyLight) {
        if (skyLight <= 0) {
            return 0.0;
        }
        return Math.min(1.0, skyLight / (double) FULL_SKY_LIGHT);
    }

    public static double exposure(int skyLight, boolean coveredByPanel) {
        return coveredByPanel ? 0.0 : skyLightFactor(skyLight);
    }

    public static int effectiveArraySize(double exposureSum) {
        if (!(exposureSum > 0.0)) {
            return 1;
        }
        return (int) Math.max(1L, Math.round(exposureSum));
    }

    public static boolean isNight(long dayTime) {
        return sunFactor(dayTime) <= 0.0;
    }

    public static int deliverable(int rate, int outflowCap) {
        return Math.max(0, Math.min(rate, outflowCap));
    }

    public static int fePerTick(double peakFePerTick, int panels, long dayTime, Sky sky,
                                int skyLight) {
        return fePerTick(peakFePerTick, panels, dayTime, sky, skyLightFactor(skyLight));
    }

    public static int fePerTick(double peakFePerTick, int panels, long dayTime, Sky sky,
                                double exposure) {
        final double light = sunFactor(dayTime) * Math.max(0.0, Math.min(1.0, exposure))
                * sky.factor();
        if (light <= 0.0 || peakFePerTick <= 0.0) {
            return 0;
        }
        final double out = peakFePerTick * arrayMultiplier(panels) * light;

        return (int) Math.floor(out);
    }

    public static int fePerTickIn(double peakFePerTick, int panels, long dayTime, int aheadTicks,
                                 Sky sky, int skyLight) {
        return fePerTick(peakFePerTick, panels, dayTime + Math.max(0, aheadTicks), sky, skyLight);
    }

    public static int fePerTickIn(double peakFePerTick, int panels, long dayTime, int aheadTicks,
                                 Sky sky, double exposure, boolean clockRuns) {
        final long at = clockRuns ? dayTime + Math.max(0, aheadTicks) : dayTime;
        return fePerTick(peakFePerTick, panels, at, sky, exposure);
    }
}
