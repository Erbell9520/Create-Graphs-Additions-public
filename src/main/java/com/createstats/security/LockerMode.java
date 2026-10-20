package com.createstats.security;

public enum LockerMode {
    UNSET,

    GENERAL,

    PERSONAL;

    public boolean personal() {
        return this == PERSONAL;
    }

    public static LockerMode fromOrdinal(int ordinal) {
        final LockerMode[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : UNSET;
    }
}
