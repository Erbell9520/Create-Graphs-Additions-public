package com.createstats.alarm.annunciator;

public enum RuleFault {
    NONE("none"),

    UNBOUND("unbound"),

    NO_CIRCUIT("no_circuit"),

    OUT_OF_RANGE("out_of_range"),

    OFF_CIRCUIT("off_circuit"),

    UNLOADED("unloaded"),

    PANEL_MISSING("panel_missing"),

    CHANNEL_MISSING("channel_missing"),

    NO_SAMPLES("no_samples"),

    MACHINE_MISSING("machine_missing"),

    METRIC_MISSING("metric_missing"),

    NO_HISTORY("no_history"),

    NO_GRID_DATA("no_grid_data");

    private final String serializedName;

    RuleFault(String serializedName) {
        this.serializedName = serializedName;
    }

    public String getSerializedName() {
        return this.serializedName;
    }

    public boolean isFault() {
        return this != NONE;
    }

    public String translationKey() {
        return "gui.create_stats.annunciator.fault." + this.serializedName;
    }

    public boolean isReachProblem() {
        return this == OUT_OF_RANGE || this == OFF_CIRCUIT || this == NO_CIRCUIT;
    }

    public static RuleFault byName(String name) {
        if (name == null || name.isEmpty()) {
            return NONE;
        }
        for (RuleFault fault : values()) {
            if (fault.serializedName.equalsIgnoreCase(name) || fault.name().equalsIgnoreCase(name)) {
                return fault;
            }
        }
        return NONE;
    }
}
