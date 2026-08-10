package com.createstats.pathfinder.model;

public enum UnknownPolicy {
    BLOCK,
    TENTATIVE,
    WANDERING;

    public static UnknownPolicy safeValueOf(String name, UnknownPolicy fallback) {
        if (name == null || name.isBlank()) return fallback;
        try {
            return UnknownPolicy.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}