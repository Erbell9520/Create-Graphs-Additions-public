package com.createstats.power;

import javax.annotation.Nullable;

// pure so the cap can be asserted. duplication and voiding bugs live in transport
public final class LimiterBudget {
    private int capacity;
    private int energy;

    private long tick = Long.MIN_VALUE;
    private int taken;
    private int given;
    private boolean clipped;

    public record Tick(int moved, boolean limited) {
    }

    public LimiterBudget(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    public int capacity() {
        return Math.max(this.capacity, this.energy);
    }

    public void setCapacity(int fe) {
        this.capacity = Math.max(1, fe);
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
        final Tick closed = this.tick == Long.MIN_VALUE ? null : new Tick(this.given, this.clipped);
        this.tick = now;
        this.taken = 0;
        this.given = 0;
        this.clipped = false;
        return closed;
    }

    public int taken() {
        return this.taken;
    }

    public int given() {
        return this.given;
    }

    public boolean clipped() {
        return this.clipped;
    }

    public int room(int wanted) {
        return Math.max(0, Math.min(wanted, this.capacity - this.energy));
    }

    public int intakeRoom(int wanted, int cap) {
        return Math.max(0, Math.min(this.room(wanted), cap - this.taken));
    }

    public int bookIntake(int fe) {
        final int booked = this.room(fe);
        this.energy += booked;
        this.taken += booked;
        return booked;
    }

    public int stored(int wanted) {
        return Math.max(0, Math.min(wanted, this.energy));
    }

    public int outflowAvailable(int wanted, int cap) {
        return Math.max(0, Math.min(this.stored(wanted), cap - this.given));
    }

    public int bookOutflow(int fe) {
        final int booked = this.stored(fe);
        this.energy -= booked;
        this.given += booked;
        return booked;
    }

    public int bookPassThrough(int fe, int cap) {
        final int booked = Math.max(0, Math.min(fe, cap - this.given));
        this.given += booked;
        return booked;
    }

    public void noteClipped(int wanted, int allowed) {
        if (wanted > allowed) {
            this.clipped = true;
        }
    }

    public void noteClipped(boolean held) {
        if (held) {
            this.clipped = true;
        }
    }
}
