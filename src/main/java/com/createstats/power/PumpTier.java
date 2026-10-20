package com.createstats.power;

public enum PumpTier {
    MK_I("Fluid Pump MK I", 128),

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

    public int maxPressure() {
        return maxPressure;
    }

    public int maxFlowMbPerTick() {
        return maxPressure / 2;
    }

    public static PumpTier byOrdinal(int ordinal) {
        final PumpTier[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : MK_I;
    }
}
