package com.createstats.power;

/** [1] */
public enum PduMode {
    OFF("Off"),
    MANUAL("Manual"),
    AUTO("Active Auto");

    private final String displayName;

    PduMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Cycles OFF to MANUAL to AUTO and back, for the one screen button. */
    public PduMode next() {
        return switch (this) {
            case OFF -> MANUAL;
            case MANUAL -> AUTO;
            case AUTO -> OFF;
        };
    }

    /** Ordinal-safe lookup for loading from NBT, defaulting to {@link #MANUAL}. */
    public static PduMode byOrdinal(int ordinal) {
        final PduMode[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : MANUAL;
    }
}
