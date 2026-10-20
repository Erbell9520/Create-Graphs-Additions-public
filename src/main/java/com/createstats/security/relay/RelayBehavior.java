package com.createstats.security.relay;

import org.jetbrains.annotations.Nullable;

public enum RelayBehavior {
    FOLLOW("follow"),

    PULSE("pulse"),

    HOLD("hold");

    private static final RelayBehavior[] ORDER = values();

    private final String key;

    RelayBehavior(String key) {
        this.key = key;
    }

    public String key() {
        return this.key;
    }

    @Nullable
    public static RelayBehavior byKey(@Nullable String key) {
        for (RelayBehavior b : ORDER) {
            if (b.key.equals(key)) {
                return b;
            }
        }
        return null;
    }

    public static RelayBehavior[] all() {
        return ORDER;
    }
}
