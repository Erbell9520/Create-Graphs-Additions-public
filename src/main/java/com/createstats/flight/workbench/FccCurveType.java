package com.createstats.flight.workbench;

public enum FccCurveType {
    LINEAR,
    SOFT,
    AGGRESSIVE,
    S_CURVE;

    public static FccCurveType safeValueOf(String name, FccCurveType fallback) {
        if (name == null || name.isEmpty()) return fallback;
        try {
            return valueOf(name);
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
