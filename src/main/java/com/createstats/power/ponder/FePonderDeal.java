package com.createstats.power.ponder;

import com.createstats.power.BatteryControllerPlan;
import com.createstats.power.BatteryOutputMode;
import com.createstats.power.FeBoardDeal;
import com.createstats.power.FeLock;
import com.createstats.power.FePriority;
import com.createstats.power.FeReserveBand;
import com.createstats.power.FeReserveReport;
import com.createstats.power.PduAutoController;
import com.createstats.power.PduReason;
import com.createstats.power.ReserveProjection;
import com.createstats.power.SupplyForecast;

import java.util.ArrayList;
import java.util.List;

public final class FePonderDeal {
    private FePonderDeal() {
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
    }

    public record Device(Load load, FePriority tier) {
    }

    public record Board(long pos, List<Device> devices) {
    }

    public record Setup(long controllerPos, int[] banks, int[] bankStart, int bankRate, int busCapacity,
                        int portCeiling, int portRate, FeReserveBand.Band band, int rideTicks, int claimPct,
                        FePonderPlant.Tuning boardTuning, int boardWire, int legWire, List<Board> boards) {
    }

    public static final class Recording {
        public final int length;
        public final Setup setup;
        public final int[] reserve;
        public final int[] socPct;
        public final FeReserveReport.Stance[] stance;
        public final int[] runPool;
        public final int[] criticalPool;

        public final int[][] bank;
        public final int[][] bankFlow;

        public final int[][] askCritical;
        public final int[][] askRunning;
        public final int[][] askRestore;
        public final int[][] gotCritical;
        public final int[][] gotRunning;
        public final int[][] gotRestore;
        public final FeBoardDeal.Reason[][] reason;
        public final int[][] boardEnergy;
        public final int[][] intake;

        public final boolean[][][] admitted;
        public final PduReason[][][] why;
        public final boolean[][][] working;
        public final int[][][] fed;

        public final ReserveProjection.Result[] outlook;
        public final int[] shortIn;

        Recording(Setup s, int length) {
            this.setup = s;
            this.length = length;
            final int b = s.boards().size();
            reserve = new int[length];
            socPct = new int[length];
            stance = new FeReserveReport.Stance[length];
            runPool = new int[length];
            criticalPool = new int[length];
            bank = new int[s.banks().length][length];
            bankFlow = new int[s.banks().length][length];
            askCritical = new int[b][length];
            askRunning = new int[b][length];
            askRestore = new int[b][length];
            gotCritical = new int[b][length];
            gotRunning = new int[b][length];
            gotRestore = new int[b][length];
            reason = new FeBoardDeal.Reason[b][length];
            boardEnergy = new int[b][length];
            intake = new int[b][length];
            admitted = new boolean[b][][];
            why = new PduReason[b][][];
            working = new boolean[b][][];
            fed = new int[b][][];
            for (int i = 0; i < b; i++) {
                final int n = s.boards().get(i).devices().size();
                admitted[i] = new boolean[n][length];
                why[i] = new PduReason[n][length];
                working[i] = new boolean[n][length];
                fed[i] = new int[n][length];
            }
            outlook = new ReserveProjection.Result[length];
            shortIn = new int[length];
        }

        public int at(int tick) {
            return Math.max(0, Math.min(length - 1, tick));
        }

        public int capacity() {
            int c = 0;
            for (int v : setup.banks()) c += v;
            return c;
        }

        public float fill(int t) {
            return capacity() <= 0 ? 0f : reserve[at(t)] / (float) capacity();
        }

        public int asked(int board, int t) {
            return askCritical[board][t] + askRunning[board][t] + askRestore[board][t];
        }

        public int granted(int board, int t) {
            return gotCritical[board][t] + gotRunning[board][t] + gotRestore[board][t];
        }

        public int firstReason(int b, FeBoardDeal.Reason r, int from) {
            for (int t = Math.max(0, from); t < length; t++) if (reason[b][t] == r) return t;
            return -1;
        }

        public int firstShed(int b, int d, int from) {
            for (int t = Math.max(0, from); t < length; t++) if (!admitted[b][d][t]) return t;
            return -1;
        }
    }

    public static final int OUTLOOK_TICKS = 100;

    public static Recording run(Setup s, int length) {
        final Recording rec = new Recording(s, length);
        final FePonderPlant.Tuning bt = s.boardTuning();
        final int nb = s.boards().size();
        final int nBanks = s.banks().length;
        final int[] bankE = s.bankStart().clone();
        final int[] boardE = new int[nb];
        final int[][] devE = new int[nb][];
        final boolean[][] running = new boolean[nb][];
        final PduAutoController[] auto = new PduAutoController[nb];
        final boolean[] wasHeld = new boolean[nb];
        for (int b = 0; b < nb; b++) {
            boardE[b] = bt.bufferCapacity();
            final int n = s.boards().get(b).devices().size();
            devE[b] = new int[n];
            running[b] = new boolean[n];
            java.util.Arrays.fill(running[b], true);
            for (int d = 0; d < n; d++) devE[b][d] = s.boards().get(b).devices().get(d).load().buffer;
            auto[b] = new PduAutoController();
            auto[b].setTuning(new PduAutoController.Tuning(bt.dwellTicks(), bt.restoreIntervalTicks()));
        }
        final int dischargeRate = s.portRate() * 3;
        final int chargeRate = s.portRate() * 3;
        int energy = 0;
        float filtered = 0f;
        int reserveRate = 0;
        FeReserveReport.Stance previous = null;
        ReserveProjection.Result outlook = ReserveProjection.Result.NONE;
        int outlookAt = Integer.MIN_VALUE / 2;
        for (int t = 0; t < length; t++) {
            final List<FeBoardDeal.Request> requests = new ArrayList<>(nb);
            for (int b = 0; b < nb; b++) {
                int critical = 0;
                int runningOrdinary = 0;
                int shedOrdinary = 0;
                final List<Device> devices = s.boards().get(b).devices();
                for (int d = 0; d < devices.size(); d++) {
                    final Device dev = devices.get(d);
                    if (dev.tier() == FePriority.CRITICAL) critical += dev.load().draw;
                    else if (running[b][d]) runningOrdinary += dev.load().draw;
                    else shedOrdinary += dev.load().draw;
                }
                requests.add(new FeBoardDeal.Request(s.boards().get(b).pos(), critical,
                        runningOrdinary + bt.upkeep(), shedOrdinary));
            }

            int reserve = 0;
            int reserveCap = 0;
            for (int k = 0; k < nBanks; k++) {
                reserve += bankE[k];
                reserveCap += s.banks()[k];
            }
            final int soc = reserveCap <= 0 ? 0 : (int) Math.max(0, Math.min(100, (long) reserve * 100L / reserveCap));
            final List<BatteryControllerPlan.Output> plan = new ArrayList<>(nb);
            for (int b = 0; b < nb; b++) {
                final int cost = Math.min(s.boardWire(), Math.max(0, bt.bufferCapacity() - boardE[b]));
                plan.add(new BatteryControllerPlan.Output(true, true, BatteryOutputMode.DIRECT, cost, s.portCeiling(), 0,
                        0, FePriority.NORMAL, wasHeld[b], true, requests.get(b).critical()));
            }

            final FeReserveReport state = FeReserveBand.assess(s.controllerPos(), new FeReserveBand.Params(reserve,
                    reserveCap, 0, reserveRate, Math.min(dischargeRate, reserve),
                    Math.max(1, BatteryControllerPlan.wiredCeiling(plan)), s.band(), s.rideTicks(), previous,
                    BatteryControllerPlan.criticalWant(plan)));
            final int fromReserve = Math.min(Math.max(state.sustainedDraw(), state.criticalDraw()), reserve);
            final BatteryControllerPlan.Plan decided = BatteryControllerPlan.plan(plan, 0,
                    Math.max(0, energy + fromReserve), soc, 0);
            int drawnTotal = 0;
            for (int b = 0; b < nb; b++) {
                final BatteryControllerPlan.Grant grant = decided.grants().get(b);
                wasHeld[b] = grant.held();
                rec.intake[b][t] = 0;
                if (grant.granted() <= 0) continue;
                if (energy < grant.granted()) {
                    int need = grant.granted() - energy;
                    for (int k = 0; k < nBanks && need > 0; k++) {
                        final int drawn = Math.min(Math.min(need, s.bankRate()), bankE[k]);
                        bankE[k] -= drawn;
                        rec.bankFlow[k][t] -= drawn;
                        energy += drawn;
                        drawnTotal += drawn;
                        need -= drawn;
                    }
                }
                final int served = Math.min(Math.min(grant.granted(), energy), bt.bufferCapacity() - boardE[b]);
                boardE[b] += served;
                energy -= served;
                rec.intake[b][t] = served;
            }

            final int[] caps = new int[nb];
            final boolean[] held = new boolean[nb];
            for (int b = 0; b < nb; b++) {
                final int cap = BatteryControllerPlan.boardCap(plan.get(b), decided.grants().get(b), 0, 0);
                caps[b] = FeBoardDeal.shareWire(new int[] {requests.get(b).total()}, cap)[0];
                held[b] = caps[b] <= 0;
            }
            final FeBoardDeal.Offer offer = FeBoardDeal.Offer.of(state.withChargeClaim(0), 0, false);
            final List<FeBoardDeal.Answer> answers = FeBoardDeal.split(s.controllerPos(), false, offer, requests, caps,
                    held, t + 20L);

            int charged = 0;
            if (energy > 0) {
                int spare = Math.min(energy, chargeRate);
                for (int k = 0; k < nBanks && spare > 0; k++) {
                    final int stored = Math.min(Math.min(spare, s.bankRate()), s.banks()[k] - bankE[k]);
                    bankE[k] += stored;
                    rec.bankFlow[k][t] += stored;
                    energy -= stored;
                    charged += stored;
                    spare -= stored;
                }
            }
            filtered = filtered * 0.75f + (charged - drawnTotal) * 0.25f;
            reserveRate = filtered > 0f ? (int) Math.ceil(filtered - 0.001f)
                    : filtered < 0f ? (int) Math.floor(filtered + 0.001f) : 0;
            previous = state.stance();

            if (t - outlookAt >= OUTLOOK_TICKS) {
                outlookAt = t;
                final List<BatteryControllerPlan.Output> outs = new ArrayList<>(nb);
                for (int b = 0; b < nb; b++) {
                    final BatteryControllerPlan.Output o = plan.get(b);
                    outs.add(new BatteryControllerPlan.Output(o.wired(), o.enabled(), o.mode(),
                            Math.min(o.ceiling(), requests.get(b).total()), o.ceiling(), o.limit(), o.floorPct(),
                            o.priority(), o.wasHeld(), o.managed(), o.criticalDemand()));
                }
                int after = 0;
                for (int v : bankE) after += v;
                outlook = ReserveProjection.project(new ReserveProjection.Setup(after, reserveCap, 0, outs, s.band(),
                        s.rideTicks(), dischargeRate, chargeRate, s.claimPct(), s.portRate(), true, 0, 0,
                        new SupplyForecast.Builder(3, ReserveProjection.HORIZON_TICKS).build(), s.band(), null, 0));
            }

            for (int b = 0; b < nb; b++) {
                final FeBoardDeal.Answer deal = answers.get(b);
                final List<Device> devices = s.boards().get(b).devices();
                final int n = devices.size();
                final int ceiling = n * s.legWire();
                final int standing = bt.upkeep();
                boardE[b] -= Math.min(boardE[b], standing);
                final int floor = bt.floorEnergy();
                final boolean belowFloor = boardE[b] < floor;
                final int ride = Math.max(0, boardE[b] - floor) / Math.max(1, bt.rideHorizonTicks());
                final int dealSupply = deal.total();
                final int dealRun = Math.min(ceiling, Math.max(0, dealSupply - standing));
                final int useRun = FeBoardDeal.runBudget(dealRun, 0, deal.spare(), ceiling);
                final int useAdmit = Math.min(useRun, dealRun);
                final int useCritical = Math.min(ceiling, Math.max(useRun, dealRun + ride));
                final boolean useSurplus = deal.restore() > 0;
                final boolean useReserveLow = deal.restore() <= 0 && deal.stance().isRationing();
                int total = 0;
                final List<PduAutoController.Device> devs = new ArrayList<>(n);
                for (int d = 0; d < n; d++) {
                    devs.add(new PduAutoController.Device(d + 1L, devices.get(d).tier().ordinal(),
                            devices.get(d).load().draw, FeLock.NONE));
                    total += devices.get(d).load().draw;
                }
                final int margin = Math.round(dealSupply * (bt.burstMarginPct() / 100.0f));
                final int useMargin = PduAutoController.restoreMargin(margin, useAdmit, (long) useAdmit + deal.spare());
                final PduAutoController.Supply supply = new PduAutoController.Supply(useRun, useAdmit, useMargin,
                        belowFloor, useSurplus, total > ceiling, useCritical, 0, useReserveLow, false, 0);
                final PduAutoController.Plan decision = auto[b].decide(devs, supply, t);
                int pool = Math.min(boardE[b], ceiling);
                for (int d = 0; d < n; d++) {
                    running[b][d] = decision.isRunning(d + 1L);
                    rec.admitted[b][d][t] = running[b][d];
                    rec.why[b][d][t] = decision.reasonFor(d + 1L);
                    final Load load = devices.get(d).load();
                    if (running[b][d]) {
                        final int give = Math.min(pool, Math.min(load.draw, load.buffer - devE[b][d]));
                        devE[b][d] += give;
                        pool -= give;
                        boardE[b] -= give;
                        rec.fed[b][d][t] = give;
                    }

                    final boolean works = running[b][d] && devE[b][d] >= load.draw;
                    if (works) devE[b][d] -= load.draw;
                    rec.working[b][d][t] = works;
                }
                rec.askCritical[b][t] = requests.get(b).critical();
                rec.askRunning[b][t] = requests.get(b).running();
                rec.askRestore[b][t] = requests.get(b).restore();
                rec.gotCritical[b][t] = deal.critical();
                rec.gotRunning[b][t] = deal.running();
                rec.gotRestore[b][t] = deal.restore();
                rec.reason[b][t] = deal.reason();
                rec.boardEnergy[b][t] = boardE[b];
            }
            int after = 0;
            for (int k = 0; k < nBanks; k++) {
                after += bankE[k];
                rec.bank[k][t] = bankE[k];
            }
            rec.reserve[t] = after;
            rec.socPct[t] = reserveCap <= 0 ? 0 : (int) Math.max(0, Math.min(100, (long) after * 100L / reserveCap));
            rec.stance[t] = state.stance();
            rec.runPool[t] = offer.runPool();
            rec.criticalPool[t] = offer.criticalPool();
            rec.outlook[t] = outlook;
            final ReserveProjection.Milestone first = outlook.first(ReserveProjection.Kind.SHORT);
            rec.shortIn[t] = outlook.shortNow() ? 0 : first == null ? -1 : Math.max(0, first.tick() - (t - outlookAt));
        }
        return rec;
    }
}
