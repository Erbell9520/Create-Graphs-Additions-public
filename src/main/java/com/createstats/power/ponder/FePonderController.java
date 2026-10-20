package com.createstats.power.ponder;

import com.createstats.power.BatteryControllerPlan;
import com.createstats.power.BatteryOutputMode;
import com.createstats.power.FeChargePolicy;
import com.createstats.power.FePriority;
import com.createstats.power.FeReserveBand;
import com.createstats.power.FeReserveReport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// the block's own decisions in the block's order
public final class FePonderController {
    private FePonderController() {
    }

    public enum Load {
        RANGE(256, 2_000),

        LAMP(1, 100);

        final int draw;
        final int buffer;

        Load(int draw, int buffer) {
            this.draw = draw;
            this.buffer = buffer;
        }

        public int draw() {
            return draw;
        }

        public int buffer() {
            return buffer;
        }
    }

    public record Output(Load load, FePriority priority, int floorPct) {
    }

    public record Setup(int[] banks, int[] bankStart, int bankRate, int dynamoBuffer, int dynamoRate, double fePerRpm,
                        int busCapacity, int portCeiling, int portRate, FeReserveBand.Band band, int rideTicks,
                        int claimPct, List<Output> outputs) {
    }

    public static final class Script {
        final int length;
        private final Map<Integer, Integer> rpm = new HashMap<>();
        private final int[] wired = {0, 0, 0, 0, 0, 0};

        public Script(int length) {
            this.length = length;
        }

        public Script rpm(int tick, int rpm) {
            this.rpm.put(tick, rpm);
            return this;
        }

        public Script wire(int tick, int port) {
            wired[port] = tick;
            return this;
        }
    }

    public static final class Recording {
        public final int length;
        public final Setup setup;
        public final int[] rpm;
        public final int[] generation;
        public final int[] reserve;
        public final int[] socPct;
        public final FeReserveReport.Stance[] stance;
        public final int[] sustained;
        public final int[] critical;
        public final int[] claim;
        public final int[] rate;
        public final int[] delivered;
        public final int[] bus;

        public final int[][] bank;
        public final int[][] bankFlow;

        public final int[][] want;
        public final int[][] flow;
        public final BatteryControllerPlan.Reason[][] why;
        public final boolean[][] held;

        public final int[][] level;
        public final boolean[][] starved;
        private final int[] wired;

        Recording(Setup setup, int length, int[] wired) {
            this.setup = setup;
            this.length = length;
            this.wired = wired.clone();
            final int outs = setup.outputs().size();
            final int banks = setup.banks().length;
            rpm = new int[length];
            generation = new int[length];
            reserve = new int[length];
            socPct = new int[length];
            stance = new FeReserveReport.Stance[length];
            sustained = new int[length];
            critical = new int[length];
            claim = new int[length];
            rate = new int[length];
            delivered = new int[length];
            bus = new int[length];
            bank = new int[banks][length];
            bankFlow = new int[banks][length];
            want = new int[outs][length];
            flow = new int[outs][length];
            why = new BatteryControllerPlan.Reason[outs][length];
            held = new boolean[outs][length];
            level = new int[outs][length];
            starved = new boolean[outs][length];
        }

        public int at(int tick) {
            return Math.max(0, Math.min(length - 1, tick));
        }

        public int wired(int port) {
            return wired[port];
        }

        public int capacity() {
            int c = 0;
            for (int b : setup.banks()) c += b;
            return c;
        }

        public float fill(int t) {
            final int cap = capacity();
            return cap <= 0 ? 0f : reserve[at(t)] / (float) cap;
        }

        public int firstStance(FeReserveReport.Stance s, int from) {
            for (int t = Math.max(0, from); t < length; t++) if (stance[t] == s) return t;
            return -1;
        }

        public int firstWhy(int i, BatteryControllerPlan.Reason r, int from) {
            for (int t = Math.max(0, from); t < length; t++) if (why[i][t] == r) return t;
            return -1;
        }
    }

    static int rangeDraw(int setting) {
        return switch (setting) {
            case 1 -> 16;
            case 2 -> 64;
            case 3 -> 256;
            default -> 0;
        };
    }

    public static Recording run(Setup s, Script script) {
        final Recording rec = new Recording(s, script.length, script.wired);
        final int outs = s.outputs().size();
        final int nBanks = s.banks().length;
        final int[] bankE = s.bankStart().clone();
        final int[] loadE = new int[outs];
        final boolean[] wasHeld = new boolean[outs];
        final boolean[] suppressed = new boolean[outs];
        final int dischargeRate = s.portRate() * 3;
        final int chargeRate = s.portRate() * 3;
        int dynamoBuffer = 0;
        int rpm = 0;
        int energy = 0;
        float filtered = 0f;
        int reserveRate = 0;
        FeReserveReport.Stance previous = null;
        for (int t = 0; t < script.length; t++) {
            if (script.rpm.containsKey(t)) rpm = script.rpm.get(t);
            dynamoBuffer = Math.min(s.dynamoBuffer(), dynamoBuffer + (rpm == 0 ? 0 : (int) Math.floor(Math.abs(rpm)
                    * s.fePerRpm())));

            final boolean[] bankOn = new boolean[nBanks];
            for (int b = 0; b < nBanks; b++) bankOn[b] = t >= script.wired[1 + b];
            final int room = Math.max(0, s.busCapacity() - energy);
            final int pulled = t < script.wired[0] ? 0
                    : Math.min(dynamoBuffer, Math.min(Math.min(s.dynamoRate(), s.portCeiling()), room));
            dynamoBuffer -= pulled;
            energy += pulled;
            final int generation = pulled;
            int reserve = 0;
            int reserveCap = 0;
            for (int b = 0; b < nBanks; b++) {
                if (!bankOn[b]) continue;
                reserve += bankE[b];
                reserveCap += s.banks()[b];
            }
            final int soc = reserveCap <= 0 ? 0 : (int) Math.max(0, Math.min(100, (long) reserve * 100L / reserveCap));

            final List<BatteryControllerPlan.Output> plan = new ArrayList<>(outs);
            final int[] cost = new int[outs];
            for (int i = 0; i < outs; i++) {
                final Output o = s.outputs().get(i);
                cost[i] = Math.min(o.load().draw, Math.max(0, o.load().buffer - loadE[i]));
                final boolean on = t >= script.wired[3 + i];
                plan.add(new BatteryControllerPlan.Output(on, true, BatteryOutputMode.DIRECT, on ? cost[i] : 0,
                        on ? s.portCeiling() : 0, 0, o.floorPct(), o.priority(), wasHeld[i], false, 0));
            }

            final int criticalWant = BatteryControllerPlan.criticalWant(plan);
            final FeReserveReport state = FeReserveBand.assess(0L, new FeReserveBand.Params(reserve, reserveCap,
                    generation, reserveRate, Math.min(dischargeRate, reserve),
                    Math.max(1, BatteryControllerPlan.wiredCeiling(plan)), s.band(), s.rideTicks(), previous,
                    criticalWant));

            final int allowance = Math.min(Math.max(state.sustainedDraw(), state.criticalDraw()), reserve);
            int headroom = 0;
            for (int b = 0; b < nBanks; b++) if (bankOn[b]) headroom += Math.min(s.bankRate(), s.banks()[b] - bankE[b]);
            final int claim = Math.min(FeChargePolicy.claimable(energy, allowance, criticalWant),
                    FeChargePolicy.claim(generation, soc, reserveCap, s.band(), s.claimPct(), headroom));

            final BatteryControllerPlan.Plan decided = BatteryControllerPlan.plan(plan, generation,
                    Math.max(0, energy + allowance - claim), soc, claim);

            int delivered = 0;
            int drawnTotal = 0;
            final int[] moved = new int[nBanks];
            for (int i = 0; i < outs; i++) {
                final BatteryControllerPlan.Grant grant = decided.grants().get(i);
                rec.why[i][t] = grant.reason();
                rec.held[i][t] = grant.held();
                wasHeld[i] = grant.held();
                rec.want[i][t] = cost[i];
                if (grant.granted() <= 0) {
                    suppressed[i] = grant.reason().isWithheld();
                    continue;
                }
                if (energy < grant.granted()) {
                    int need = grant.granted() - energy;
                    for (int b = 0; b < nBanks && need > 0; b++) {
                        if (!bankOn[b]) continue;
                        final int drawn = Math.min(Math.min(need, s.bankRate()), bankE[b]);
                        bankE[b] -= drawn;
                        moved[b] -= drawn;
                        energy += drawn;
                        drawnTotal += drawn;
                        need -= drawn;
                    }
                }
                suppressed[i] = false;
                final Output o = s.outputs().get(i);
                final int served = Math.min(Math.min(grant.granted(), energy), Math.max(0, o.load().buffer - loadE[i]));
                loadE[i] += served;
                energy -= served;
                rec.flow[i][t] = served;
                delivered += served;
            }

            int charged = 0;
            if (energy > 0) {
                int spare = Math.min(energy, chargeRate);
                for (int b = 0; b < nBanks && spare > 0; b++) {
                    if (!bankOn[b]) continue;
                    final int stored = Math.min(Math.min(spare, s.bankRate()), s.banks()[b] - bankE[b]);
                    bankE[b] += stored;
                    moved[b] += stored;
                    energy -= stored;
                    charged += stored;
                    spare -= stored;
                }
            }
            final int net = charged - drawnTotal;
            filtered = filtered * 0.75f + net * 0.25f;
            reserveRate = filtered > 0f ? (int) Math.ceil(filtered - 0.001f)
                    : filtered < 0f ? (int) Math.floor(filtered + 0.001f) : 0;

            for (int i = 0; i < outs; i++) {
                final Output o = s.outputs().get(i);
                if (o.load() == Load.RANGE) {
                    final int wanted = suppressed[i] ? 0 : 3;
                    int setting = wanted;
                    while (setting > 0 && loadE[i] < rangeDraw(setting)) setting--;
                    loadE[i] -= rangeDraw(setting);
                    rec.level[i][t] = setting;
                    rec.starved[i][t] = setting < wanted;
                } else {
                    final boolean lit = !suppressed[i] && loadE[i] >= o.load().draw;
                    if (lit) loadE[i] -= o.load().draw;
                    rec.level[i][t] = lit ? 1 : 0;
                }
            }
            previous = state.stance();
            rec.rpm[t] = rpm;
            rec.generation[t] = generation;
            int after = 0;
            for (int b = 0; b < nBanks; b++) {
                rec.bank[b][t] = bankE[b];
                rec.bankFlow[b][t] = moved[b];
                if (bankOn[b]) after += bankE[b];
            }
            rec.reserve[t] = after;
            rec.socPct[t] = reserveCap <= 0 ? 0 : (int) Math.max(0, Math.min(100, (long) after * 100L / reserveCap));
            rec.stance[t] = state.stance();
            rec.sustained[t] = state.sustainedDraw();
            rec.critical[t] = state.criticalDraw();
            rec.claim[t] = claim;
            rec.rate[t] = reserveRate;
            rec.delivered[t] = delivered;
            rec.bus[t] = energy;
        }
        return rec;
    }
}
