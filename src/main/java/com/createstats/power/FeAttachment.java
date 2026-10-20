package com.createstats.power;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

// one node per machine. one handler per cable meant the lamp OR the machine got served, never both
public record FeAttachment(BlockPos machine, IEnergyStorage storage, int rate, @Nullable FeGridNode rateOf)
        implements FeGridMember {
    public FeAttachment(BlockPos machine, IEnergyStorage storage, int rate) {
        this(machine, storage, rate, null);
    }

    public static FeAttachment station(BlockPos machine, IEnergyStorage storage, FeGridNode station) {
        return new FeAttachment(machine, storage, FeGridSolver.NO_CEILING, station);
    }

    public FeAttachment over(IEnergyStorage wrapped, int cap) {
        return new FeAttachment(machine, wrapped, cap, rateOf);
    }

    @Override
    public long nodeKey() {
        return machine.asLong();
    }

    @Override
    public int storedEnergy() {
        return 0;
    }

    @Override
    public void setStoredEnergy(int amount) {
    }

    @Override
    public int bufferCapacity() {
        return 0;
    }

    @Override
    public int transferRate() {
        return rateOf == null ? rate : Math.min(rate, Math.max(0, rateOf.transferRate()));
    }

    @Override
    @Nullable
    public IEnergyStorage attachedStorage() {
        return storage;
    }

    @Override
    public boolean isGone(Level owner) {
        return false;
    }

    @Override
    public void setLastFlow(int flow) {
    }

    @Override
    public void invalidateNetwork() {
    }
}
