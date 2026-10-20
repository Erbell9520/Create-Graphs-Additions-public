package com.createstats.flight.workbench;

public enum FccWidgetType {
    WHEEL,
    LEVER,
    DIAL,
    TOGGLE,
    BUTTON;

    public static FccWidgetType safeValueOf(String name, FccWidgetType fallback) {
        if (name == null || name.isEmpty()) return fallback;
        try {
            return valueOf(name);
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
