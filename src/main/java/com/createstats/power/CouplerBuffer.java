package com.createstats.power;

import javax.annotation.Nullable;

// the two sides aren't symmetric. new age's own rollback comment literally says "We can only HOPE"
public final class CouplerBuffer {
    private final int capacity;
    private int energy;

    private long tick = Long.MIN_VALUE;

    private int seamMoved;

    private int gridMoved;

    private long allowanceTick = Long.MIN_VALUE;
    private int allowanceUsed;

    public record Tick(int seamMoved, int gridMoved) {
    }

    public CouplerBuffer(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    public int capacity() {
        return this.capacity;
    }

    public int energy() {
        return this.energy;
    }

    public void setEnergy(int fe) {
        this.energy = Math.max(0, Math.min(this.capacity, fe));
    }

    @Nullable
    public Tick roll(long now) {
        if (this.tick == now) {
            return null;
        }
        final Tick closed =
                this.tick == Long.MIN_VALUE ? null : new Tick(this.seamMoved, this.gridMoved);
        this.tick = now;
        this.seamMoved = 0;
        this.gridMoved = 0;
        return closed;
    }

    public int seamMoved() {
        return this.seamMoved;
    }

    public int gridMoved() {
        return this.gridMoved;
    }

    public int seamReceive(int maxReceive, boolean simulate) {
        final int room = Math.max(0, Math.min(Math.max(0, maxReceive), this.capacity - this.energy));
        if (room > 0 && !simulate) {
            this.energy += room;
            this.seamMoved += room;
        }
        return room;
    }

    public int seamExtract(int maxExtract, boolean simulate) {
        final int taken = Math.max(0, Math.min(Math.max(0, maxExtract), this.energy));
        if (taken > 0 && !simulate) {
            this.energy -= taken;
            this.seamMoved -= taken;
        }
        return taken;
    }

    public int gridAllowance(int rate, long now) {
        return Math.max(0, rate - (now == this.allowanceTick ? this.allowanceUsed : 0));
    }

    private void spendAllowance(int fe, long now) {
        if (now != this.allowanceTick) {
            this.allowanceTick = now;
            this.allowanceUsed = 0;
        }
        this.allowanceUsed += fe;
    }

    public int gridReceive(int maxReceive, boolean simulate, int rate, long now) {
        final int room = Math.max(0, Math.min(Math.min(Math.max(0, maxReceive), this.gridAllowance(rate, now)),
                this.capacity - this.energy));
        if (room > 0 && !simulate) {
            this.energy += room;
            this.gridMoved += room;
            this.spendAllowance(room, now);
        }
        return room;
    }

    public int gridExtract(int maxExtract, boolean simulate, int rate, long now) {
        final int taken = Math.max(0, Math.min(Math.min(Math.max(0, maxExtract), this.gridAllowance(rate, now)),
                this.energy));
        if (taken > 0 && !simulate) {
            this.energy -= taken;
            this.gridMoved += taken;
            this.spendAllowance(taken, now);
        }
        return taken;
    }
}
