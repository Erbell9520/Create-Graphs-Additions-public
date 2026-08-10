package com.createstats.power;

/** [1] */
public enum FeLock {

    /** No override. The distribution panel decides, which is the normal state. */
    NONE("Auto"),
    /** Never shed. Admitted ahead of everything else, exempt from the budget and the dwell. */
    FORCE_ON("Locked on"),
    /** Never run. Removed from the branch entirely, in every mode. */
    FORCE_OFF("Locked off");

    private final String displayName;

    FeLock(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Cycles NONE to FORCE_ON to FORCE_OFF and back, for the one row control. */
    public FeLock next() {
        return switch (this) {
            case NONE -> FORCE_ON;
            case FORCE_ON -> FORCE_OFF;
            case FORCE_OFF -> NONE;
        };
    }

    /** Ordinal-safe lookup for packet and NBT decode, defaulting to {@link #NONE}. */
    public static FeLock byOrdinal(int ordinal) {
        final FeLock[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NONE;
    }
}
