package com.createstats.power;

import net.neoforged.neoforge.energy.IEnergyStorage;

import javax.annotation.Nullable;

public interface FeGridNode {
    long nodeKey();

    int storedEnergy();

    void setStoredEnergy(int amount);

    int bufferCapacity();

    int transferRate();

    @Nullable
    IEnergyStorage attachedStorage();

    default int deliveryAllowance() {
        return FeGridSolver.NO_CEILING;
    }

    default void bookDelivery(int fe) {
    }
}
