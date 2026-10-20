package com.createstats.surveyor.core;

// fluid isn't ground to a wheel ray, the vehicle floats or sinks
public enum Ground {
    UNKNOWN("unknown", false, 1.0),

    ROAD("road", false, 1.0),

    NATURAL("natural", false, 1.0),

    SOFT("soft", false, 0.325),

    SLIPPERY("slippery", false, 0.1),

    FRICTIVE("frictive", false, 1.0),

    WATER("water", true, 1.0),
    LAVA("lava", true, 1.0),

    DROP("drop", true, 1.0),

    HURT("hurt", true, 1.0),

    BLOCKED("blocked", true, 1.0);

    public final String key;

    public final boolean wall;

    public final double grip;

    static final Ground[] ALL = values();

    Ground(String key, boolean wall, double grip) {
        this.key = key;
        this.wall = wall;
        this.grip = grip;
    }

    public static Ground byOrdinal(int i) {
        return i >= 0 && i < ALL.length ? ALL[i] : UNKNOWN;
    }

    public boolean wallFor(boolean amphibious) {
        return wall && !(amphibious && this == WATER);
    }

    public static double offroadGrip(double friction) {
        if (!Double.isFinite(friction)) return 1.0;
        double f = friction < 1.0 ? 0.1 + 0.9 * friction : friction;
        return Math.min(f, 1.0);
    }
}
