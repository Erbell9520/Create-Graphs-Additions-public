package com.createstats.power;

// locks say absolute things tiers can't
public enum FeLock {
    NONE("Auto"),

    FORCE_ON("Locked on"),

    FORCE_OFF("Locked off");

    private final String displayName;

    FeLock(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public FeLock next() {
        return switch (this) {
            case NONE -> FORCE_ON;
            case FORCE_ON -> FORCE_OFF;
            case FORCE_OFF -> NONE;
        };
    }

    public static FeLock byOrdinal(int ordinal) {
        final FeLock[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NONE;
    }
}
