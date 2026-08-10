package com.createstats.pathfinder.model;

public enum Passability {
    PASSABLE,
    MARGINAL,
    BLOCKED,
    UNKNOWN;

    public static Passability safeValueOf(String name, Passability fallback) {
        if (name == null || name.isBlank()) return fallback;
        try {
            return Passability.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}