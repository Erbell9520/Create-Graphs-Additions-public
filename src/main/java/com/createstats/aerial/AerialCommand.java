package com.createstats.aerial;

public enum AerialCommand {
    SAVE_TRAIL_AS_ROUTE,

    CLEAR_TRAIL,

    RESET_ENVELOPE;

    public int bit() {
        return 1 << ordinal();
    }

    public static int all() {
        int bits = 0;
        for (final AerialCommand c : values()) {
            bits |= c.bit();
        }
        return bits;
    }

    public static AerialCommand byOrdinal(int i) {
        final AerialCommand[] v = values();
        return i >= 0 && i < v.length ? v[i] : null;
    }
}
