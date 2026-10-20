package com.createstats.flight.workbench;

public enum FccRouteKind {
    CUSTOM,
    ZONE_GATE,
    TEMPLATE_ROUTE,
    SYSTEM;

    public static FccRouteKind safeValueOf(String name, FccRouteKind fallback) {
        if (name == null || name.isEmpty()) return fallback;
        try {
            return valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
