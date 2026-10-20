package com.createstats.vccmk3.model;

import java.util.Locale;

public enum ControlAxis {
    FORWARD("Forward", "FWD", false, 0xFF62D5FF),

    UP("Lift", "UP", false, 0xFF6EE7A8),

    YAW("Yaw", "YAW", true, 0xFFD4AF37),

    PITCH("Pitch", "PCH", true, 0xFFFFB454),

    ROLL("Roll", "ROL", true, 0xFFFF6B6B),

    // AUX exists so people don't paint cargo doors onto PITCH
    AUX("Auxiliary", "AUX", false, 0xFF8CA1B4);

    private final String label;
    private final String shortLabel;
    private final boolean moment;
    private final int color;

    ControlAxis(String label, String shortLabel, boolean moment, int color) {
        this.label = label;
        this.shortLabel = shortLabel;
        this.moment = moment;
        this.color = color;
    }

    public String label() {
        return label;
    }

    public String shortLabel() {
        return shortLabel;
    }

    public boolean wantsMoment() {
        return moment;
    }

    public boolean isFlightAxis() {
        return this != AUX;
    }

    public int color() {
        return color;
    }

    public String authorityUnit() {
        return moment ? "rad/s2" : "m/s2";
    }

    public static ControlAxis safeValueOf(String name, ControlAxis fallback) {
        if (name == null || name.isEmpty()) return fallback;
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
