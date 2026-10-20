package mekanism.common.tile.transmitter;

import net.neoforged.neoforge.energy.IEnergyStorage;

public final class FakeUniversalCableHandler implements IEnergyStorage {
    private final boolean pulling;
    private final int networkBuffer;
    private final int networkCapacity;

    public FakeUniversalCableHandler(boolean pulling, int networkBuffer, int networkCapacity) {
        this.pulling = pulling;
        this.networkBuffer = networkBuffer;
        this.networkCapacity = networkCapacity;
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        return pulling ? Math.min(maxReceive, networkCapacity - networkBuffer) : 0;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        return pulling ? 0 : Math.min(maxExtract, networkBuffer);
    }

    @Override
    public int getEnergyStored() {
        return networkBuffer;
    }

    @Override
    public int getMaxEnergyStored() {
        return networkCapacity;
    }

    @Override
    public boolean canExtract() {
        return true;
    }

    @Override
    public boolean canReceive() {
        return true;
    }
}
