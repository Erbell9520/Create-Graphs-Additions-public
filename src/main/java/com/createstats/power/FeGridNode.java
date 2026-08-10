package com.createstats.power;

import net.neoforged.neoforge.energy.IEnergyStorage;

import javax.annotation.Nullable;

/** [1] */
public interface FeGridNode {

    /** Stable identity, used to remember which nodes were pulled from during a solve. */
    long nodeKey();

    int storedEnergy();

    void setStoredEnergy(int amount);

    int bufferCapacity();

    /** Maximum FE this node moves in one direction per tick. */
    int transferRate();

    /** The block this node is mounted on, as an energy handler, or null when it has none. */
    @Nullable
    IEnergyStorage attachedStorage();
}
