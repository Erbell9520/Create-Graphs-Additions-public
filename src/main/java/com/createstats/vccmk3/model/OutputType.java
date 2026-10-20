package com.createstats.vccmk3.model;

import java.util.Locale;

public enum OutputType {
    RPM_DELTA("RPM Delta", "rpm", -1.0, 1.0, 0.25, true),

    RPM_SCALE("RPM Scale", "x", -1.0, 1.0, 0.25, true),

    ANGLE_TARGET("Angle", "deg", -45.0, 45.0, 15.0, true),

    ANGLE_OFFSET("Angle Trim", "deg", -45.0, 45.0, 15.0, true),

    STEER_SIGNAL("Steering", "", -15.0, 15.0, 5.0, true),

    SIGNAL_LEVEL("Signal", "rs", 0.0, 15.0, 15.0, false),

    TOGGLE_STATE("Toggle", "", 0.0, 1.0, 1.0, false),

    PULSE_EVENT("Pulse", "", 0.0, 1.0, 1.0, false);

    private final String label;
    private final String unit;
    private final double defaultMin;
    private final double defaultMax;
    private final double defaultScale;
    private final boolean bipolar;

    OutputType(String label, String unit, double defaultMin, double defaultMax,
               double defaultScale, boolean bipolar) {
        this.label = label;
        this.unit = unit;
        this.defaultMin = defaultMin;
        this.defaultMax = defaultMax;
        this.defaultScale = defaultScale;
        this.bipolar = bipolar;
    }

    public String label() {
        return label;
    }

    public String unit() {
        return unit;
    }

    public double defaultMin() {
        return defaultMin;
    }

    public double defaultMax() {
        return defaultMax;
    }

    public double defaultScale() {
        return defaultScale;
    }

    public boolean isBipolar() {
        return bipolar;
    }

    public boolean isRedstone() {
        return this == SIGNAL_LEVEL || this == TOGGLE_STATE || this == PULSE_EVENT;
    }

    public boolean isKinetic() {
        return this == RPM_DELTA || this == RPM_SCALE;
    }

    public boolean isAngular() {
        return this == ANGLE_TARGET || this == ANGLE_OFFSET;
    }

    public static OutputType safeValueOf(String name, OutputType fallback) {
        if (name == null || name.isEmpty()) return fallback;
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
