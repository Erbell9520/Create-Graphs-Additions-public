package com.createstats.vccmk3.hardware;

import com.createstats.vccmk3.model.OutputType;

import java.util.Locale;
import java.util.Set;

public enum DeviceCategory {
    // the governor is mandatory, thrust should be a real block in the shaft
    PROPELLER("Propeller", "Prop", 0xFF39C8FF, true,
            Set.of(OutputType.RPM_DELTA, OutputType.RPM_SCALE)),

    GYRO_BEARING("Gyro Bearing", "Gyro", 0xFFD4AF37, true,
            Set.of(OutputType.RPM_DELTA, OutputType.RPM_SCALE, OutputType.ANGLE_TARGET, OutputType.ANGLE_OFFSET)),

    WHEEL("Wheel Mount", "Wheel", 0xFF9ACD32, true,
            Set.of(OutputType.RPM_DELTA, OutputType.RPM_SCALE, OutputType.STEER_SIGNAL)),

    CONTROL_SURFACE("Control Surface", "Surf", 0xFF55E0A0, false,
            Set.of(OutputType.ANGLE_TARGET, OutputType.ANGLE_OFFSET)),

    SIGNAL_LINK("Signal Link", "Link", 0xFFFF6B6B, false,
            Set.of(OutputType.SIGNAL_LEVEL, OutputType.TOGGLE_STATE, OutputType.PULSE_EVENT));

    private final String label;
    private final String shortLabel;
    private final int color;
    private final boolean requiresGovernor;
    private final Set<OutputType> accepts;

    DeviceCategory(String label, String shortLabel, int color, boolean requiresGovernor, Set<OutputType> accepts) {
        this.label = label;
        this.shortLabel = shortLabel;
        this.color = color;
        this.requiresGovernor = requiresGovernor;
        this.accepts = accepts;
    }

    public String label() {
        return label;
    }

    public String shortLabel() {
        return shortLabel;
    }

    public int color() {
        return color;
    }

    public boolean requiresGovernor() {
        return requiresGovernor;
    }

    public boolean accepts(OutputType type) {
        return type != null && accepts.contains(type);
    }

    public Set<OutputType> acceptedOutputs() {
        return accepts;
    }

    public OutputType defaultOutput() {
        return switch (this) {
            case PROPELLER, WHEEL -> OutputType.RPM_DELTA;
            case GYRO_BEARING -> OutputType.RPM_DELTA;
            case CONTROL_SURFACE -> OutputType.ANGLE_TARGET;
            case SIGNAL_LINK -> OutputType.SIGNAL_LEVEL;
        };
    }

    public static DeviceCategory safeValueOf(String name, DeviceCategory fallback) {
        if (name == null || name.isEmpty()) return fallback;
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
