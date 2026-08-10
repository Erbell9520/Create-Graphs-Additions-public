package com.createstats.flight.workbench;

public enum FccDeviceCategory {
    REGULAR_PROP("Prop"),
    WHEEL_MOUNT("Wheel"),
    PROP_BEARING("Bearing"),
    FIN("Fin"),
    REDSTONE_LINK("Link");

    private final String shortLabel;

    FccDeviceCategory(String shortLabel) {
        this.shortLabel = shortLabel;
    }

    public String shortLabel() {
        return shortLabel;
    }

    public static FccDeviceCategory safeValueOf(String name, FccDeviceCategory fallback) {
        if (name == null || name.isEmpty()) return fallback;
        try {
            return valueOf(name);
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}