package com.createstats.power;

import net.neoforged.neoforge.energy.IEnergyStorage;

// per run, not per grid. the grid ceiling is the surface cable rule
public final class IndoorCableRunBudget {
    private final int ratePerTick;

    private long tick = Long.MIN_VALUE;

    private int intakeLeft;
    private int outflowLeft;

    public IndoorCableRunBudget(int ratePerTick) {
        this.ratePerTick = Math.max(0, ratePerTick);
    }

    public int ratePerTick() {
        return ratePerTick;
    }

    private void roll(long now) {
        if (now != tick) {
            tick = now;
            intakeLeft = ratePerTick;
            outflowLeft = ratePerTick;
        }
    }

    public int intakeRemaining(long now) {
        roll(now);
        return intakeLeft;
    }

    public int outflowRemaining(long now) {
        roll(now);
        return outflowLeft;
    }

    public void spendIntake(long now, int fe) {
        if (fe > 0) {
            intakeLeft = Math.max(0, intakeRemaining(now) - fe);
        }
    }

    public void spendOutflow(long now, int fe) {
        if (fe > 0) {
            outflowLeft = Math.max(0, outflowRemaining(now) - fe);
        }
    }

    public record Throttled(IEnergyStorage inner, IndoorCableRunBudget budget,
                            java.util.function.LongSupplier clock) implements IEnergyStorage {
        public static Throttled of(IEnergyStorage inner, IndoorCableRunBudget budget,
                                   net.minecraft.world.level.Level level) {
            return new Throttled(inner, budget, level::getGameTime);
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            final long now = clock.getAsLong();
            final int moved = inner.receiveEnergy(
                    Math.min(maxReceive, budget.outflowRemaining(now)), simulate);
            if (!simulate) {
                budget.spendOutflow(now, moved);
            }
            return moved;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            final long now = clock.getAsLong();
            final int moved = inner.extractEnergy(
                    Math.min(maxExtract, budget.intakeRemaining(now)), simulate);
            if (!simulate) {
                budget.spendIntake(now, moved);
            }
            return moved;
        }

        @Override
        public int getEnergyStored() {
            return inner.getEnergyStored();
        }

        @Override
        public int getMaxEnergyStored() {
            return inner.getMaxEnergyStored();
        }

        @Override
        public boolean canExtract() {
            return inner.canExtract();
        }

        @Override
        public boolean canReceive() {
            return inner.canReceive();
        }
    }
}
