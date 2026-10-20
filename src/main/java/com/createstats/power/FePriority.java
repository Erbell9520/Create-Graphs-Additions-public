package com.createstats.power;

// ordinal order is serve order, don't reorder
public enum FePriority {
    CRITICAL("Critical"),
    HIGH("High"),
    NORMAL("Normal"),
    LOW("Low"),
    OPTIONAL("Optional");

    public static final FePriority DEFAULT = NORMAL;

    private final String displayName;

    FePriority(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public String getShortName() {
        return switch (this) {
            case CRITICAL -> "CRIT";
            case HIGH -> "HIGH";
            case NORMAL -> "NORM";
            case LOW -> "LOW";
            case OPTIONAL -> "OPT";
        };
    }

    public FePriority next() {
        final FePriority[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    public static FePriority byOrdinal(int ordinal) {
        final FePriority[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            return DEFAULT;
        }
        return values[ordinal];
    }
}
