package com.createstats.power;

import java.util.ArrayList;
import java.util.List;

// the controller never actually empties, so "empty in 37:57" was a lie
public final class ReserveProjection {
    private ReserveProjection() {
    }

    public static final int HORIZON_TICKS = 36_000;

    public static final int STEP_TICKS = 100;

    public static final int SAMPLE_TICKS = 600;

    public static final int SAMPLES = HORIZON_TICKS / SAMPLE_TICKS + 1;

    public record Setup(int stored, int capacity, int generation, List<BatteryControllerPlan.Output> outputs,
                        FeReserveBand.Band band, int rideTicks, int dischargeRate, int chargeRate,
                        int maxClaimPct, int portRate, boolean claimAllowed, int storeRoom, int storeRate,
                        SupplyForecast.Result supply, FeReserveBand.Band claimBand,
                        FeReserveReport.Stance upstreamStance, int upstreamAllowance) {
        public Setup {
            stored = Math.max(0, stored);
            capacity = Math.max(0, capacity);
            generation = Math.max(0, generation);
            outputs = outputs == null ? List.of() : List.copyOf(outputs);
            band = band == null ? FeReserveBand.Band.DEFAULT : band;
            rideTicks = Math.max(1, rideTicks);
            dischargeRate = Math.max(0, dischargeRate);
            chargeRate = Math.max(0, chargeRate);
            storeRoom = Math.max(0, storeRoom);
            storeRate = Math.max(0, storeRate);
            supply = supply == null ? SupplyForecast.Result.NONE : supply;
            claimBand = claimBand == null ? band : claimBand;
            upstreamAllowance = Math.max(0, upstreamAllowance);
        }

        public Setup(int stored, int capacity, int generation, List<BatteryControllerPlan.Output> outputs,
                     FeReserveBand.Band band, int rideTicks, int dischargeRate, int chargeRate,
                     int maxClaimPct, int portRate, boolean claimAllowed, int storeRoom, int storeRate,
                     SupplyForecast.Result supply) {
            this(stored, capacity, generation, outputs, band, rideTicks, dischargeRate, chargeRate, maxClaimPct,
                    portRate, claimAllowed, storeRoom, storeRate, supply, band, null, 0);
        }

        public Setup(int stored, int capacity, int generation, List<BatteryControllerPlan.Output> outputs,
                     FeReserveBand.Band band, int rideTicks, int dischargeRate, int chargeRate,
                     int maxClaimPct, int portRate, boolean claimAllowed, int storeRoom, int storeRate) {
            this(stored, capacity, generation, outputs, band, rideTicks, dischargeRate, chargeRate, maxClaimPct,
                    portRate, claimAllowed, storeRoom, storeRate, SupplyForecast.Result.NONE);
        }

        public int generationAt(int ticks) {
            if (!supply.isPresent()) {
                return generation;
            }
            return (int) Math.max(0L, Math.min(Integer.MAX_VALUE,
                    (long) generation + supply.at(ticks) - supply.at(0)));
        }
    }

    public enum Kind {
        SHORT,

        STANCE,

        FLOOR,

        FULL,

        STORES_FULL,

        SETTLES,

        SUN_UP,

        SUN_DOWN,

        FUEL_OUT
    }

    private static Kind kindOf(SupplyForecast.What what) {
        return switch (what) {
            case SUN_UP -> Kind.SUN_UP;
            case SUN_DOWN -> Kind.SUN_DOWN;
            case FUEL_OUT -> Kind.FUEL_OUT;
        };
    }

    public record Milestone(int tick, Kind kind, int output, int value, int socPct) {
    }

    public record Result(byte[] trace, List<Milestone> milestones, boolean shortNow, int[] supply) {
        public static final Result NONE = new Result(new byte[0], List.of(), false, new int[0]);

        public Result {
            trace = trace == null ? new byte[0] : trace;
            milestones = milestones == null ? List.of() : milestones;
            supply = supply == null ? new int[0] : supply;
        }

        public Result(byte[] trace, List<Milestone> milestones, boolean shortNow) {
            this(trace, milestones, shortNow, null);
        }

        public Milestone first(Kind kind) {
            for (Milestone m : milestones) {
                if (m.kind() == kind) {
                    return m;
                }
            }
            return null;
        }

        public Milestone first(Kind kind, int output) {
            for (Milestone m : milestones) {
                if (m.kind() == kind && m.output() == output) {
                    return m;
                }
            }
            return null;
        }

        public int ticksToShort() {
            if (shortNow) {
                return 0;
            }
            final Milestone m = first(Kind.SHORT);
            return m == null ? -1 : m.tick();
        }

        public int[] packMilestones() {
            final int[] out = new int[milestones.size() * 5];
            for (int i = 0; i < milestones.size(); i++) {
                final Milestone m = milestones.get(i);
                out[i * 5] = m.tick();
                out[i * 5 + 1] = m.kind().ordinal();
                out[i * 5 + 2] = m.output();
                out[i * 5 + 3] = m.value();
                out[i * 5 + 4] = m.socPct();
            }
            return out;
        }

        public static Result unpack(byte[] trace, int[] packed, boolean shortNow) {
            return unpack(trace, packed, shortNow, null);
        }

        public static Result unpack(byte[] trace, int[] packed, boolean shortNow, int[] supply) {
            final List<Milestone> ms = new ArrayList<>();
            final Kind[] kinds = Kind.values();
            if (packed != null) {
                for (int i = 0; i + 4 < packed.length; i += 5) {
                    final int k = packed[i + 1];
                    if (k < 0 || k >= kinds.length) {
                        continue;
                    }
                    ms.add(new Milestone(packed[i], kinds[k], packed[i + 2], packed[i + 3], packed[i + 4]));
                }
            }
            return new Result(trace == null ? new byte[0] : trace, List.copyOf(ms), shortNow, supply);
        }
    }

    public static int storeBudget(int spareGeneration, int allowanceLeft, FeReserveReport.Stance stance,
                                  boolean anyShort, boolean claimAllowed) {
        if (!claimAllowed) {
            return 0;
        }
        final int spare = Math.max(0, spareGeneration);
        final boolean deep = stance == FeReserveReport.Stance.SURPLUS && !anyShort;
        return deep ? FeRates.plus(spare, Math.max(0, allowanceLeft)) : spare;
    }

    public static int criticalWant(List<BatteryControllerPlan.Output> outputs) {
        return BatteryControllerPlan.criticalWant(outputs);
    }

    public static boolean shortOf(BatteryControllerPlan.Grant grant, BatteryControllerPlan.Output out) {
        final BatteryControllerPlan.Reason r = grant.reason();
        return out.wired() && out.enabled() && out.demand() > 0
                && (r == BatteryControllerPlan.Reason.PARTIAL || r == BatteryControllerPlan.Reason.SHED
                || r == BatteryControllerPlan.Reason.RECHARGE || r == BatteryControllerPlan.Reason.NO_SURPLUS
                || r == BatteryControllerPlan.Reason.FLOOR || r == BatteryControllerPlan.Reason.RATIONED);
    }

    static int[] boardServed(List<BatteryControllerPlan.Output> outs, BatteryControllerPlan.Plan plan,
                             FeReserveReport report, boolean claimAllowed) {
        final int n = outs.size();
        final int[] served = new int[n];
        int otherSpend = 0;
        final List<FeBoardDeal.Request> requests = new ArrayList<>(n);
        final List<Integer> boardOf = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            final BatteryControllerPlan.Output o = outs.get(i);
            final int granted = Math.max(0, plan.grants().get(i).granted());
            served[i] = granted;
            if (!o.managed()) {
                otherSpend = FeRates.plus(otherSpend, granted);
                continue;
            }
            final int critical = Math.min(o.criticalDemand(), o.demand());
            requests.add(new FeBoardDeal.Request(i, critical, o.demand() - critical, 0));
            boardOf.add(i);
        }
        if (requests.isEmpty()) {
            return served;
        }

        final int generationFree = Math.max(0, report.generation() - report.chargeClaim());
        final int[] caps = new int[requests.size()];
        final boolean[] heldOut = new boolean[requests.size()];

        final int[] tiers = new int[requests.size()];
        for (int j = 0; j < requests.size(); j++) {
            final BatteryControllerPlan.Output o = outs.get(boardOf.get(j));
            final BatteryControllerPlan.Grant g = plan.grants().get(boardOf.get(j));
            caps[j] = BatteryControllerPlan.boardCap(o, g, generationFree, 0);
            heldOut[j] = caps[j] <= 0;
            tiers[j] = o.priority().ordinal();
        }
        final List<FeBoardDeal.Answer> answers = FeBoardDeal.split(0L, false,
                FeBoardDeal.Offer.of(report, otherSpend, !claimAllowed), requests, caps, heldOut, tiers, 1L);
        for (int j = 0; j < answers.size(); j++) {
            final int i = boardOf.get(j);
            served[i] = Math.min(served[i], answers.get(j).total());
        }
        return served;
    }

    public static Result project(Setup s) {
        return project(s, HORIZON_TICKS, STEP_TICKS, SAMPLE_TICKS);
    }

    public static Result project(Setup s, int horizon, int step, int sample) {
        if (s.capacity() <= 0) {
            return Result.NONE;
        }
        final int stepTicks = Math.max(1, step);
        final int sampleTicks = Math.max(stepTicks, sample - sample % stepTicks);
        final int samples = Math.max(1, horizon / sampleTicks + 1);
        final byte[] trace = new byte[samples];
        final int[] supply = new int[samples];
        final List<Milestone> ms = new ArrayList<>();
        final int n = s.outputs().size();

        long stored = s.stored();
        long storeRoom = s.storeRoom();
        final boolean[] held = new boolean[n];
        final boolean[] wasShort = new boolean[n];
        final boolean[] wasFloor = new boolean[n];
        for (int i = 0; i < n; i++) {
            held[i] = s.outputs().get(i).wasHeld();
        }
        FeReserveReport.Stance stance = null;
        boolean shortNow = false;
        boolean full = stored >= s.capacity();
        boolean storesFull = storeRoom <= 0;
        int stillSteps = 0;
        boolean settled = false;
        final int critical = criticalWant(s.outputs());
        final List<SupplyForecast.Change> changes = s.supply().changes();
        int nextChange = 0;

        for (int t = 0; t <= horizon; t += stepTicks) {
            final int soc = (int) Math.max(0, Math.min(100, stored * 100L / s.capacity()));
            if (t % sampleTicks == 0 && t / sampleTicks < samples) {
                trace[t / sampleTicks] = (byte) soc;
            }

            while (nextChange < changes.size() && changes.get(nextChange).tick() <= t) {
                final SupplyForecast.Change c = changes.get(nextChange++);
                if (c.tick() <= horizon) {
                    ms.add(new Milestone(c.tick(), kindOf(c.what()), -1, c.fePerTick(), soc));
                }
            }
            final int gen = s.generationAt(t);
            if (t % sampleTicks == 0 && t / sampleTicks < samples) {
                supply[t / sampleTicks] = gen;
            }
            final int storedNow = (int) Math.min(Integer.MAX_VALUE, stored);

            FeReserveReport report = FeReserveBand.assess(0L, new FeReserveBand.Params(storedNow,
                    s.capacity(), gen, 0, Math.min(s.dischargeRate(), storedNow),
                    Math.max(1, BatteryControllerPlan.wiredCeiling(s.outputs())), s.band(), s.rideTicks(), stance,
                    critical));
            if (s.upstreamStance() != null) {
                report = report.limitedBy(s.upstreamStance(), s.upstreamAllowance());
            }
            if (stance != null && report.stance() != stance) {
                ms.add(new Milestone(t, Kind.STANCE, -1, report.stance().ordinal(), soc));
            }
            stance = report.stance();

            final int headroom = (int) Math.min(s.chargeRate(), Math.max(0L, (long) s.capacity() - stored));
            final int fromReserve = Math.min(Math.max(report.sustainedDraw(), report.criticalDraw()), storedNow);
            int claim = 0;
            if (s.claimAllowed()) {
                claim = Math.min(FeChargePolicy.claimable(gen, fromReserve, critical),
                        FeChargePolicy.claim(gen, soc, s.capacity(), s.claimBand(), s.maxClaimPct(), headroom));
            }

            final List<BatteryControllerPlan.Output> outs = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                final BatteryControllerPlan.Output o = s.outputs().get(i);
                outs.add(new BatteryControllerPlan.Output(o.wired(), o.enabled(), o.mode(), o.demand(),
                        o.ceiling(), o.limit(), o.floorPct(), o.priority(), held[i], o.managed(),
                        o.criticalDemand()));
            }
            final BatteryControllerPlan.Plan plan = BatteryControllerPlan.plan(outs, gen,
                    Math.max(0, FeRates.plus(gen, fromReserve) - claim), soc, claim);

            final int[] served = boardServed(outs, plan, report.withChargeClaim(claim), s.claimAllowed());
            boolean anyShort = false;
            for (int i = 0; i < n; i++) {
                final BatteryControllerPlan.Grant g = plan.grants().get(i);
                held[i] = g.held();
                final boolean isShort = shortOf(g, outs.get(i)) || served[i] < Math.min(outs.get(i).demand(),
                        outs.get(i).ceiling()) && outs.get(i).managed() && g.granted() > 0;
                anyShort |= isShort;
                if (t == 0) {
                    shortNow |= isShort;
                } else if (isShort && !wasShort[i]) {
                    ms.add(new Milestone(t, Kind.SHORT, i, 0, soc));
                }
                wasShort[i] = isShort;
                final boolean floor = g.reason() == BatteryControllerPlan.Reason.FLOOR
                        || g.reason() == BatteryControllerPlan.Reason.RATIONED;
                if (t > 0 && floor && !wasFloor[i]) {
                    ms.add(new Milestone(t, Kind.FLOOR, i, 0, soc));
                }
                wasFloor[i] = floor;
            }

            int total = 0;
            for (int i = 0; i < n; i++) {
                total = FeRates.plus(total, served[i]);
            }
            final int fromGen = Math.min(total, gen);
            final int drawn = (int) Math.min(stored, Math.max(0, total - fromGen));
            int spare = Math.max(0, gen - fromGen);

            final int storeBudget = storeBudget(spare, Math.max(0, fromReserve - drawn), stance, anyShort,
                    s.claimAllowed());
            final int toStores = (int) Math.min(Math.min(storeBudget, s.storeRate()), storeRoom);
            final int storesFromGen = Math.min(toStores, spare);
            spare -= storesFromGen;
            final int storesFromBank = toStores - storesFromGen;
            final int charged = (int) Math.min(Math.min(spare, s.chargeRate()),
                    Math.max(0L, (long) s.capacity() - stored));
            final long net = (long) charged - drawn - storesFromBank;

            final long before = stored;
            stored = Math.max(0L, Math.min(s.capacity(), stored + net * stepTicks));
            storeRoom = Math.max(0L, storeRoom - (long) toStores * stepTicks);

            if (!full && stored >= s.capacity()) {
                full = true;
                ms.add(new Milestone(t + stepTicks, Kind.FULL, -1, 100, 100));
            }
            if (!storesFull && s.storeRoom() > 0 && storeRoom <= 0) {
                storesFull = true;
                ms.add(new Milestone(t + stepTicks, Kind.STORES_FULL, -1, 0,
                        (int) Math.min(100, stored * 100L / s.capacity())));
            }

            final long sliver = Math.max(1L, s.capacity() / 2_000L);
            stillSteps = Math.abs(stored - before) <= sliver ? stillSteps + 1 : 0;
            if (!settled && stillSteps * stepTicks >= 1_200 && stored != s.stored() && !full) {
                settled = true;
                ms.add(new Milestone(t + stepTicks, Kind.SETTLES, -1,
                        (int) Math.min(100, stored * 100L / s.capacity()),
                        (int) Math.min(100, stored * 100L / s.capacity())));
            }
        }
        return new Result(trace, List.copyOf(ms), shortNow, supply);
    }
}
