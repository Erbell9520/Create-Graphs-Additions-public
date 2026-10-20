package com.createstats.inventory.core;

public final class StallDetector {
    public static final int[] STALL_TICKS = {600, 1200, 6000, 1000};
    public static final int DEFAULT_INDEX = 1;

    public static final long IDLE_AFTER_TICKS = 24_000L;

    private long lastMovement = Long.MIN_VALUE;
    private int stallIndex = DEFAULT_INDEX;

    public static int ticksFor(int index) {
        return STALL_TICKS[Math.floorMod(index, STALL_TICKS.length)];
    }

    public void setStallIndex(int index) {
        this.stallIndex = Math.floorMod(index, STALL_TICKS.length);
    }

    public int stallIndex() {
        return this.stallIndex;
    }

    public void observe(long gameTime, boolean moved) {
        if (moved) {
            this.lastMovement = gameTime;
        }
    }

    public boolean stalled(long gameTime) {
        if (this.lastMovement == Long.MIN_VALUE) {
            return false;
        }
        final long quiet = gameTime - this.lastMovement;
        return quiet >= ticksFor(this.stallIndex) && quiet < IDLE_AFTER_TICKS;
    }

    public long quietTicks(long gameTime) {
        return this.lastMovement == Long.MIN_VALUE ? -1L : Math.max(0L, gameTime - this.lastMovement);
    }

    public void reset() {
        this.lastMovement = Long.MIN_VALUE;
    }

    public long lastMovement() {
        return this.lastMovement;
    }

    public void restore(long lastMovement) {
        this.lastMovement = lastMovement;
    }
}
