package com.createstats.power;

public enum StarterMode {
    AUTO,

    REDSTONE,

    HOLD;

    public static StarterMode byOrdinal(int i) {
        final StarterMode[] v = values();
        return i >= 0 && i < v.length ? v[i] : AUTO;
    }
}
