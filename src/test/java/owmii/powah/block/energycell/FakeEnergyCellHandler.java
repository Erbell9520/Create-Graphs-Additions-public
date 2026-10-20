package owmii.powah.block.energycell;

import net.neoforged.neoforge.energy.IEnergyStorage;

public final class FakeEnergyCellHandler implements IEnergyStorage {
    private int stored;
    private final int capacity;
    private final int rate;

    public FakeEnergyCellHandler(int stored, int capacity, int rate) {
        this.stored = stored;
        this.capacity = capacity;
        this.rate = rate;
    }

    public static FakeEnergyCellHandler unresolved() {
        return new FakeEnergyCellHandler(0, 0, 0);
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        final int accepted = Math.min(Math.min(maxReceive, rate), capacity - stored);
        if (accepted <= 0) {
            return 0;
        }
        if (!simulate) {
            stored += accepted;
        }
        return accepted;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        final int given = Math.min(Math.min(maxExtract, rate), stored);
        if (given <= 0) {
            return 0;
        }
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
        return true;
    }

    @Override
    public boolean canReceive() {
        return true;
    }
}
