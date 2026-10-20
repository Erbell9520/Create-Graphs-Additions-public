package com.createstats.power;

import javax.annotation.Nullable;

// the two sides aren't symmetric. new age's own rollback comment literally says "We can only HOPE"
public final class CouplerBuffer {
    private final int capacity;
    private int energy;

    private long tick = Long.MIN_VALUE;

    private int seamMoved;

    private int gridMoved;

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

    public int gridReceive(int maxReceive, boolean simulate, int rate) {
        final int allowance = Math.max(0, rate - this.gridMoved);
        final int room = Math.max(0, Math.min(Math.min(Math.max(0, maxReceive), allowance),
                this.capacity - this.energy));
        if (room > 0 && !simulate) {
            this.energy += room;
            this.gridMoved += room;
        }
        return room;
    }

    public int gridExtract(int maxExtract, boolean simulate, int rate) {
        final int allowance = Math.max(0, rate - this.gridMoved);
        final int taken =
                Math.max(0, Math.min(Math.min(Math.max(0, maxExtract), allowance), this.energy));
        if (taken > 0 && !simulate) {
            this.energy -= taken;
            this.gridMoved += taken;
        }
        return taken;
    }
}
