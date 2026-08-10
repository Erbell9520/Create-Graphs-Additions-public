package com.createstats.power;

/** [1] */
public enum MotorTier {

    /** Sources 128 RPM and up to 4,096 SU, for 512 FE/t. */
    MK_I("Electric Drive MK I", 128, 4096, 4),
    /** Sources 256 RPM (Create's top speed) and up to 16,384 SU, for 1,024 FE/t. */
    MK_II("Electric Drive MK II", 256, 16384, 4);

    private final String displayName;
    private final int ratedRpm;
    private final int stressCapacitySu;
    private final int feCostPerRpm;

    MotorTier(String displayName, int ratedRpm, int stressCapacitySu, int feCostPerRpm) {
        this.displayName = displayName;
        this.ratedRpm = ratedRpm;
        this.stressCapacitySu = stressCapacitySu;
        this.feCostPerRpm = feCostPerRpm;
    }

    public String displayName() {
        return displayName;
    }

    /** The shaft speed this motor sources when it is running, in RPM (magnitude). */
    public int ratedRpm() {
        return ratedRpm;
    }

    /** The stress capacity this motor provides at its rated speed, in SU. */
    public int stressCapacitySu() {
        return stressCapacitySu;
    }

    /** FE burned per RPM per tick while running, so the whole draw is {@link #feCostPerTick()}. */
    public int feCostPerRpm() {
        return feCostPerRpm;
    }

    /** What the motor spends every tick while running at its rated speed. */
    public int feCostPerTick() {
        return ratedRpm * feCostPerRpm;
    }

    /** [2] */
    public float stressCapacityCoeff() {
        return (float) stressCapacitySu / ratedRpm;
    }

    /** Ordinal-safe lookup for NBT decode, defaulting to {@link #MK_I}. */
    public static MotorTier byOrdinal(int ordinal) {
        final MotorTier[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : MK_I;
    }
}
