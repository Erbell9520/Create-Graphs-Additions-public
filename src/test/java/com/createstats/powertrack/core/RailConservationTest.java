package com.createstats.powertrack.core;

import com.createstats.power.FeGridNode;
import com.createstats.power.FeGridSolver;
import com.createstats.power.PortableFeBuffer;
import com.createstats.power.PortableFeDirectionLock;
import com.createstats.power.TrainBankLedger;
import com.createstats.powertrack.core.RailTick.CarIn;
import com.createstats.powertrack.core.RailTick.FeederIn;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RailConservationTest {
    private static final int BASE = 8_192;
    private static final int CEILING = RailPower.sectionCeiling(BASE);
    private static final int SHOE = RailPower.collectorRate(BASE);
    private static final int DIAL = 8_192;
    private static final int CONDUIT = 16_000;
    private static final int BANK = 1_024_000;

    private static final class Conduit implements FeGridNode {
        final long key;
        int energy;

        Conduit(long key) {
            this.key = key;
        }

        @Override
        public long nodeKey() {
            return key;
        }

        @Override
        public int storedEnergy() {
            return energy;
        }

        @Override
        public void setStoredEnergy(int amount) {
            energy = Math.max(0, Math.min(amount, CONDUIT));
        }

        @Override
        public int bufferCapacity() {
            return CONDUIT;
        }

        @Override
        public int transferRate() {
            return 2 * BASE;
        }

        @Override
        public IEnergyStorage attachedStorage() {
            return null;
        }
    }

    private record Member(long key, IEnergyStorage handler, int rate) implements FeGridNode {
        @Override
        public long nodeKey() {
            return key;
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
            return rate;
        }

        @Override
        public IEnergyStorage attachedStorage() {
            return handler;
        }
    }

    private static final class Source implements IEnergyStorage {
        long given;

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            final int out = Math.max(0, maxExtract);
            if (!simulate) {
                given += out;
            }
            return out;
        }

        @Override
        public int getEnergyStored() {
            return Integer.MAX_VALUE / 2;
        }

        @Override
        public int getMaxEnergyStored() {
            return Integer.MAX_VALUE / 2;
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

    private static final class Tank implements IEnergyStorage {
        int energy;

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            final int taken = Math.max(0, Math.min(maxReceive, BANK - energy));
            if (!simulate) {
                energy += taken;
            }
            return taken;
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
            return BANK;
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

    private static final class Feeder {
        final FeederPlan.Mode mode;
        final PortableFeBuffer buffer = new PortableFeBuffer(() -> {
        });
        final int floor;
        final int dial;
        FeederPlan.Plan plan;
        boolean holding;
        boolean auto = true;

        Feeder(FeederPlan.Mode mode, int floor) {
            this(mode, floor, DIAL);
        }

        Feeder(FeederPlan.Mode mode, int floor, int dial) {
            this.mode = mode;
            this.floor = floor;
            this.dial = dial;
        }

        void step(long stored, long capacity) {
            plan = FeederPlan.plan(new FeederPlan.Input(mode, true, false, dial, buffer.energy(), stored, capacity,
                    floor, holding, auto));
            holding = plan.floorHolding();
            auto = plan.autoSupplying();
            buffer.configure(dial, plan.chargeOpen(), plan.offersToGrid(), FeederPlan.bufferCapacity(dial));
        }

        SectionExchange.Feeder onRail(long grid) {
            return new SectionExchange.Feeder(grid, plan.role(), plan.railAvailable(), plan.railRoom(),
                    plan.trainFloorPct());
        }

        void apply(int delta) {
            if (delta > 0) {
                buffer.add(delta);
            } else if (delta < 0) {
                buffer.remove(-delta);
            }
        }
    }

    private static int[] empty(int n) {
        return new int[n];
    }

    private static int[] full(int n) {
        final int[] a = new int[n];
        java.util.Arrays.fill(a, BANK);
        return a;
    }

    private static int[] shoesAt(int tick) {
        final int t = tick % 138;
        if (t < 60) {
            return new int[]{0, 0};
        }
        if (t < 68) {
            return new int[]{-1, -1};
        }
        if (t < 70) {
            return new int[]{1, 0};
        }
        if (t < 130) {
            return new int[]{1, 1};
        }
        return new int[]{-1, -1};
    }

    private record Run(long generatorGave, long inTrain, long inBank, long inWire, long inBuffers,
                       int peakCharge, int peakDischarge) {
    }

    private static Run powerTrain(int ticks, FeederPlan.Mode farMode, int farFloor) {
        final Source generator = new Source();
        final Conduit wireA = new Conduit(1L);
        final Feeder home = new Feeder(FeederPlan.Mode.SUPPLY, 0);
        final List<FeGridNode> gridA = List.of(new Member(2L, generator, 4 * DIAL), wireA,
                new Member(3L, home.buffer, DIAL));

        final Tank bank = new Tank();
        final Conduit wireB = new Conduit(11L);
        final Feeder far = new Feeder(farMode, farFloor);
        final List<FeGridNode> gridB = List.of(new Member(12L, bank, 4 * DIAL), wireB,
                new Member(13L, far.buffer, DIAL));

        final TrainBankLedger train = new TrainBankLedger(empty(2), full(2));
        final PortableFeDirectionLock lock = new PortableFeDirectionLock();
        int peakCharge = 0;
        int peakDischarge = 0;
        home.step(0, 0);
        far.step(bank.energy, BANK);
        for (int tick = 0; tick < ticks; tick++) {
            final FeGridSolver.Result a = FeGridSolver.solve(gridA, tick, null, true, FeGridSolver.NO_CEILING);
            final FeGridSolver.Result b = FeGridSolver.solve(gridB, tick, null, true, FeGridSolver.NO_CEILING);
            assertEquals(0, a.spilled(), "tick " + tick + " grid A spilled FE");
            assertEquals(0, b.spilled(), "tick " + tick + " grid B spilled FE");
            home.step(0, 0);
            far.step(bank.energy, BANK);

            final RailTick.Result r = RailTick.run(
                    List.of(new FeederIn(0, home.onRail(100L)), new FeederIn(1, far.onRail(200L))),
                    List.of(new CarIn(9L, shoesAt(tick), train.stored(), train.capacity(), true, true)),
                    2, CEILING, SHOE, lock, tick);
            home.apply(r.feederDelta()[0]);
            far.apply(r.feederDelta()[1]);
            final int delta = r.carDelta()[0];
            if (delta > 0) {
                assertEquals(delta, train.fill(delta), "the plan promised room the banks did not have");
                peakCharge = Math.max(peakCharge, delta);
            } else if (delta < 0) {
                assertEquals(-delta, train.drain(-delta), "the plan promised charge the banks did not have");
                peakDischarge = Math.max(peakDischarge, -delta);
            }
        }
        return new Run(generator.given, train.stored(), bank.energy, (long) wireA.energy + wireB.energy,
                (long) home.buffer.energy() + far.buffer.energy(), peakCharge, peakDischarge);
    }

    @Test
    @DisplayName("the power train: every FE the home generator gave is in the train, the far bank, the wire or a feeder")
    void thePowerTrainConserves() {
        final Run r = powerTrain(2_000, FeederPlan.Mode.COLLECT, 0);
        assertEquals(r.generatorGave(), r.inTrain() + r.inBank() + r.inWire() + r.inBuffers(),
                "FE was made or lost between the generator and the far bank");
        assertTrue(r.inBank() > 0, "the far station never received anything");
        assertEquals(2 * SHOE, r.peakCharge(), "two shoes charge at two shoes' rating");
        assertEquals(2 * SHOE, r.peakDischarge());
    }

    @Test
    @DisplayName("an Auto feeder at the far end collects while its bank is low and the books still balance")
    void anAutoFarEndConserves() {
        final Run r = powerTrain(3_000, FeederPlan.Mode.AUTO, 50);
        assertEquals(r.generatorGave(), r.inTrain() + r.inBank() + r.inWire() + r.inBuffers());
        assertTrue(r.inBank() > 0);
    }

    @Test
    @DisplayName("a feeder whose circuit takes nothing collects nothing: the pull is sized by the room")
    void aFullFarStationTakesNothing() {
        final Source generator = new Source();
        final Feeder home = new Feeder(FeederPlan.Mode.SUPPLY, 0);
        final List<FeGridNode> gridA = List.of(new Member(2L, generator, 4 * DIAL), new Member(3L, home.buffer, DIAL));
        final Feeder far = new Feeder(FeederPlan.Mode.COLLECT, 0);
        final TrainBankLedger train = new TrainBankLedger(full(2), full(2));
        final PortableFeDirectionLock lock = new PortableFeDirectionLock();
        for (int tick = 0; tick < 50; tick++) {
            FeGridSolver.solve(gridA, tick, null, true, FeGridSolver.NO_CEILING);
            home.step(0, 0);

            far.step(0, 0);
            final RailTick.Result r = RailTick.run(List.of(new FeederIn(1, far.onRail(200L))),
                    List.of(new CarIn(9L, new int[]{1, 1}, train.stored(), train.capacity(), true, true)),
                    2, CEILING, SHOE, lock, tick);
            far.apply(r.feederDelta()[0]);
            train.drain(-Math.min(0, r.carDelta()[0]));
        }
        assertEquals(FeederPlan.bufferCapacity(DIAL), far.buffer.energy(), "the buffer fills once");
        assertEquals(2L * BANK - FeederPlan.bufferCapacity(DIAL), train.stored(),
                "and the train gives nothing more once there is nowhere for it to go");
    }

    private static int[] chargeUnder(int dial, int ticks) {
        final Source generator = new Source();
        final Feeder home = new Feeder(FeederPlan.Mode.SUPPLY, 0, dial);
        final List<FeGridNode> grid = List.of(new Member(2L, generator, 4 * DIAL), new Conduit(1L),
                new Member(3L, home.buffer, dial));
        final TrainBankLedger train = new TrainBankLedger(empty(2), full(2));
        final PortableFeDirectionLock lock = new PortableFeDirectionLock();
        final int[] took = new int[ticks];
        home.step(0, 0);
        for (int tick = 0; tick < ticks; tick++) {
            FeGridSolver.solve(grid, tick, null, true, FeGridSolver.NO_CEILING);
            home.step(0, 0);
            final RailTick.Result r = RailTick.run(List.of(new FeederIn(0, home.onRail(100L))),
                    List.of(new CarIn(9L, new int[]{0, 0}, train.stored(), train.capacity(), true, true)),
                    1, CEILING, SHOE, lock, tick);
            home.apply(r.feederDelta()[0]);
            took[tick] = train.fill(Math.max(0, r.carDelta()[0]));
        }
        return took;
    }

    @Test
    @DisplayName("a feeder hands a car its whole draw every tick, up to the dial, not half the dial (KI-144)")
    void aHeavyTrainGetsTheWholeDial() {
        final int draw = 2 * SHOE;

        for (int dial : new int[]{draw, draw + draw / 2, 2 * draw, draw / 2}) {
            final int[] took = chargeUnder(dial, 40);
            for (int tick = 0; tick < took.length; tick++) {
                assertEquals(Math.min(draw, dial), took[tick],
                        "dial " + dial + ", tick " + tick + ": the buffer was not refilled for the rail");
            }
        }
    }
}
