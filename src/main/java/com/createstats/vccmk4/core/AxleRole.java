package com.createstats.vccmk4.core;

public enum AxleRole {
    STEER("steer"),

    FIXED("fixed"),

    FREE("free");

    private final String key;

    AxleRole(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public AxleRole next() {
        AxleRole[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public static AxleRole byOrdinal(int ordinal) {
        AxleRole[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : FIXED;
    }
}
