package com.createstats.vccmk4.core;

public enum BodyRole {
    PART("part"),

    TRAILER("trailer"),

    TOWED("towed"),

    SEPARATE("separate"),

    LOAD("load");

    private final String key;

    BodyRole(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public boolean takesMode() {
        return this == TRAILER || this == TOWED;
    }

    public boolean neverTouched() {
        return this == SEPARATE || this == LOAD;
    }

    public static BodyRole byOrdinal(int ordinal) {
        BodyRole[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : PART;
    }
}
