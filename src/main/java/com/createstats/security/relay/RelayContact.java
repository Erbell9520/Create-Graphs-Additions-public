package com.createstats.security.relay;

public enum RelayContact {
    UNBOUND,
    // unaccepted drives nothing, or it's a stranger's window onto when you're away
    CANDIDATE,

    LINKED,

    UNKNOWN,

    LOST;

    private static final RelayContact[] ORDER = values();

    public boolean bound() {
        return this == LINKED || this == UNKNOWN || this == LOST;
    }

    public static RelayContact byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < ORDER.length ? ORDER[ordinal] : UNBOUND;
    }
}
