package com.createstats.flight.workbench;

public enum FccControlRole {
    CUSTOM,
    ZONE_SWITCH,
    THRUST,
    YAW,
    LIFT,
    AUX,
    SYSTEM;

    public static FccControlRole safeValueOf(String name, FccControlRole fallback) {
        if (name == null || name.isEmpty()) return fallback;
        try {
            return valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
