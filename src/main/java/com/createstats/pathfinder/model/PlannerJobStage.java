package com.createstats.pathfinder.model;

public enum PlannerJobStage {
    IDLE("Idle"),
    WAITING_FOR_SCAN("Waiting for scan"),
    BUILDING_GRAPH("Building graph"),
    COARSE_SEARCH("Coarse search"),
    FINE_SEARCH("Fine vehicle search"),
    SMOOTHING("Smoothing"),
    MERGING("Merging route"),
    READY("Ready"),
    HOLD("Hold");

    private final String label;

    PlannerJobStage(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static PlannerJobStage safeValueOf(String name, PlannerJobStage fallback) {
        if (name == null || name.isBlank()) return fallback;
        try {
            return PlannerJobStage.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}