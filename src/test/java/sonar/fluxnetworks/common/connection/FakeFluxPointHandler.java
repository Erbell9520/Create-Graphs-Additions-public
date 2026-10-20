package sonar.fluxnetworks.common.connection;

import net.neoforged.neoforge.energy.IEnergyStorage;

public final class FakeFluxPointHandler implements IEnergyStorage {
    private final long networkCharge;

    public FakeFluxPointHandler(long networkCharge) {
        this.networkCharge = networkCharge;
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        return maxReceive;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        return 0;
    }

    @Override
    public int getEnergyStored() {
        return (int) Math.min(Integer.MAX_VALUE, networkCharge);
    }

    @Override
    public int getMaxEnergyStored() {
        return Integer.MAX_VALUE;
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
