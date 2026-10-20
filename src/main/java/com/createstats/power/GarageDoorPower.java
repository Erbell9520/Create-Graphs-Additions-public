package com.createstats.power;

public final class GarageDoorPower {
    public static final int CYCLE_PER_BLOCK = 60;

    public static final int IDLE_BLOCKS_PER_FE = 8;

    public static final int BUFFER_CYCLES = 3;

    public static final int RESERVE_CYCLES = 2;

    public static final int MIN_TRANSFER = DoorPower.TRANSFER_RATE;

    public static final int REFILL_TICKS = 20;

    public static final int RECHARGE_TICKS = DoorPower.RECHARGE_TICKS;

    private GarageDoorPower() {
    }

    public static int cycleCost(int area) {
        return CYCLE_PER_BLOCK * Math.max(0, area);
    }

    public static int idleDraw(int area) {
        return Math.max(1, Math.ceilDiv(Math.max(0, area), IDLE_BLOCKS_PER_FE));
    }

    public static int buffer(int area) {
        return BUFFER_CYCLES * cycleCost(area);
    }

    public static int workingReserve(int area) {
        return RESERVE_CYCLES * cycleCost(area);
    }

    public static int transferRate(int area) {
        return Math.max(MIN_TRANSFER, Math.ceilDiv(cycleCost(area), REFILL_TICKS));
    }

    public static int rechargeDrain(int area) {
        return Math.max(1, Math.ceilDiv(cycleCost(area), RECHARGE_TICKS));
    }

    public static int ratedDrawFor(int stored, int area) {
        return idleDraw(area) + (stored >= workingReserve(area) ? 0 : rechargeDrain(area));
    }

    public static boolean canAffordCycle(int stored, int area) {
        return stored >= cycleCost(area);
    }
}
