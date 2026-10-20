package com.createstats.security;

public enum Truth {
    TRUE,
    FALSE,
    UNKNOWN;

    private static final Truth[] ORDER = values();

    public boolean isKnown() {
        return this != UNKNOWN;
    }

    public boolean isTrue() {
        return this == TRUE;
    }

    public static Truth of(boolean value) {
        return value ? TRUE : FALSE;
    }

    public Truth not() {
        return switch (this) {
            case TRUE -> FALSE;
            case FALSE -> TRUE;
            case UNKNOWN -> UNKNOWN;
        };
    }

    public Truth or(Truth other) {
        if (this == TRUE || other == TRUE) {
            return TRUE;
        }
        if (this == FALSE && other == FALSE) {
            return FALSE;
        }
        return UNKNOWN;
    }

    public Truth and(Truth other) {
        if (this == FALSE || other == FALSE) {
            return FALSE;
        }
        if (this == TRUE && other == TRUE) {
            return TRUE;
        }
        return UNKNOWN;
    }

    public static Truth byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < ORDER.length ? ORDER[ordinal] : UNKNOWN;
    }
}
