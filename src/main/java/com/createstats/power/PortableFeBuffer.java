package com.createstats.power;

import net.neoforged.neoforge.energy.IEnergyStorage;

// one object for the life of the block, cached handles stay right
public final class PortableFeBuffer implements IEnergyStorage {
    private final Runnable onChanged;
    private int energy;
    private int capacity = PortableFeTransfer.bufferCapacity(0);
    private boolean chargeOpen;
    private boolean offering;
    private int trainRoom;

    public PortableFeBuffer(Runnable onChanged) {
        this.onChanged = onChanged;
    }

    public void configure(int rate, boolean chargeOpen, boolean offering, int trainRoom) {
        if (chargeOpen && offering) {
            throw new IllegalArgumentException("a portable interface is a load or a source, never both");
        }
        this.capacity = PortableFeTransfer.bufferCapacity(rate);
        this.chargeOpen = chargeOpen;
        this.offering = offering;
        this.trainRoom = Math.max(0, trainRoom);
    }

    public void close() {
        this.chargeOpen = false;
        this.offering = false;
    }

    public int energy() {
        return energy;
    }

    public void setEnergy(int energy) {
        this.energy = Math.max(0, energy);
    }

    public void add(int fe) {
        if (fe > 0) {
            energy += fe;
            onChanged.run();
        }
    }

    public void remove(int fe) {
        if (fe > 0) {
            energy = Math.max(0, energy - fe);
            onChanged.run();
        }
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        if (!chargeOpen || maxReceive <= 0) {
            return 0;
        }
        final int room = Math.min(capacity - energy, trainRoom - energy);
        final int accepted = Math.max(0, Math.min(maxReceive, room));
        if (!simulate && accepted > 0) {
            energy += accepted;
            onChanged.run();
        }
        return accepted;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        if (!offering || maxExtract <= 0) {
            return 0;
        }
        final int given = Math.min(maxExtract, energy);
        if (!simulate && given > 0) {
            energy -= given;
            onChanged.run();
        }
        return given;
    }

    @Override
    public int getEnergyStored() {
        return energy;
    }

    @Override
    public int getMaxEnergyStored() {
        return Math.max(capacity, energy);
    }

    @Override
    public boolean canExtract() {
        return offering;
    }

    @Override
    public boolean canReceive() {
        return chargeOpen;
    }
}
