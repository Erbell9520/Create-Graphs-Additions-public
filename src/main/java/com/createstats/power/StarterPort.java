package com.createstats.power;

import java.util.function.LongSupplier;

import net.neoforged.neoforge.energy.IEnergyStorage;

public final class StarterPort implements IEnergyStorage {
    public interface View extends IEnergyStorage {
    }

    private final LongSupplier clock;
    private final Runnable onChanged;
    private int stored;
    private boolean firing;
    private long firingSince;

    private final boolean[] chargeOpen = new boolean[2];
    private final IEnergyStorage[] faces = {new Face(0), new Face(1)};

    private long tick = Long.MIN_VALUE;
    private int outLeft;
    private int inLeft;
    private int movedThisTick;
    private long dissipatedThisBurst;
    private int lastDissipated;

    private long askTick = Long.MIN_VALUE;
    private int asked;
    private int got;

    public StarterPort(LongSupplier clock, Runnable onChanged) {
        this.clock = clock;
        this.onChanged = onChanged;
    }

    public void setFiring(boolean firing, long since) {
        if (this.firing != firing || this.firingSince != since) {
            if (this.firing && tick != Long.MIN_VALUE) {
                burn();
            }
            if (firing) {
                dissipatedThisBurst = 0;
            }
            this.firing = firing;
            this.firingSince = since;
            this.tick = Long.MIN_VALUE;
        }
    }

    public static final int GLAND = 0;
    public static final int LUG = 1;

    public void setChargeOpen(int face, boolean open) {
        this.chargeOpen[face] = open;
    }

    public void setChargeOpen(boolean open) {
        this.chargeOpen[GLAND] = open;
        this.chargeOpen[LUG] = open;
    }

    public IEnergyStorage face(int face) {
        return faces[face];
    }

    public static boolean chargeGate(boolean network, boolean managed, boolean panelHolds, boolean gridShort) {
        return chargeGate(network, managed, panelHolds, gridShort, false);
    }

    public static boolean chargeGate(boolean network, boolean managed, boolean panelHolds, boolean gridShort,
                                     boolean refillFunded) {
        if (!network) {
            return true;
        }
        if (managed && panelHolds) {
            return false;
        }
        if (managed && refillFunded) {
            return true;
        }
        return !gridShort;
    }

    public static boolean managedShort(FeManagerView view) {
        return view != null && view.drawsBursts() && view.unserved() > 0;
    }

    public static boolean feedShort(int gridShortfall, int ownUnserved, int boardIntake, boolean boardShort) {
        if (boardShort) {
            return true;
        }
        return (long) gridShortfall - Math.max(0, ownUnserved) - Math.max(0, boardIntake) > 0;
    }

    public boolean firing() {
        return firing;
    }

    public int stored() {
        return stored;
    }

    public void setStored(int fe) {
        this.stored = Math.max(0, Math.min(CapacitorDischarge.CAPACITY, fe));
    }

    public int movedThisTick() {
        return clock.getAsLong() == tick ? movedThisTick : 0;
    }

    public int unservedAtLastSolve() {
        return Math.max(0, asked - got);
    }

    public int allowanceLeft() {
        roll();
        return outLeft;
    }

    public long dissipatedThisBurst() {
        return dissipatedThisBurst;
    }

    public int lastDissipated() {
        return lastDissipated;
    }

    public void settle() {
        roll();
    }

    private void burn() {
        final int n = Math.max(0, Math.min(outLeft, stored));
        stored -= n;
        outLeft = 0;
        lastDissipated = n;
        dissipatedThisBurst += n;
        if (n > 0) {
            onChanged.run();
        }
    }

    private void roll() {
        final long now = clock.getAsLong();
        if (now != tick) {
            if (firing && tick != Long.MIN_VALUE) {
                burn();
            }
            tick = now;
            outLeft = firing ? CapacitorDischarge.allowanceAt((int) Math.min(Integer.MAX_VALUE, now - firingSince)) : 0;
            inLeft = CapacitorDischarge.CHARGE_FE_PER_TICK;
            movedThisTick = 0;
        }
    }

    @Override
    public int receiveEnergy(int max, boolean simulate) {
        return receive(chargeOpen[GLAND] || chargeOpen[LUG], max, simulate);
    }

    private int receive(boolean open, int max, boolean simulate) {
        roll();

        if (askTick != tick) {
            askTick = tick;
            asked = 0;
            got = 0;
        }
        if (firing || !open || max <= 0) {
            return 0;
        }
        final int n = Math.max(0, Math.min(max, Math.min(inLeft, CapacitorDischarge.CAPACITY - stored)));
        if (simulate) {
            asked = Math.max(asked, n);
        } else if (n > 0) {
            stored += n;
            inLeft -= n;
            movedThisTick += n;
            got += n;
            asked = Math.max(asked, got);
            onChanged.run();
        }
        return n;
    }

    @Override
    public int extractEnergy(int max, boolean simulate) {
        if (!firing || max <= 0) {
            return 0;
        }
        roll();
        final int n = Math.min(max, Math.min(outLeft, stored));
        if (n > 0 && !simulate) {
            stored -= n;
            outLeft -= n;
            movedThisTick -= n;
            onChanged.run();
        }
        return Math.max(0, n);
    }

    @Override
    public int getEnergyStored() {
        return stored;
    }

    @Override
    public int getMaxEnergyStored() {
        return CapacitorDischarge.CAPACITY;
    }

    @Override
    public boolean canExtract() {
        return firing;
    }

    @Override
    public boolean canReceive() {
        return !firing;
    }

    private final class Face implements View {
        private final int face;

        private Face(int face) {
            this.face = face;
        }

        @Override
        public int receiveEnergy(int max, boolean simulate) {
            return receive(chargeOpen[face], max, simulate);
        }

        @Override
        public int extractEnergy(int max, boolean simulate) {
            return StarterPort.this.extractEnergy(max, simulate);
        }

        @Override
        public int getEnergyStored() {
            return StarterPort.this.getEnergyStored();
        }

        @Override
        public int getMaxEnergyStored() {
            return StarterPort.this.getMaxEnergyStored();
        }

        @Override
        public boolean canExtract() {
            return StarterPort.this.canExtract();
        }

        @Override
        public boolean canReceive() {
            return StarterPort.this.canReceive();
        }
    }
}
