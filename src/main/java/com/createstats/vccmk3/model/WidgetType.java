package com.createstats.vccmk3.model;

import java.util.Locale;

public enum WidgetType {
    LEVER("Lever", true, false),

    WHEEL("Wheel", true, true),

    STICK("Stick", true, true),

    SELECTOR("Selector", false, false),

    TOGGLE("Toggle", false, false),

    BUTTON("Button", false, true),

    GUARDED("Guarded Switch", false, false),

    DIAL("Dial", true, false);

    private final String label;
    private final boolean continuous;
    private final boolean selfCentring;

    WidgetType(String label, boolean continuous, boolean selfCentring) {
        this.label = label;
        this.continuous = continuous;
        this.selfCentring = selfCentring;
    }

    public String label() {
        return label;
    }

    public boolean isContinuous() {
        return continuous;
    }

    public boolean isSelfCentring() {
        return selfCentring;
    }

    public boolean isMomentary() {
        return this == BUTTON;
    }

    public boolean isGuarded() {
        return this == GUARDED;
    }

    public static WidgetType defaultFor(ControlAxis axis) {
        if (axis == null) return LEVER;
        return switch (axis) {
            case FORWARD, UP -> LEVER;
            case YAW -> WHEEL;
            case PITCH, ROLL -> STICK;
            case AUX -> TOGGLE;
        };
    }

    public static WidgetType safeValueOf(String name, WidgetType fallback) {
        if (name == null || name.isEmpty()) return fallback;
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
