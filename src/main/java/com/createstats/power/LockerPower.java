package com.createstats.power;

public final class LockerPower {
    private LockerPower() {
    }

    public static final int DRAIN = 1;

    public static final int BUFFER = 200;

    public static final int TRANSFER_RATE = 64;

    public static int ratedDrawFor(int stored) {
        return DRAIN;
    }

    public static boolean canRead(int stored) {
        return stored >= DRAIN;
    }
}
