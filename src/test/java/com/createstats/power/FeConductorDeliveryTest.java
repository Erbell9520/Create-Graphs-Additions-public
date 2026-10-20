package com.createstats.power;

import net.neoforged.neoforge.energy.IEnergyStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeConductorDeliveryTest {
    private static final int DUCT = 16_384;
    private static final int CABLE = 4_096;
    private static final int NONE = FeGridSolver.NO_CEILING;
    private static final long TICK = 40L;

    private static class Box implements IEnergyStorage {
        int energy;
        final int capacity;

        Box(int energy, int capacity) {
            this.energy = energy;
            this.capacity = capacity;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            final int taken = Math.max(0, Math.min(maxReceive, capacity - energy));
            if (!simulate) {
                energy += taken;
            }
            return taken;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return energy;
        }

        @Override
        public int getMaxEnergyStored() {
            return capacity;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }

    private static final class Tank extends Box {
        Tank(int energy, int capacity) {
            super(energy, capacity);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            final int given = Math.max(0, Math.min(maxExtract, energy));
            if (!simulate) {
                energy -= given;
            }
            return given;
        }

        @Override
        public boolean canExtract() {
            return true;
        }
    }

    private static final class Source implements IEnergyStorage {
        int energy;

        Source(int energy) {
            this.energy = energy;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            final int given = Math.max(0, Math.min(maxExtract, energy));
            if (!simulate) {
                energy -= given;
            }
            return given;
        }

        @Override
        public int getEnergyStored() {
            return energy;
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
            return false;
        }
    }

    private static final class Member implements FeGridNode {
        private final long key;
        private final IEnergyStorage attached;
        private final int rate;
        private final FePathBudget budget;
        private int stored;
        private final int buffer;

        Member(long key, IEnergyStorage attached, int rate, FePathBudget budget, int buffer) {
            this.key = key;
            this.attached = attached;
            this.rate = rate;
            this.budget = budget;
            this.buffer = buffer;
        }

        @Override
        public long nodeKey() {
            return key;
        }

        @Override
        public int storedEnergy() {
            return stored;
        }

        @Override
        public void setStoredEnergy(int amount) {
            stored = Math.max(0, Math.min(amount, buffer));
        }

        @Override
        public int bufferCapacity() {
            return buffer;
        }

        @Override
        public int transferRate() {
            return rate;
        }

        @Override
        public IEnergyStorage attachedStorage() {
            return attached;
        }

        @Override
        public int deliveryAllowance() {
            return budget == null ? FeGridSolver.NO_CEILING : budget.remaining(key, TICK);
        }

        @Override
        public void bookDelivery(int fe) {
            if (budget != null) {
                budget.spend(key, fe, TICK);
            }
        }
    }

    private static Member plain(long key, IEnergyStorage attached, int rate) {
        return new Member(key, attached, rate, null, 0);
    }

    private static long key(int index) {
        return 5_000L + index;
    }

    private static long[] keys(int n) {
        final long[] out = new long[n];
        for (int i = 0; i < n; i++) {
            out[i] = key(i);
        }
        return out;
    }

    private static boolean[] suppliesAt(int n, int... at) {
        final boolean[] out = new boolean[n];
        for (int one : at) {
            out[one] = true;
        }
        return out;
    }

    private static FePathBudget spurOffATrunk() {
        final int[] parent = {-1, 0, 1, 2, 3, 3};
        final int[] rate = {NONE, DUCT, DUCT, CABLE, CABLE, CABLE};
        return new FePathBudget(keys(6), parent, rate, suppliesAt(6, 0));
    }

    @Test
    @DisplayName("two machines on one Surface Cable share it rather than getting one each")
    void oneConductorIsSharedByTheMachinesOnIt() {
        final FePathBudget budget = spurOffATrunk();
        final Box first = new Box(0, 100_000);
        final Box second = new Box(0, 100_000);
        final List<FeGridNode> loads = List.of(
                new Member(key(4), first, DUCT, budget, 0),
                new Member(key(5), second, DUCT, budget, 0));

        final FeGridSolver.BranchResult served = FeGridSolver.serveFlat(loads, 40_000, 0);

        assertEquals(CABLE, served.delivered(),
                "the spur carries its rating, not the sum of what the machines on it can take");
        assertEquals(CABLE, first.energy + second.energy);
    }

    @Test
    @DisplayName("without a conductor behind them the same two machines take everything, as before")
    void aNodeWithNoWiringIsUnconstrained() {
        final Box first = new Box(0, 100_000);
        final Box second = new Box(0, 100_000);
        final List<FeGridNode> loads = List.of(plain(key(4), first, DUCT), plain(key(5), second, DUCT));

        final FeGridSolver.BranchResult served = FeGridSolver.serveFlat(loads, 40_000, 0);

        assertEquals(2 * DUCT, served.delivered());
    }

    private static int servedThroughATwoPassMopUp(int pool, Box hungry) {
        final int[] parent = {-1, 0, 0};
        final int[] rate = {NONE, 6_000, 6_000};
        final FePathBudget budget = new FePathBudget(keys(3), parent, rate, suppliesAt(3, 0));
        final List<FeGridNode> loads = List.of(
                new Member(key(1), hungry, DUCT, budget, 0),
                new Member(key(2), new Box(500, 500), DUCT, budget, 0));
        return FeGridSolver.serveFlat(loads, pool, 0).delivered();
    }

    @Test
    @DisplayName("the two delivery passes together reach the whole allowance, not half of it")
    void bothPassesShareOneAllowance() {
        final Box hungry = new Box(0, 100_000);
        assertEquals(6_000, servedThroughATwoPassMopUp(6_000, hungry),
                "3,000 here is the same FE charged to one conductor twice");
        assertEquals(6_000, hungry.energy);
    }

    @Test
    @DisplayName("and the mop-up pass cannot go past the allowance the first pass left")
    void theSecondPassIsHeldToo() {
        final Box hungry = new Box(0, 100_000);
        assertEquals(6_000, servedThroughATwoPassMopUp(12_000, hungry),
                "12,000 here is the mop-up ignoring the conductor");
        assertEquals(6_000, hungry.energy);
    }

    @Test
    @DisplayName("a whole solve holds the spur, generator, trunk and all")
    void aFullSolveHoldsTheSpur() {
        final FePathBudget budget = spurOffATrunk();
        final Source fuel = new Source(40_000);
        final Box machine = new Box(0, 100_000);
        final List<FeGridNode> nodes = new ArrayList<>();

        nodes.add(new Member(key(0), fuel, 40_000, budget, 8_000));
        nodes.add(new Member(key(4), machine, DUCT, budget, 8_000));

        final FeGridSolver.Result result = FeGridSolver.solve(nodes, 0);

        assertEquals(CABLE, result.delivered(), "the machine is behind one Surface Cable segment");
        assertEquals(CABLE, machine.energy);
        assertEquals(40_000, fuel.energy + machine.energy
                        + nodes.get(0).storedEnergy() + nodes.get(1).storedEnergy(),
                "and nothing was minted or voided on the way");
    }

    @Test
    @DisplayName("a battery behind the same spur charges at the spur's rate, not the trunk's")
    void theChargePhaseIsHeldToo() {
        // a bank on a thin spur used to charge at the trunk's rate

        final FePathBudget budget = spurOffATrunk();
        final Source fuel = new Source(40_000);
        final Tank bank = new Tank(0, 100_000);
        final List<FeGridNode> nodes = new ArrayList<>();
        nodes.add(new Member(key(0), fuel, 40_000, budget, 0));
        nodes.add(new Member(key(4), bank, DUCT, budget, 0));

        final FeGridSolver.Result result = FeGridSolver.solve(nodes, 0);

        assertEquals(CABLE, result.charged());
        assertEquals(CABLE, bank.energy);
    }

    @Test
    @DisplayName("the allowance refills on the next tick and not before")
    void theAllowanceIsPerTick() {
        final FePathBudget budget = spurOffATrunk();
        final Box machine = new Box(0, 100_000);
        final List<FeGridNode> loads = List.of(new Member(key(4), machine, DUCT, budget, 0));

        FeGridSolver.serveFlat(loads, 40_000, 0);
        assertEquals(CABLE, machine.energy);
        FeGridSolver.serveFlat(loads, 40_000, 0);
        assertEquals(CABLE, machine.energy, "a second serve inside one tick gets nothing");

        assertEquals(CABLE, budget.remaining(key(4), TICK + 1));
    }
}
