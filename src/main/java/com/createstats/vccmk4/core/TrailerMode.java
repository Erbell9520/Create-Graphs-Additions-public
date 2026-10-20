package com.createstats.vccmk4.core;

public enum TrailerMode {
    ASSIST("assist"),
    MIRROR("mirror"),
    OFF("off");

    public static final TrailerMode DEFAULT = ASSIST;

    private final String key;

    TrailerMode(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static TrailerMode byOrdinal(int ordinal) {
        TrailerMode[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : DEFAULT;
    }

    public static TrailerMode byKey(String key) {
        for (TrailerMode m : values()) {
            if (m.key.equals(key)) return m;
        }
        return DEFAULT;
    }
}
