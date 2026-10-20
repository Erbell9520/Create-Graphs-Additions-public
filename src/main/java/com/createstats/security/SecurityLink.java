package com.createstats.security;

import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public enum SecurityLink {
    LINKED,

    UNKNOWN,

    LOST;

    public boolean isLost() {
        return this == LOST;
    }

    public record Contact<T>(SecurityLink state, @Nullable T panel) {
    }

    public static <T> Contact<T> resolve(@Nullable T known, boolean hadPanel, boolean knownLoaded,
                                         Supplier<T> sweep) {
        return resolve(known, hadPanel, knownLoaded, true, sweep);
    }

    public static <T> Contact<T> resolve(@Nullable T known, boolean hadPanel, boolean knownLoaded,
                                         boolean onCircuit, Supplier<T> sweep) {
        if (known != null) {
            return new Contact<>(LINKED, known);
        }
        if (hadPanel && !knownLoaded) {
            return new Contact<>(UNKNOWN, null);
        }
        if (!onCircuit) {
            return new Contact<>(UNKNOWN, null);
        }
        final T found = sweep.get();
        return found != null ? new Contact<>(LINKED, found) : new Contact<>(LOST, null);
    }
}
