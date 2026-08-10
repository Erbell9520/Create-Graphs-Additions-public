package com.createstats.pathfinder.model;

public final class HazardFlags {
    public static final int UNKNOWN_CHUNK = 1;
    public static final int LIQUID = 1 << 1;
    public static final int LAVA = 1 << 2;
    public static final int RAIL_OR_FLOOR = 1 << 3;
    public static final int UNSTABLE = 1 << 4;
    public static final int TWO_BLOCK = 1 << 5;

    private HazardFlags() {
    }
}