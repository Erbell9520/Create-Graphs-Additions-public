package com.createstats.security;

public enum Presence {
    PRESENT,

    STALE,

    UNLOADED,

    UNREACHED,

    MISSING;

    private static final Presence[] ORDER = values();

    public boolean available() {
        return this == PRESENT;
    }

    public static Presence byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < ORDER.length ? ORDER[ordinal] : STALE;
    }
}
