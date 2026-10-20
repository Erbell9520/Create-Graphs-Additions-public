package com.mrh0.createaddition.blocks.modular_accumulator;

import net.neoforged.neoforge.energy.IEnergyStorage;

public final class FakeAccumulatorEnergyHandler implements IEnergyStorage {
    private int stored;
    private final int capacity;
    private final int rate;

    public FakeAccumulatorEnergyHandler(int stored, int capacity, int rate) {
        this.stored = stored;
        this.capacity = capacity;
        this.rate = rate;
    }

    public static FakeAccumulatorEnergyHandler unresolved() {
        return new FakeAccumulatorEnergyHandler(0, 0, 0);
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        final int accepted = Math.max(0, Math.min(Math.min(rate, maxReceive), capacity - stored));
        if (!simulate) {
            stored += accepted;
        }
        return accepted;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        final int given = Math.max(0, Math.min(Math.min(rate, maxExtract), stored));
        if (!simulate) {
            stored -= given;
        }
        return given;
    }

    @Override
    public int getEnergyStored() {
        return stored;
    }

    @Override
    public int getMaxEnergyStored() {
        return capacity;
    }

    @Override
    public boolean canExtract() {
        return capacity > 0;
    }

    @Override
    public boolean canReceive() {
        return capacity > 0;
    }
}
