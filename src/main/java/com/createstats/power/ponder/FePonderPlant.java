package com.createstats.power.ponder;

import com.createstats.power.FeGridNode;
import com.createstats.power.FeGridSolver;
import com.createstats.power.FeLock;
import com.createstats.power.FePriority;
import com.createstats.power.FeServePolicy;
import com.createstats.power.PduAutoController;
import com.createstats.power.PduMode;
import com.createstats.power.PduReason;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// the block's policy, the model's plant
public final class FePonderPlant {
    private FePonderPlant() {
    }

    public enum Kind {
        MOTOR,

        REPELLER,

        RANGE,

        LAMP
    }

    public record Machine(String name, int tier, int draw, int buffer, int rate, Kind kind, int start) {
        public Machine {
            start = Math.max(0, Math.min(buffer, start));
        }

        public Machine full() {
            return new Machine(name, tier, draw, buffer, rate, kind, buffer);
        }
    }

    public record Tuning(int bufferCapacity, int floorPct, int restorePct, int burstMarginPct, int dwellTicks,
                         int restoreIntervalTicks, int rideHorizonTicks, int supplyReadIntervalTicks, int upkeep,
                         float observedDecay, double fePerRpm, int dynamoBuffer, int dynamoRate) {
        public static Tuning defaults() {
            return new Tuning(512_000, 15, 45, 10, 60, 20, 600, 10, 1024 / 20, 0.98f, 1.40625, 64_000, 4_096);
        }

        public int floorEnergy() {
            return Math.round(bufferCapacity * (floorPct / 100.0f));
        }

        public int restoreEnergy() {
            return Math.round(bufferCapacity * (restorePct / 100.0f));
        }
    }

    public record Setup(List<Machine> machines, int dynamos, int wireCeiling, int feedCeiling, int energy,
                        PduMode mode, Tuning tuning) {
    }

    public static final class Script {
        final int length;
        private final List<int[]> rpm = new ArrayList<>();
        private final List<Object[]> modes = new ArrayList<>();
        private final List<Object[]> locks = new ArrayList<>();
        private final List<int[]> wires = new ArrayList<>();
        private final List<int[]> tiers = new ArrayList<>();

        public Script(int length) {
            this.length = length;
        }

        public Script rpm(int tick, int dynamo, int rpm) {
            this.rpm.add(new int[] {tick, dynamo, rpm});
            return this;
        }

        public Script mode(int tick, PduMode mode) {
            modes.add(new Object[] {tick, mode});
            return this;
        }

        public Script lock(int tick, int machine, FeLock lock) {
            locks.add(new Object[] {tick, machine, lock});
            return this;
        }

        public Script wire(int tick, int machine) {
            wires.add(new int[] {tick, machine});
            return this;
        }

        public Script tier(int tick, int machine, int tier) {
            tiers.add(new int[] {tick, machine, tier});
            return this;
        }

        public int length() {
            return length;
        }
    }

    public record Flip(int tick, int machine, boolean on, PduReason reason) {
    }

    public static final class Recording {
        public final Setup setup;
        public final int length;

        public final int[] energy;

        public final int[] intake;

        public final int[] potential;

        public final int[] generation;

        public final int[] runBudget;
        public final boolean[] surplus;
        public final boolean[] belowFloor;
        public final PduMode[] mode;

        public final int[] servedCount;
        public final int[] shedCount;

        public final int[] demandShown;
        public final int[] servedDemand;

        public final boolean[][] admitted;

        public final boolean[][] working;
        public final PduReason[][] reason;
        public final int[][] fed;
        public final int[][] buffer;

        public final boolean[][] connected;

        public final int[][] tier;

        public final int[][] rpm;
        public final List<Flip> flips = new ArrayList<>();

        Recording(Setup setup, int length) {
            this.setup = setup;
            this.length = length;
            int m = setup.machines().size();
            energy = new int[length];
            intake = new int[length];
            potential = new int[length];
            generation = new int[length];
            runBudget = new int[length];
            surplus = new boolean[length];
            belowFloor = new boolean[length];
            mode = new PduMode[length];
            servedCount = new int[length];
            shedCount = new int[length];
            demandShown = new int[length];
            servedDemand = new int[length];
            admitted = new boolean[m][length];
            working = new boolean[m][length];
            reason = new PduReason[m][length];
            fed = new int[m][length];
            buffer = new int[m][length];
            connected = new boolean[m][length];
            tier = new int[m][length];
            rpm = new int[setup.dynamos()][length];
        }

        public int machines() {
            return setup.machines().size();
        }

        public Machine machine(int i) {
            return setup.machines().get(i);
        }

        public int at(int tick) {
            return Math.max(0, Math.min(length - 1, tick));
        }

        public float fill(int tick) {
            return energy[at(tick)] / (float) setup.tuning().bufferCapacity();
        }

        public int demand() {
            int d = 0;
            for (Machine machine : setup.machines()) d += machine.draw();
            return d;
        }

        public int admittedDemand(int tick) {
            int t = at(tick);
            int d = 0;
            for (int i = 0; i < machines(); i++) if (admitted[i][t]) d += machine(i).draw();
            return d;
        }

        public int flip(int machine, boolean on, int from) {
            for (Flip f : flips) if (f.machine() == machine && f.on() == on && f.tick() >= from) return f.tick();
            return -1;
        }

        public int crossing(int fe, boolean downward, int from) {
            for (int t = Math.max(0, from); t < length; t++) {
                if (downward ? energy[t] <= fe : energy[t] > fe) return t;
            }
            return -1;
        }
    }

    public static Recording run(Setup setup, Script script) {
        final Tuning tune = setup.tuning();
        final int n = setup.machines().size();
        final int length = script.length();
        final Recording rec = new Recording(setup, length);

        final PduAutoController auto = new PduAutoController();
        auto.setTuning(new PduAutoController.Tuning(tune.dwellTicks(), tune.restoreIntervalTicks()));
        final Node[] nodes = new Node[n];
        for (int i = 0; i < n; i++) nodes[i] = new Node(i, setup.machines().get(i));
        final FeLock[] locks = new FeLock[n];
        Arrays.fill(locks, FeLock.NONE);
        final boolean[] connected = new boolean[n];
        final int[] tier = new int[n];
        for (int i = 0; i < n; i++) {
            final int index = i;
            connected[i] = script.wires.stream().noneMatch(w -> w[1] == index);
            tier[i] = setup.machines().get(i).tier();
            nodes[i].tier = tier[i];
        }
        final int[] rpm = new int[setup.dynamos()];
        final int[] dynamoEnergy = new int[setup.dynamos()];

        int energy = Math.max(0, Math.min(tune.bufferCapacity(), setup.energy()));
        PduMode mode = setup.mode();
        int lastIntake = 0;
        int observed = 0;
        int rawGen = 0;
        float smoothGen = -1f;
        boolean[] wasAdmitted = new boolean[n];
        Arrays.fill(wasAdmitted, true);
        float smoothDemand = -1f;

        for (int t = 0; t < length; t++) {
            for (int[] r : script.rpm) if (r[0] == t && r[1] >= 0 && r[1] < rpm.length) rpm[r[1]] = r[2];
            for (Object[] m : script.modes) if ((int) m[0] == t) mode = (PduMode) m[1];
            for (Object[] l : script.locks) if ((int) l[0] == t) locks[(int) l[1]] = (FeLock) l[2];
            for (int[] w : script.wires) if (w[0] == t) connected[w[1]] = true;
            for (int[] r : script.tiers) {
                if (r[0] == t) {
                    tier[r[1]] = r[2];
                    nodes[r[1]].tier = r[2];
                }
            }

            int potential = 0;
            for (int d = 0; d < rpm.length; d++) potential += potentialOf(rpm[d], tune);

            if (t % Math.max(1, tune.supplyReadIntervalTicks()) == 0) rawGen = potential;

            observed = Math.max(lastIntake, Math.round(observed * tune.observedDecay()));
            final int measured = Math.max(rawGen, observed);
            smoothGen = smoothGen < 0f ? measured : smoothGen + (measured - smoothGen) * 0.1f;
            final int gen = Math.max(0, Math.round(smoothGen));

            final int upkeep = Math.min(energy, tune.upkeep());
            energy -= upkeep;

            final int[] fed = new int[n];
            final PduReason[] reasons = new PduReason[n];
            final boolean[] admitted = new boolean[n];
            int budget = Integer.MAX_VALUE;
            int servedCount = 0;
            int shedCount = 0;
            int servedDemand = 0;
            boolean surplus = energy > tune.restoreEnergy();
            boolean belowFloor = energy < tune.floorEnergy();
            if (mode == PduMode.AUTO) {
                final Auto a = autoStep(auto, setup, tune, locks, connected, tier, energy, gen, t);
                budget = a.surplus ? Integer.MAX_VALUE : a.supply.runBudget();
                surplus = a.surplus;
                belowFloor = a.supply.belowFloor();
                int pool = Math.min(energy, setup.wireCeiling());
                for (int i = 0; i < n; i++) {
                    admitted[i] = a.plan.isRunning(i + 1L);
                    reasons[i] = a.plan.reasonFor(i + 1L);
                    if (admitted[i]) {
                        int give = Math.min(pool, Math.min(setup.machines().get(i).draw(), nodes[i].room()));
                        nodes[i].energy += give;
                        pool -= give;
                        fed[i] += give;
                    }
                }
                energy = Math.max(0, energy - sum(fed));

                int total = 0;
                for (int i = 0; i < n; i++) {
                    if (!connected[i] || locks[i] == FeLock.FORCE_OFF) continue;
                    total += setup.machines().get(i).draw();
                    if (admitted[i]) servedCount++;
                }
                for (int i = 0; i < n; i++) {
                    if (connected[i] && locks[i] != FeLock.FORCE_OFF && !admitted[i] && servedCount > 0) shedCount++;
                }
                servedDemand = a.plan.admittedDemand();
                smoothDemand = smoothDemand < 0f ? total : smoothDemand + (total - smoothDemand) * 0.1f;
            } else {
                final List<Node> loads = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    nodes[i].shed = false;
                    if (!connected[i]) {
                        continue;
                    }
                    if (locks[i] == FeLock.FORCE_OFF) {
                        reasons[i] = PduReason.LOCKED_OFF;
                        continue;
                    }
                    loads.add(nodes[i]);
                }
                for (Node node : nodes) node.served = 0;
                final FeGridSolver.BranchResult r = mode == PduMode.MANUAL
                        ? FeGridSolver.serveByPriority(loads, energy, t, null, null, POLICY)
                        : FeGridSolver.serveFlat(loads, energy, t, null, POLICY);
                energy = Math.max(0, energy - r.delivered());
                servedCount = r.served();
                shedCount = r.shed();
                smoothDemand = -1f;
                for (int i = 0; i < n; i++) {
                    fed[i] = nodes[i].served;
                    admitted[i] = connected[i] && locks[i] != FeLock.FORCE_OFF && !nodes[i].shed;
                    if (reasons[i] == null) reasons[i] = nodes[i].shed ? PduReason.TIER_SHED : PduReason.RUNNING;
                }
            }

            for (int i = 0; i < n; i++) {
                final boolean suppressed = connected[i] && (locks[i] == FeLock.FORCE_OFF
                        || (!admitted[i] && locks[i] != FeLock.FORCE_ON));
                rec.working[i][t] = nodes[i].pay(suppressed);
                rec.connected[i][t] = connected[i];
                rec.tier[i][t] = tier[i];
            }

            int made = 0;
            for (int d = 0; d < rpm.length; d++) {
                int p = potentialOf(rpm[d], tune);
                int into = Math.min(p, tune.dynamoBuffer() - dynamoEnergy[d]);
                dynamoEnergy[d] += into;
                made += dynamoEnergy[d];
            }
            final int room = tune.bufferCapacity() - energy;
            int want = Math.min(room, Math.min(made, setup.feedCeiling()));
            final int intake = want;
            for (int d = 0; d < rpm.length && want > 0; d++) {
                int take = Math.min(want, Math.min(dynamoEnergy[d], tune.dynamoRate()));
                dynamoEnergy[d] -= take;
                want -= take;
            }
            energy += intake - want;
            lastIntake = intake - want;

            rec.energy[t] = energy;
            rec.intake[t] = lastIntake;
            rec.potential[t] = potential;
            rec.generation[t] = gen;
            rec.runBudget[t] = budget;
            rec.surplus[t] = surplus;
            rec.belowFloor[t] = belowFloor;
            rec.mode[t] = mode;
            rec.servedCount[t] = servedCount;
            rec.shedCount[t] = shedCount;
            rec.demandShown[t] = smoothDemand < 0f ? 0 : Math.round(smoothDemand);
            rec.servedDemand[t] = servedDemand;
            for (int d = 0; d < rpm.length; d++) rec.rpm[d][t] = rpm[d];
            for (int i = 0; i < n; i++) {
                rec.admitted[i][t] = admitted[i];
                rec.reason[i][t] = reasons[i] == null ? PduReason.RUNNING : reasons[i];
                rec.fed[i][t] = fed[i];
                rec.buffer[i][t] = nodes[i].energy;
                if (t > 0 && admitted[i] != wasAdmitted[i]) rec.flips.add(new Flip(t, i, admitted[i], rec.reason[i][t]));
                wasAdmitted[i] = admitted[i];
            }
        }
        return rec;
    }

    private static int sum(int[] a) {
        int s = 0;
        for (int v : a) s += v;
        return s;
    }

    public static int potentialOf(int rpm, Tuning tune) {
        final double speed = Math.abs(rpm);
        return speed <= 0.01 ? 0 : Math.max(0, (int) Math.floor(speed * tune.fePerRpm()));
    }

    private record Auto(PduAutoController.Plan plan, PduAutoController.Supply supply, boolean surplus) {
    }

    private static Auto autoStep(PduAutoController auto, Setup setup, Tuning tune, FeLock[] locks, boolean[] connected,
                                 int[] tier, int energy, int gen, long now) {
        final int floor = tune.floorEnergy();
        final boolean belowFloor = energy < floor;
        final int ride = Math.max(0, energy - floor) / Math.max(1, tune.rideHorizonTicks());
        final boolean surplus = energy > tune.restoreEnergy();
        final int ceiling = setup.wireCeiling();
        final int standing = tune.upkeep();
        final int run = surplus ? Math.max(0, ceiling - standing)
                : Math.min(ceiling, Math.max(0, gen - standing) + ride);
        final int admit = surplus ? Math.max(0, ceiling - standing) : Math.min(ceiling, Math.max(0, gen - standing));
        final int critical = surplus ? ceiling : Math.min(ceiling, gen + ride);
        final int margin = Math.round(gen * (tune.burstMarginPct() / 100.0f));
        final long restoreSupply = surplus ? (long) Math.max(0, gen - standing) + ride : Math.max(0, gen - standing);
        final int useMargin = PduAutoController.restoreMargin(margin, admit, restoreSupply);
        int total = 0;
        final List<PduAutoController.Device> devices = new ArrayList<>();
        for (int i = 0; i < setup.machines().size(); i++) {
            if (!connected[i]) continue;
            final Machine m = setup.machines().get(i);
            devices.add(new PduAutoController.Device(i + 1L, tier[i], m.draw(), locks[i]));
            if (locks[i] != FeLock.FORCE_OFF) total += m.draw();
        }
        final PduAutoController.Supply supply = new PduAutoController.Supply(run, admit, useMargin, belowFloor, surplus,
                surplus ? total > ceiling : ceiling < gen + ride, critical, 0, false, false, 0);
        return new Auto(auto.decide(devices, supply, now), supply, surplus);
    }

    private static final FeServePolicy POLICY = new FeServePolicy() {
        @Override
        public int tierOrdinalOf(FeGridNode node) {
            return ((Node) node).tier;
        }

        @Override
        public void markShed(FeGridNode node, boolean shed) {
            ((Node) node).shed = shed;
        }
    };

    private static final class Node implements FeGridNode, IEnergyStorage {
        final int index;
        final Machine m;
        int energy;
        int tier;
        boolean shed;
        int served;

        Node(int index, Machine m) {
            this.index = index;
            this.m = m;
            this.energy = m.start();
        }

        int room() {
            return Math.max(0, m.buffer() - energy);
        }

        boolean pay(boolean suppressed) {
            if (suppressed) return false;
            switch (m.kind()) {
                case MOTOR, RANGE, LAMP -> {
                    if (energy >= m.draw()) {
                        energy -= m.draw();
                        return true;
                    }
                    return false;
                }
                case REPELLER -> {
                    int spent = Math.min(m.draw(), energy);
                    energy -= spent;
                    return spent > 0;
                }
            }
            return false;
        }

        @Override
        public long nodeKey() {
            return index + 1L;
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
            return m.rate();
        }

        @Override
        public IEnergyStorage attachedStorage() {
            return this;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int take = Math.max(0, Math.min(maxReceive, room()));
            if (!simulate) {
                energy += take;
                served += take;
            }
            return take;
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
            return m.buffer();
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
}
