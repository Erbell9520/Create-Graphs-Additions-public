package com.createstats.powertrack.core;

public final class FeederLamp {
    public static final int GREEN = 1;

    public static final int AMBER = -1;

    public static final int DARK = 0;

    private FeederLamp() {
    }

    public static int of(SectionExchange.Role role, int available, int room) {
        if (role == SectionExchange.Role.SUPPLY && available > 0) {
            return GREEN;
        }
        if (role == SectionExchange.Role.COLLECT && room > 0) {
            return AMBER;
        }
        return DARK;
    }
}
