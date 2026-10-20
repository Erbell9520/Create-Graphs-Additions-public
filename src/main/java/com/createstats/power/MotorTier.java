package com.createstats.power;

public enum MotorTier {
    MK_I("Electric Drive MK I", 128, 8192, 2.25),

    MK_II("Electric Drive MK II", 256, 16384, 2.25);

    private final String displayName;
    private final int ratedRpm;
    private final int stressCapacitySu;
    private final double feCostPerRpm;

    MotorTier(String displayName, int ratedRpm, int stressCapacitySu, double feCostPerRpm) {
        this.displayName = displayName;
        this.ratedRpm = ratedRpm;
        this.stressCapacitySu = stressCapacitySu;
        this.feCostPerRpm = feCostPerRpm;
    }

    public String displayName() {
        return displayName;
    }

    public int ratedRpm() {
        return ratedRpm;
    }

    public int stressCapacitySu() {
        return stressCapacitySu;
    }

    public double feCostPerRpm() {
        return feCostPerRpm;
    }

    public int feCostPerTick() {
        return feCostForRpm(ratedRpm);
    }

    public int feCostForRpm(int rpm) {
        return (int) Math.round(rpm * feCostPerRpm);
    }

    public float stressCapacityCoeff() {
        return (float) stressCapacitySu / ratedRpm;
    }

    public static MotorTier byOrdinal(int ordinal) {
        final MotorTier[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : MK_I;
    }
}
