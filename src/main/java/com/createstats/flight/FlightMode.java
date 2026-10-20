package com.createstats.flight;

import java.util.EnumSet;
import java.util.Set;

public enum FlightMode {
    OFF        (EnumSet.noneOf(SensorRequirement.class)),
    STANDBY    (EnumSet.of(SensorRequirement.GIMBAL)),

    GO_N       (EnumSet.of(SensorRequirement.GIMBAL, SensorRequirement.VELOCITY)),
    GO_S       (EnumSet.of(SensorRequirement.GIMBAL, SensorRequirement.VELOCITY)),
    GO_E       (EnumSet.of(SensorRequirement.GIMBAL, SensorRequirement.VELOCITY)),
    GO_W       (EnumSet.of(SensorRequirement.GIMBAL, SensorRequirement.VELOCITY)),
    NAV_TARGET (EnumSet.of(SensorRequirement.GIMBAL, SensorRequirement.VELOCITY, SensorRequirement.NAV_TABLE));

    private final Set<SensorRequirement> requires;

    FlightMode(Set<SensorRequirement> requires) {
        this.requires = requires;
    }

    public Set<SensorRequirement> requires() {
        return requires;
    }

    public boolean isAvailable(Set<SensorRequirement> linkedSensors) {
        return linkedSensors.containsAll(requires);
    }

    public boolean isAutonomousNavigationMode() {
        return this == GO_N || this == GO_S || this == GO_E || this == GO_W || this == NAV_TARGET;
    }

    public boolean isEnabledInCurrentBuild() {
        return !isAutonomousNavigationMode();
    }

    public static FlightMode sanitizeForCurrentBuild(FlightMode mode) {
        if (mode == null) return OFF;
        return mode.isEnabledInCurrentBuild() ? mode : STANDBY;
    }

    public static FlightMode safeValueOf(String name, FlightMode fallback) {
        if (name == null) return fallback;
        try {
            return FlightMode.valueOf(name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
