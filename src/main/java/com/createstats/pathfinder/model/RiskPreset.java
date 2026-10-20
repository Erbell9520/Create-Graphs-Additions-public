package com.createstats.pathfinder.model;

public enum RiskPreset {
    SAFE,
    BALANCED,
    AGGRESSIVE;

    public static RiskPreset safeValueOf(String name, RiskPreset fallback) {
        if (name == null || name.isBlank()) return fallback;
        try {
            return RiskPreset.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
