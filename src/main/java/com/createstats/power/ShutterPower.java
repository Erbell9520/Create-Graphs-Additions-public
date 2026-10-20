package com.createstats.power;

public final class ShutterPower {
    private ShutterPower() {
    }

    public static final int IDLE_DRAIN = 1;

    public static final int CYCLE_COST = 80;

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

    public static int cycleCostFor(int height) {
        return CYCLE_COST * Math.max(2, height) / 2;
    }

    // three cycles, derived so a buffer under one cycle can never seal
    public static int bufferFor(int height) {
        return cycleCostFor(height) * 3;
    }

    public static int workingReserveFor(int height) {
        return cycleCostFor(height) * 2;
    }

    public static int ratedDrawFor(int stored, int height) {
        return IDLE_DRAIN + (stored >= workingReserveFor(height) ? 0 : rechargeDrainFor(height));
    }

    public static int rechargeDrainFor(int height) {
        return Math.max(1, cycleCostFor(height) / RECHARGE_TICKS);
    }

    public static boolean canAffordCycle(int stored, int height) {
        return stored >= cycleCostFor(height);
    }
}
