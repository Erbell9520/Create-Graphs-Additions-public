package com.createstats.power;

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

    public PduMode next() {
        return switch (this) {
            case OFF -> MANUAL;
            case MANUAL -> AUTO;
            case AUTO -> OFF;
        };
    }

    public static PduMode byOrdinal(int ordinal) {
        final PduMode[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : MANUAL;
    }
}
