package com.createstats.surveyor.core;

public enum PathKind {
    LOOP("loop"),
    ONE_WAY("one_way");

    public final String key;

    PathKind(String key) {
        this.key = key;
    }

    public static PathKind byKey(String key) {
        for (PathKind k : values()) if (k.key.equals(key)) return k;
        return ONE_WAY;
    }

    public static PathKind byOrdinal(int i) {
        PathKind[] v = values();
        return i >= 0 && i < v.length ? v[i] : ONE_WAY;
    }
}
