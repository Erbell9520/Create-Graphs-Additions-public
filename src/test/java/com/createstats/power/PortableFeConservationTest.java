package com.createstats.power;

import com.createstats.power.PortableFeTransfer.Mode;
import com.createstats.power.PortableFeTransfer.Reason;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PortableFeConservationTest {
    private static final int DIAL = 32_768;
    private static final int WIRE = 8_192;

    private static final int CONDUIT = 16_000;
    private static final int BANK = 1_024_000;
    private static final int TICKS = 40;

    private static final int WARM = 5;

    private static final int INTERFACE_SLOT = 0;

    private static final class Conduit implements FeGridNode {
        int energy;

        @Override
        public long nodeKey() {
            return 1L;
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
            return 2 * WIRE;
        }

        @Override
        public IEnergyStorage attachedStorage() {
            return null;
        }
    }

    private static final class Member implements FeGridNode {
        private final long key;
        private final IEnergyStorage handler;
        private final int rate;
        private final int slot;
        private final int wire;
        private int spent;

        Member(long key, IEnergyStorage handler, int rate, int slot, int wire) {
            this.key = key;
            this.handler = handler;
            this.rate = rate;
            this.slot = slot;
            this.wire = wire;
        }

        void newTick() {
            spent = 0;
        }

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
            return slot;
        }

        @Override
        public int transferRate() {
            return rate;
        }

        @Override
        public IEnergyStorage attachedStorage() {
            return handler;
        }

        @Override
        public int deliveryAllowance() {
            return wire >= FeGridSolver.NO_CEILING ? wire : Math.max(0, wire - spent);
        }

        @Override
        public void bookDelivery(int fe) {
            spent += fe;
        }
    }

    private static final class Tank implements IEnergyStorage {
        int energy;

        Tank(int energy) {
            this.energy = energy;
        }

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

    private static final class Machine implements IEnergyStorage {
        final int appetite;
        long taken;

        Machine(int appetite) {
            this.appetite = appetite;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            final int in = Math.max(0, Math.min(maxReceive, appetite));
            if (!simulate) {
                taken += in;
            }
            return in;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return 0;
        }

        @Override
        public int getMaxEnergyStored() {
            return appetite;
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

    private static final class Interface {
        final Mode mode;
        final PortableFeBuffer buffer = new PortableFeBuffer(() -> {
        });
        final TrainBankLedger train;

        Interface(Mode mode, int[] cells) {
            this.mode = mode;
            final int[] caps = new int[cells.length];
            java.util.Arrays.fill(caps, BANK);
            this.train = new TrainBankLedger(cells, caps);
        }

        void step() {
            final PortableFeTransfer.Plan plan = PortableFeTransfer.plan(new PortableFeTransfer.Input(
                    mode, true, false, false, DIAL, buffer.energy(), train.stored(), train.capacity(),
                    0L, 0L, 0, false));
            if (plan.trainDelivery() > 0) {
                buffer.remove(train.fill(plan.trainDelivery()));
            } else if (plan.trainPull() > 0) {
                buffer.add(train.drain(plan.trainPull()));
            }
            buffer.configure(DIAL, mode == Mode.CHARGE && plan.reason() == Reason.READY,
                    mode == Mode.DRAW && buffer.energy() > 0, train.room());
        }
    }

    private static void run(List<? extends FeGridNode> nodes, Interface ppi, Runnable eachTick) {
        ppi.step();
        for (int tick = 0; tick < TICKS; tick++) {
            for (FeGridNode node : nodes) {
                if (node instanceof Member member) {
                    member.newTick();
                }
            }
            FeGridSolver.solve(nodes, tick, null, true, FeGridSolver.NO_CEILING);
            ppi.step();
            eachTick.run();
        }
    }

    private static final int FULL = 4 * BANK;

    private record DrawRun(long trainOut, long bankIn, int conduit, int buffer, int lastTrainOut, int lastBankIn) {
    }

    private static DrawRun draw(int interfaceSlot) {
        final Interface ppi = new Interface(Mode.DRAW, new int[]{BANK, BANK, BANK, BANK});
        final Tank bank = new Tank(0);
        final Conduit conduit = new Conduit();
        final List<FeGridNode> nodes = List.of(
                new Member(2L, ppi.buffer, DIAL, interfaceSlot, FeGridSolver.NO_CEILING),
                conduit,
                new Member(3L, bank, WIRE, 0, FeGridSolver.NO_CEILING));
        final int[] last = new int[2];
        final int[] prev = {ppi.train.stored(), bank.energy};
        run(nodes, ppi, () -> {
            last[0] = prev[0] - ppi.train.stored();
            last[1] = bank.energy - prev[1];
            prev[0] = ppi.train.stored();
            prev[1] = bank.energy;
        });
        return new DrawRun(FULL - ppi.train.stored(), bank.energy, conduit.energy, ppi.buffer.energy(),
                last[0], last[1]);
    }

    @Test
    @DisplayName("KI-33: drawing above the wire, every FE that leaves the train arrives somewhere")
    void drawAboveTheWireIsConserved() {
        final DrawRun r = draw(INTERFACE_SLOT);
        assertEquals(r.trainOut(), r.bankIn() + r.conduit() + r.buffer(), "FE left the train and vanished");
        assertEquals(WIRE, r.lastTrainOut(), "the train should empty at the wire's rate, not the dial's");
        assertEquals(WIRE, r.lastBankIn());
    }

    @Test
    @DisplayName("KI-33: a pool slot the node cannot hold reproduces the report, 32,768 out and 8,192 in")
    void slotTheNodeCannotHoldDeletesTheRest() {
        final DrawRun r = draw(DIAL);
        assertEquals(DIAL, r.lastTrainOut());
        assertEquals(WIRE, r.lastBankIn());
    }

    @Test
    @DisplayName("KI-34: charging above the wire at a generator-only station, the generator pays the wire's rate")
    void chargeAboveTheWireIsConserved() {
        final Interface ppi = new Interface(Mode.CHARGE, new int[]{0, 0, 0, 0});
        final Source generator = new Source();
        final Conduit conduit = new Conduit();
        final List<FeGridNode> nodes = List.of(
                new Member(4L, generator, 4 * DIAL, 0, FeGridSolver.NO_CEILING),
                conduit,

                new Member(2L, ppi.buffer, DIAL, INTERFACE_SLOT, WIRE));
        final long[] prev = {0L};
        final long[] lastGiven = {0L};
        run(nodes, ppi, () -> {
            lastGiven[0] = generator.given - prev[0];
            prev[0] = generator.given;
        });
        assertEquals(generator.given, (long) ppi.train.stored() + conduit.energy + ppi.buffer.energy(),
                "the generator paid for FE the train never received");
        assertEquals(WIRE, lastGiven[0]);
        assertEquals((long) WIRE * TICKS, (long) ppi.train.stored());
    }

    @Test
    @DisplayName("KI-34: a machine hungrier than its wire costs its generator only what the wire delivers")
    void appetiteAboveTheWireIsNotCollected() {
        final Source generator = new Source();
        final Machine machine = new Machine(DIAL);
        final Conduit conduit = new Conduit();
        final Member load = new Member(5L, machine, DIAL, 0, WIRE);
        final Member gen = new Member(4L, generator, 4 * DIAL, 0, FeGridSolver.NO_CEILING);
        final List<FeGridNode> nodes = List.of(gen, conduit, load);
        for (int tick = 0; tick < TICKS; tick++) {
            load.newTick();
            final FeGridSolver.Result r = FeGridSolver.solve(nodes, tick, null, true, FeGridSolver.NO_CEILING);
            assertEquals(0, r.spilled(), "tick " + tick + " dropped FE");
            assertEquals(DIAL, r.demand(), "demand is still the appetite a readout should show");
        }
        assertEquals(generator.given, machine.taken + conduit.energy);
        assertEquals((long) WIRE * TICKS, machine.taken);
    }

    @Test
    @DisplayName("KI-34: with a bank, Cover discharges what the wire delivers and nothing is handed back")
    void coverIsSizedByTheWire() {
        final Tank bank = new Tank(BANK);
        final Machine machine = new Machine(DIAL);
        final Conduit conduit = new Conduit();
        final Member load = new Member(5L, machine, DIAL, 0, WIRE);
        final List<FeGridNode> nodes = List.of(new Member(3L, bank, DIAL, 0, FeGridSolver.NO_CEILING),
                conduit, load);
        for (int tick = 0; tick < TICKS; tick++) {
            load.newTick();
            final FeGridSolver.Result r = FeGridSolver.solve(nodes, tick, null, true, FeGridSolver.NO_CEILING);
            if (tick >= WARM) {
                assertEquals(0, r.charged(), "tick " + tick + ": a bank discharged and was refilled (FM-13)");
                assertEquals(WIRE, r.delivered());
            }
        }
        assertEquals((long) BANK - bank.energy, machine.taken + conduit.energy);
    }
}
