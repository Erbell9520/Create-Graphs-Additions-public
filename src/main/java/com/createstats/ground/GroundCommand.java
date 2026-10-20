package com.createstats.ground;

public enum GroundCommand {
    RESET_TRIP,

    CLEAR_TRAIL,

    RESET_LEARNED;

    public int bit() {
        return 1 << ordinal();
    }

    public static int all() {
        int bits = 0;
        for (final GroundCommand c : values()) {
            bits |= c.bit();
        }
        return bits;
    }

    public static GroundCommand byOrdinal(int i) {
        final GroundCommand[] v = values();
        return i >= 0 && i < v.length ? v[i] : null;
    }
}
