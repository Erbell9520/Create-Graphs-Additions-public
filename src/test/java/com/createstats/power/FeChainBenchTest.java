package com.createstats.power;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeChainBenchTest {
    private static final int CAP = 512_000;
    private static final int FLOOR = Math.round(CAP * 0.15f);
    private static final int MARK = Math.round(CAP * 0.45f);
    private static final int HORIZON = 600;
    private static final int WIRE = 16_384;
    private static final int UPKEEP = 51;

    private interface Budgeter {
        PduOwnBudget.Budgets at(int gen, int energy, boolean surplus, int demand);
    }

    private static final Budgeter SHIPPED = (gen, energy, surplus, demand) -> PduOwnBudget.of(
            new PduOwnBudget.Inputs(gen, gen, UPKEEP, WIRE, energy, FLOOR, HORIZON, surplus, 0, 0, demand));

    private static final Budgeter AS_SHIPPED_1_15_10 = (gen, energy, surplus, demand) -> {
        final int ride = Math.max(0, energy - FLOOR) / HORIZON;
        final int run = surplus ? WIRE - UPKEEP : Math.min(WIRE, Math.max(0, gen - UPKEEP) + ride);
        final int admit = surplus ? WIRE - UPKEEP : Math.min(WIRE, Math.max(0, gen - UPKEEP));
        final int critical = surplus ? WIRE : Math.min(WIRE, gen + ride);
        final long supply = surplus ? (long) Math.max(0, gen - UPKEEP) + ride : Math.max(0, gen - UPKEEP);
        return new PduOwnBudget.Budgets(run, admit, critical, supply, false);
    };

    private record Settled(int changes, int minRunning, int maxRunning, int maxFlips) {
    }

    private static Settled runBoard(Budgeter budgets, int gen, List<PduAutoController.Device> devices,
                                    int ticks, int settle) {
        final PduAutoController c = new PduAutoController();
        int energy = CAP;
        int demand = 0;
        for (PduAutoController.Device d : devices) {
            demand += d.demand();
        }
        Set<Long> previous = null;
        final java.util.Map<Long, Integer> flips = new java.util.HashMap<>();
        int changes = 0;
        int min = Integer.MAX_VALUE;
        int max = 0;
        for (long t = 0; t < ticks; t++) {
            energy = Math.max(0, Math.min(CAP, energy + gen) - UPKEEP);
            final boolean surplus = energy > MARK;
            final PduOwnBudget.Budgets b = budgets.at(gen, energy, surplus, demand);
            final int margin = Math.round(gen * 0.10f);
            final PduAutoController.Supply supply = new PduAutoController.Supply(b.run(), b.admit(),
                    PduAutoController.restoreMargin(margin, b.admit(), b.restoreSupply()), energy < FLOOR, surplus,
                    false, b.critical(), 0, false, false, 0);
            final PduAutoController.Plan plan = c.decide(devices, supply, t);
            energy = Math.max(0, energy - plan.admittedDemand());
            if (t >= settle) {
                if (previous != null && !previous.equals(plan.admitted())) {
                    changes++;
                    for (PduAutoController.Device d : devices) {
                        if (previous.contains(d.key()) != plan.admitted().contains(d.key())) {
                            flips.merge(d.key(), 1, Integer::sum);
                        }
                    }
                }
                min = Math.min(min, plan.admitted().size());
                max = Math.max(max, plan.admitted().size());
            }
            previous = new HashSet<>(plan.admitted());
        }
        int maxFlips = 0;
        for (int f : flips.values()) {
            maxFlips = Math.max(maxFlips, f);
        }
        return new Settled(changes, min, max, maxFlips);
    }

    private static List<PduAutoController.Device> uniform(int count, int cost) {
        final List<PduAutoController.Device> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            out.add(new PduAutoController.Device(i, FePriority.NORMAL.ordinal(), cost));
        }
        return out;
    }

    @Test
    void theAuditsTwoPulsingBoardsSettleAndTheOldShapeStillPulses() {
        final Settled oldA = runBoard(AS_SHIPPED_1_15_10, 2_200, uniform(10, 500), 24_000, 4_000);
        final Settled oldB = runBoard(AS_SHIPPED_1_15_10, 9_000, uniform(12, 1_024), 24_000, 4_000);
        assertTrue(oldA.changes() > 100, "the 1.15.10 shape pulsed; got " + oldA);
        assertTrue(oldB.changes() > 100, "the 1.15.10 shape pulsed; got " + oldB);
        assertEquals(0, runBoard(SHIPPED, 2_200, uniform(10, 500), 24_000, 4_000).changes());
        assertEquals(0, runBoard(SHIPPED, 9_000, uniform(12, 1_024), 24_000, 4_000).changes());
    }

    @Test
    void aBoardOnItsOwnBufferSettlesUnderSteadySupply() {
        final Random rnd = new Random(1_185L);
        int worst = 0;
        String worstCase = "";
        for (int gen = 600; gen <= 14_000; gen += 350) {
            final List<List<PduAutoController.Device>> branches = new ArrayList<>();
            branches.add(uniform(10, 500));
            branches.add(uniform(12, 1_024));
            branches.add(uniform(40, 256));
            final List<PduAutoController.Device> mixed = new ArrayList<>();
            for (int i = 0; i < 18; i++) {
                mixed.add(new PduAutoController.Device(i, 1 + rnd.nextInt(4), 16 + rnd.nextInt(1_500)));
            }
            branches.add(mixed);
            for (List<PduAutoController.Device> branch : branches) {
                final Settled s = runBoard(SHIPPED, gen, branch, 24_000, 4_000);
                if (s.maxFlips() > worst) {
                    worst = s.maxFlips();
                    worstCase = "gen " + gen + ", " + branch.size() + " devices: " + s;
                }
            }
        }
        assertTrue(worst <= 1, "a device flipped back and forth under steady supply: " + worstCase);
    }

    @Test
    void aNewcomerNeverShedsARunningDeviceOfItsOwnTierOrAbove() {
        final Random rnd = new Random(186L);
        for (int run = 0; run < 3_000; run++) {
            final PduAutoController a = new PduAutoController();
            final PduAutoController b = new PduAutoController();
            final List<PduAutoController.Device> branch = new ArrayList<>();
            final int n = 2 + rnd.nextInt(10);
            for (int i = 0; i < n; i++) {
                branch.add(new PduAutoController.Device(i, rnd.nextInt(5), 16 + rnd.nextInt(2_000),
                        FeLock.NONE, rnd.nextInt(5)));
            }
            final int budget = 200 + rnd.nextInt(12_000);
            final PduAutoController.Supply supply = new PduAutoController.Supply(budget,
                    Math.max(0, budget - rnd.nextInt(800)), rnd.nextInt(400), false, rnd.nextBoolean(),
                    false, budget + rnd.nextInt(2_000), 0, false, false);
            final long settle = 200;
            for (long t = 0; t < settle; t++) {
                a.decide(branch, supply, t);
                b.decide(branch, supply, t);
            }
            final PduAutoController.Device newcomer = new PduAutoController.Device(1_000, 1 + rnd.nextInt(4),
                    16 + rnd.nextInt(2_000), FeLock.NONE, rnd.nextInt(5));
            final List<PduAutoController.Device> plus = new ArrayList<>(branch);
            plus.add(newcomer);
            final Set<Long> wasRunning = new HashSet<>();
            for (PduAutoController.Device d : branch) {
                if (a.isRunning(d.key())) {
                    wasRunning.add(d.key());
                }
            }
            final PduAutoController.Plan withIt = a.decide(plus, supply, settle);
            final PduAutoController.Plan without = b.decide(branch, supply, settle);
            for (PduAutoController.Device d : branch) {
                final boolean atOrAbove = d.tierOrdinal() < newcomer.tierOrdinal()
                        || d.tierOrdinal() == newcomer.tierOrdinal() && d.legTierOrdinal() <= newcomer.legTierOrdinal();
                if (atOrAbove && wasRunning.contains(d.key())) {
                    assertEquals(without.isRunning(d.key()), withIt.isRunning(d.key()),
                            "run " + run + ": a newcomer moved a running device of its tier or above");
                }
            }
        }
    }

    @Test
    void aSurplusOutputOnlyEverGetsGenerationTheTiersAboveItLeft() {
        final Random rnd = new Random(190L);
        final BatteryOutputMode[] modes = {BatteryOutputMode.DIRECT, BatteryOutputMode.SURPLUS, BatteryOutputMode.LIMIT};
        for (int run = 0; run < 5_000; run++) {
            final List<BatteryControllerPlan.Output> outs = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                outs.add(new BatteryControllerPlan.Output(true, true, modes[rnd.nextInt(modes.length)],
                        rnd.nextInt(20_000), 1 + rnd.nextInt(65_536), rnd.nextInt(20_000), 0,
                        FePriority.values()[rnd.nextInt(FePriority.values().length)], false));
            }
            final int generation = rnd.nextInt(15_000);
            final int claim = rnd.nextInt(Math.max(1, generation));
            final int spendable = Math.max(0, generation + rnd.nextInt(30_000) - claim);
            final BatteryControllerPlan.Plan plan = BatteryControllerPlan.plan(outs, generation, spendable, 50, claim);
            int liveLeft = Math.max(0, generation - claim);
            for (FePriority tier : FePriority.values()) {
                int surplusHere = 0;
                int allHere = 0;
                for (int i = 0; i < outs.size(); i++) {
                    if (outs.get(i).priority() != tier) {
                        continue;
                    }
                    final int g = plan.grants().get(i).granted();
                    allHere += g;
                    if (outs.get(i).mode() == BatteryOutputMode.SURPLUS) {
                        surplusHere += g;
                    }
                }
                assertTrue(surplusHere <= liveLeft, "run " + run + ": SURPLUS at " + tier + " got " + surplusHere
                        + " with " + liveLeft + " of live generation left");
                liveLeft = Math.max(0, liveLeft - allHere);
            }
        }
    }

    private static final int FARM = 500_000_000;
    private static final int FARM_WIRE = 65_536;
    private static final int FARM_RATE = 65_536 * 3;
    private static final int RIDE = FeReserveBand.DEFAULT_RIDE_TICKS;
    private static final int WANT = 2_000;
    private static final long CRITICAL_KEY = 1_000L;

    private interface Policy {
        FeReserveBand.Band band(int capacity, int want);

        FeReserveReport assess(int capacity, int stored, int generation, int want, FeReserveReport.Stance previous);
    }

    private static final Policy PERCENTS = new Policy() {
        @Override
        public FeReserveBand.Band band(int capacity, int want) {
            return FeReserveBand.Band.DEFAULT;
        }

        @Override
        public FeReserveReport assess(int capacity, int stored, int generation, int want,
                                      FeReserveReport.Stance previous) {
            return FeReserveBand.assess(1L, new FeReserveBand.Params(stored, capacity, generation, 0,
                    Math.min(FARM_RATE, stored), FARM_WIRE, band(capacity, want), RIDE, previous));
        }
    };

    private static final Policy SIZED = new Policy() {
        @Override
        public FeReserveBand.Band band(int capacity, int want) {
            return FeReserveBand.sized(FeReserveBand.Band.DEFAULT, FeReserveBand.Minutes.DEFAULT, capacity, want,
                    FARM_RATE, RIDE);
        }

        @Override
        public FeReserveReport assess(int capacity, int stored, int generation, int want,
                                      FeReserveReport.Stance previous) {
            return FeReserveBand.assess(1L, new FeReserveBand.Params(stored, capacity, generation, 0,
                    Math.min(FARM_RATE, stored), FARM_WIRE, band(capacity, want), RIDE, previous, want));
        }
    };

    private static final Policy FIRST_DRAFT = new Policy() {
        @Override
        public FeReserveBand.Band band(int capacity, int want) {
            return SIZED.band(capacity, want);
        }

        @Override
        public FeReserveReport assess(int capacity, int stored, int generation, int want,
                                      FeReserveReport.Stance previous) {
            final FeReserveReport r = FeReserveBand.assess(1L, new FeReserveBand.Params(stored, capacity, generation,
                    0, Math.min(FARM_RATE, stored), FARM_WIRE, band(capacity, want), RIDE, previous));
            final int floor = (int) ((long) capacity * r.floorPct() / 100L);
            if (stored > floor || want <= 0) {
                return r;
            }
            final long horizon = Math.max(RIDE, Math.min(FeReserveBand.EMERGENCY_HORIZON_TICKS, floor / want));
            return new FeReserveReport(r.controllerPos(), r.stored(), r.capacity(), r.generation(), r.netRate(),
                    r.sustainedDraw(), (int) Math.min(FARM_RATE, stored / horizon), r.chargeClaim(),
                    r.outputCeiling(), r.floorPct(), r.ceilingPct(), r.stance());
        }
    };

    private static PduAutoController.Supply boardSupply(FeBoardDeal.Answer deal, int ceiling) {
        final int dealSupply = deal.total();
        final int dealRun = Math.min(ceiling, Math.max(0, dealSupply));
        final int run = FeBoardDeal.runBudget(dealRun, 0, deal.spare(), ceiling);
        final int admit = Math.min(run, dealRun);
        final int critical = Math.min(ceiling, Math.max(run, dealRun));
        final int margin = Math.round(dealSupply * 0.10f);
        return new PduAutoController.Supply(run, admit,
                PduAutoController.restoreMargin(margin, admit, (long) admit + deal.spare()), false,
                deal.restore() > 0, false, critical, 0, deal.restore() <= 0 && deal.stance().isRationing(), false, 0);
    }

    private static List<PduAutoController.Device> board(int want, int count, int cost) {
        final List<PduAutoController.Device> out = new ArrayList<>(uniform(count, cost));
        out.add(new PduAutoController.Device(CRITICAL_KEY, FePriority.CRITICAL.ordinal(), want));
        return out;
    }

    private record Stretch(long ordinaryFe, int ordinaryEndedAt, int criticalShortAt, int restoredAt,
                           int ordinaryUnderFloor, int maxFlips, int stored) {
    }

    private static Stretch stretch(Policy policy, int capacity, int stored, int generation, int ticks,
                                   List<PduAutoController.Device> devices, int want, PduAutoController board, long from) {
        FeReserveReport.Stance stance = null;
        long ordinaryFe = 0;
        int ordinaryEnded = -1;
        int criticalShort = -1;
        int restored = -1;
        int underFloor = 0;
        final Map<Long, Integer> flips = new HashMap<>();
        for (long t = from; t < from + ticks; t++) {
            final FeReserveBand.Band band = policy.band(capacity, want);
            final int floor = (int) ((long) capacity * band.floorPct() / 100L);
            FeReserveReport report = policy.assess(capacity, stored, generation, want, stance);
            stance = report.stance();
            final int soc = (int) ((long) stored * 100L / capacity);
            final int allowance = Math.min(Math.max(report.sustainedDraw(), report.criticalDraw()), stored);
            final int claim = Math.min(FeChargePolicy.claimable(generation, allowance, want),
                    FeChargePolicy.claim(generation, soc, capacity, band, FeChargePolicy.DEFAULT_MAX_CLAIM_PCT,
                            Math.max(0, capacity - stored)));
            report = report.withChargeClaim(claim);
            final Set<Long> before = new HashSet<>();
            int running = 0;
            int shed = 0;
            for (PduAutoController.Device d : devices) {
                final boolean on = board.isRunning(d.key());
                if (on) {
                    before.add(d.key());
                }
                if (d.key() != CRITICAL_KEY) {
                    if (on) {
                        running += d.demand();
                    } else {
                        shed += d.demand();
                    }
                }
            }
            final FeBoardDeal.Answer deal = FeBoardDeal.split(1L, false, FeBoardDeal.Offer.of(report, 0, false),
                    List.of(new FeBoardDeal.Request(2L, want, running, shed)), null, null, t + 10).get(0);
            final PduAutoController.Plan plan = board.decide(devices, boardSupply(deal, FARM_WIRE), t);
            int ordinaryNow = 0;
            for (PduAutoController.Device d : devices) {
                final boolean on = plan.isRunning(d.key());
                if (on != before.contains(d.key())) {
                    flips.merge(d.key(), 1, Integer::sum);
                }
                if (d.key() == CRITICAL_KEY) {
                    if (!on && criticalShort < 0) {
                        criticalShort = stored;
                    }
                } else if (on) {
                    ordinaryNow += d.demand();
                    if (!before.contains(d.key()) && restored < 0) {
                        restored = stored;
                    }
                }
            }
            if (ordinaryNow == 0 && running > 0 && ordinaryEnded < 0) {
                ordinaryEnded = stored;
            }
            if (ordinaryNow > 0 && stored <= floor) {
                underFloor++;
            }
            ordinaryFe += ordinaryNow;
            stored = (int) Math.max(0, Math.min(capacity, (long) stored + generation - plan.admittedDemand()));
        }
        int maxFlips = 0;
        for (int f : flips.values()) {
            maxFlips = Math.max(maxFlips, f);
        }
        return new Stretch(ordinaryFe, ordinaryEnded, criticalShort, restored, underFloor, maxFlips, stored);
    }

    private static Stretch night(Policy policy) {
        return stretch(policy, FARM, FARM / 5 * 2, 0, 30_000, board(WANT, 30, 1_000), WANT,
                new PduAutoController(), 0);
    }

    @Test
    void aBigFarmSpendsItsBanksOnTheNightAndKeepsTenMinutesForCritical() {
        final Stretch old = night(PERCENTS);
        final Stretch now = night(SIZED);

        assertTrue(old.ordinaryEndedAt() >= 45_000_000, "1.15.33 stopped ordinary load at " + old.ordinaryEndedAt());

        assertTrue(now.ordinaryEndedAt() >= 24_000_000 && now.ordinaryEndedAt() <= 32_000_000,
                "ordinary load stopped at " + now.ordinaryEndedAt());
        assertTrue(now.ordinaryFe() >= old.ordinaryFe() + 20_000_000L,
                "the night ran ordinary load on " + now.ordinaryFe() + " FE, against " + old.ordinaryFe());
        assertEquals(0, now.ordinaryUnderFloor(), "and nothing ordinary ran with the banks under the floor");

        assertTrue(now.criticalShortAt() >= 0 && now.criticalShortAt() <= WANT * RIDE + WANT,
                "Critical went off with " + now.criticalShortAt() + " FE left");

        assertTrue(now.maxFlips() <= 1, "a machine flipped " + now.maxFlips() + " times in one night");
    }

    @Test
    void aTaperUnderTheFloorStrandsTheFloorOfAWholeCriticalMachine() {
        assertTrue(night(PERCENTS).criticalShortAt() >= 20_000_000, "1.15.33 stranded about 24M");
        assertTrue(night(FIRST_DRAFT).criticalShortAt() >= 20_000_000, "the first draft stranded the floor");
        assertTrue(night(SIZED).criticalShortAt() <= WANT * RIDE + WANT);
    }

    @Test
    void aMorningBringsShedMachinesBackOnceTheBanksAreThirtyMinutesOfCriticalDeep() {
        final Stretch old = morning(PERCENTS);
        final Stretch now = morning(SIZED);
        assertTrue(old.restoredAt() >= FARM / 100 * 32, "1.15.33 restored at " + old.restoredAt());
        assertTrue(now.restoredAt() >= 0 && now.restoredAt() <= FARM / 100 * 19, "restored at " + now.restoredAt());
        assertTrue(now.maxFlips() <= 1, "a machine flipped " + now.maxFlips() + " times on a rising morning");
    }

    private static Stretch morning(Policy policy) {
        final List<PduAutoController.Device> devices = board(WANT, 30, 1_000);
        final PduAutoController board = new PduAutoController();

        final PduAutoController.Supply criticalOnly = new PduAutoController.Supply(WANT, WANT, 0, false, false,
                false, WANT, 0, true, false, 0);
        for (long t = 0; t < 600; t++) {
            board.decide(devices, criticalOnly, t);
        }
        return stretch(policy, FARM, FARM / 100 * 12, 40_000, 12_000, devices, WANT, board, 600);
    }

    @Test
    void acrossFarmsAndWantsTheFloorIsKeptForCriticalAndSpentOnIt() {
        final Random rnd = new Random(198L);
        final int[] farms = {100_000_000, 300_000_000, 1_000_000_000, 2_000_000_000};
        for (int farm : farms) {
            for (int i = 0; i < 3; i++) {
                final int want = 500 + rnd.nextInt(12_000);
                final int count = 10 + rnd.nextInt(30);
                final int cost = 200 + rnd.nextInt(2_800);
                final FeReserveBand.Band band = SIZED.band(farm, want);
                final int floor = (int) ((long) farm * band.floorPct() / 100L);

                final int start = (int) Math.min(farm, (long) floor + (long) (count * cost + want) * 1_500L);
                final Stretch s = stretch(SIZED, farm, start, 0, 16_000, board(want, count, cost), want,
                        new PduAutoController(), 0);
                final String at = farm + " FE, " + want + " FE/t Critical, " + count + " x " + cost + ", floor " + floor;
                assertEquals(0, s.ordinaryUnderFloor(), "ordinary ran on the floor: " + at);
                assertTrue(s.criticalShortAt() < 0 || s.criticalShortAt() <= want * RIDE + want,
                        "Critical went off with " + s.criticalShortAt() + " FE left: " + at);
                assertTrue(s.maxFlips() <= 1, "a machine flipped " + s.maxFlips() + " times: " + at);
            }
        }
    }
}
