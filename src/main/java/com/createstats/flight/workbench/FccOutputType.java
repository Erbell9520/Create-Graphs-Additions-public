package com.createstats.flight.workbench;

public enum FccOutputType {
    RPM_DELTA,
    RPM_SCALE,
    ANGLE_TARGET,
    ANGLE_OFFSET,
    SIGNAL_LEVEL,
    TOGGLE_STATE,
    PULSE_EVENT;

    public static FccOutputType safeValueOf(String name, FccOutputType fallback) {
        if (name == null || name.isEmpty()) return fallback;
        try {
            return valueOf(name);
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}