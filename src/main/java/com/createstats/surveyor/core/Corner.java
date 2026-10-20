package com.createstats.surveyor.core;

public enum Corner {
    SMOOTH("smooth"),

    SHARP("sharp"),

    WIDE("wide");

    public static final double WIDE_TENSION = 1.6;

    public final String key;

    Corner(String key) {
        this.key = key;
    }

    public Corner next(int direction) {
        Corner[] v = values();
        int i = Math.floorMod(ordinal() + (direction < 0 ? -1 : 1), v.length);
        return v[i];
    }

    public static Corner byKey(String key) {
        for (Corner c : values()) if (c.key.equals(key)) return c;
        return SMOOTH;
    }

    public static Corner byOrdinal(int i) {
        Corner[] v = values();
        return i >= 0 && i < v.length ? v[i] : SMOOTH;
    }
}
