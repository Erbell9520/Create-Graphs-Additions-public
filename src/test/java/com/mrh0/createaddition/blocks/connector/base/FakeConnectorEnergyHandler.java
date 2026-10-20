package com.mrh0.createaddition.blocks.connector.base;

import net.neoforged.neoforge.energy.IEnergyStorage;

public final class FakeConnectorEnergyHandler implements IEnergyStorage {
    public int networkBuff;

    private final boolean push;
    private final int rate;

    public FakeConnectorEnergyHandler(boolean push, int rate, int networkBuff) {
        this.push = push;
        this.rate = rate;
        this.networkBuff = networkBuff;
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        if (push) {
            return 0;
        }
        final int accepted = Math.max(0, Math.min(rate, maxReceive));
        if (!simulate) {
            networkBuff += accepted;
        }
        return accepted;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        if (!push) {
            return 0;
        }
        final int given = Math.max(0, Math.min(networkBuff, Math.min(rate, maxExtract)));
        if (!simulate) {
            networkBuff -= given;
        }
        return given;
    }

    @Override
    public int getEnergyStored() {
        return Math.min(rate, networkBuff);
    }

    @Override
    public int getMaxEnergyStored() {
        return rate;
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
