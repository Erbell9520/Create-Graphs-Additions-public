package com.createstats.flight.workbench;

public enum FccDiagramProfile {
    BALLOON_ZEPPELIN;

    public static FccDiagramProfile safeValueOf(String name, FccDiagramProfile fallback) {
        if (name == null || name.isEmpty()) return fallback;
        try {
            return valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}