package com.createstats.surveyor.core;

public enum Comfort {
    GENTLE("gentle", 1.2, 0.8, 1.0, 1.5),
    NORMAL("normal", 2.0, 1.4, 1.6, 3.0),
    BRISK("brisk", 3.2, 2.2, 2.4, 5.0);

    public final String key;

    public final double lateral;

    public final double accel;

    public final double decel;

    public final double jerk;

    Comfort(String key, double lateral, double accel, double decel, double jerk) {
        this.key = key;
        this.lateral = lateral;
        this.accel = accel;
        this.decel = decel;
        this.jerk = jerk;
    }

    public Comfort next(int direction) {
        Comfort[] v = values();
        return v[Math.floorMod(ordinal() + (direction < 0 ? -1 : 1), v.length)];
    }

    public static Comfort byKey(String key) {
        for (Comfort c : values()) if (c.key.equals(key)) return c;
        return NORMAL;
    }

    public static Comfort byOrdinal(int i) {
        Comfort[] v = values();
        return i >= 0 && i < v.length ? v[i] : NORMAL;
    }
}
