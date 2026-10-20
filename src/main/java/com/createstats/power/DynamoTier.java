package com.createstats.power;

public enum DynamoTier {
    OPEN_FRAME(0.75, 0.50),

    STANDARD(1.00, 1.00),

    INDUSTRIAL(1.50, 1.20);

    public static final double BASE_FE_PER_RPM = 1.40625;

    public static final double BASE_STRESS_PER_RPM = 64.0;

    private final double powerScale;
    private final double stressScale;

    DynamoTier(double powerScale, double stressScale) {
        this.powerScale = powerScale;
        this.stressScale = stressScale;
    }

    public double fePerRpm() {
        return baseFePerRpm() * powerScale;
    }

    public double stressPerRpm() {
        return baseStressPerRpm() * stressScale;
    }

    public double fePerStress() {
        final double stress = stressPerRpm();
        return stress <= 0.0 ? 0.0 : fePerRpm() / stress;
    }

    private static double baseFePerRpm() {
        try {
            return com.createstats.ModConfiguration.COMMON.dynamoFePerRpm.get();
        } catch (RuntimeException ignored) {
            return BASE_FE_PER_RPM;
        }
    }

    private static double baseStressPerRpm() {
        try {
            return com.createstats.ModConfiguration.COMMON.dynamoStressImpact.get();
        } catch (RuntimeException ignored) {
            return BASE_STRESS_PER_RPM;
        }
    }
}
