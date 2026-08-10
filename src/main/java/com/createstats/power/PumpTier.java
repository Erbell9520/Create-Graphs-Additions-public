package com.createstats.power;

/** [1] */
public enum PumpTier {

    /** Matches a Create pump at 128 RPM: pressure 128, so up to 64 mB/t. */
    MK_I("Fluid Pump MK I", 128),
    /** Matches a Create pump at 256 RPM (Create's top speed): pressure 256, so up to 128 mB/t. */
    MK_II("Fluid Pump MK II", 256);

    private final String displayName;
    private final int maxPressure;

    PumpTier(String displayName, int maxPressure) {
        this.displayName = displayName;
        this.maxPressure = maxPressure;
    }

    public String displayName() {
        return displayName;
    }

    /** [2] */
    public int maxPressure() {
        return maxPressure;
    }

    /** [3] */
    public int maxFlowMbPerTick() {
        return maxPressure / 2;
    }

    /** Ordinal-safe lookup for NBT decode, defaulting to {@link #MK_I}. */
    public static PumpTier byOrdinal(int ordinal) {
        final PumpTier[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : MK_I;
    }
}
