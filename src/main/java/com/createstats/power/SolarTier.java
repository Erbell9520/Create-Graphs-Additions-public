package com.createstats.power;

public enum SolarTier {
    BASIC(0.60),

    REINFORCED(1.00),

    PHOTOVOLTAIC(1.60);

    private final double outputScale;

    SolarTier(double outputScale) {
        this.outputScale = outputScale;
    }

    public double outputScale() {
        return outputScale;
    }

    public double peakFePerTick() {
        return baseFePerTick() * outputScale;
    }

    public static double baseFePerTick() {
        try {
            return com.createstats.ModConfiguration.COMMON.solarFePerTick.get();
        } catch (RuntimeException ignored) {
            return SolarCurve.BASE_FE_PER_TICK;
        }
    }
}
