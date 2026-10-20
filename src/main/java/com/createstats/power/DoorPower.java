package com.createstats.power;

public final class DoorPower {
    private DoorPower() {
    }

    public static final int IDLE_DRAIN = 1;

    public static final int CYCLE_COST = 160;

    public static final int BUFFER = CYCLE_COST * 3;

    public static final int WORKING_RESERVE = CYCLE_COST * 2;

    public static final int RECHARGE_TICKS = 40;

    public static final int RECHARGE_DRAIN = Math.max(1, CYCLE_COST / RECHARGE_TICKS);

    public static final int TRANSFER_RATE = 128;

    public static int ratedDrawFor(int stored) {
        return IDLE_DRAIN + (stored >= WORKING_RESERVE ? 0 : RECHARGE_DRAIN);
    }

    public static boolean canAffordCycle(int stored) {
        return stored >= CYCLE_COST;
    }
}
