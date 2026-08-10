package com.createstats.power;

/** [1] */
public enum FePriority {
    CRITICAL("Critical"),
    HIGH("High"),
    NORMAL("Normal"),
    LOW("Low"),
    OPTIONAL("Optional");

    /** Tier a non-{@link FeConsumer} load (a third party machine) is treated as: neither privileged nor shed first. */
    public static final FePriority DEFAULT = NORMAL;

    private final String displayName;

    FePriority(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    /** The next tier for a cycle control, wrapping {@code OPTIONAL} back to {@code CRITICAL}. */
    public FePriority next() {
        final FePriority[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    /** Ordinal lookup for packet and NBT decode, clamped to {@link #DEFAULT} on anything out of range. */
    public static FePriority byOrdinal(int ordinal) {
        final FePriority[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            return DEFAULT;
        }
        return values[ordinal];
    }
}
