package com.createstats.power;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeReserveBandTest {
    private static final int CAPACITY = 10_000_000;
    private static final int DISCHARGE = 98_304;
    private static final int CEILING = 32_768;

    private static FeReserveReport at(int socPct, int generation) {
        return FeReserveBand.assess(42L, new FeReserveBand.Params(
                (int) ((long) CAPACITY * socPct / 100L), CAPACITY, generation, 0,
                DISCHARGE, CEILING, FeReserveBand.Band.DEFAULT));
    }

    @Test
    void aDeepReserveIsSpentWithoutRationing() {
        FeReserveReport r = at(80, 500);
        assertEquals(FeReserveReport.Stance.SURPLUS, r.stance());

        assertTrue(r.sustainedDraw() > CEILING, "nothing meaningful is held back at 80 percent");
        assertEquals(CEILING, r.runBudget(), "and the wire is what actually caps it");
    }

    @Test
    void theAllowanceIsProportionalToTheChargeAboveTheFloor() {
        FeReserveReport r = at(45, 0);
        assertEquals(3_500_000 / FeReserveBand.DEFAULT_RIDE_TICKS, r.sustainedDraw());
    }

    @Test
    void theAllowanceIsContinuousAndMonotoneAcrossTheWholeRange() {
        final int rampStep = DISCHARGE / Math.max(1, 100 - FeReserveBand.DEFAULT_HEALTHY_PCT) + 2;
        int previous = -1;
        for (int soc = 100; soc >= 0; soc--) {
            final int now = at(soc, 0).sustainedDraw();
            if (previous >= 0) {
                assertTrue(now <= previous,
                        "the allowance must never rise as charge falls, broke at " + soc + "%");
                assertTrue(previous - now <= rampStep,
                        "no cliff: " + previous + " to " + now + " across one percent at " + soc + "%");
            }
            previous = now;
        }
        assertEquals(0, at(10, 0).sustainedDraw(), "and it reaches exactly zero at the floor");
        assertEquals(0, at(0, 0).sustainedDraw());
    }

    @Test
    void insideTheBandTheReserveIsStillSpentRatherThanFrozen() {
        FeReserveReport r = at(20, 800);
        assertEquals(FeReserveReport.Stance.CONSERVE, r.stance());
        assertTrue(r.sustainedDraw() > 0, "charge above the floor is spendable wherever it sits");
        assertTrue(r.runBudget() > 800, "so the budget is more than bare generation");
    }

    @Test
    void aFullReserveIsSpentAtTheFullRateAndRampsThereRatherThanJumping() {
        assertEquals(DISCHARGE, at(100, 0).sustainedDraw(), "a full bank is not rationed");
        assertEquals(FeReserveReport.Stance.SURPLUS, at(70, 0).stance());

        assertTrue(at(100, 0).sustainedDraw() > at(80, 0).sustainedDraw());
        assertTrue(at(80, 0).sustainedDraw() > at(60, 0).sustainedDraw());
    }

    @Test
    void belowTheFloorOnlyCriticalDrawsAndItDrawsATrickle() {
        FeReserveReport r = at(5, 0);
        assertEquals(FeReserveReport.Stance.CRITICAL, r.stance());
        assertEquals(0, r.sustainedDraw(), "ordinary load is cut");
        assertTrue(r.criticalDraw() > 0, "but critical is never cut to zero while charge remains");
        assertTrue(r.criticalDraw() < 100,
                "and it is metered so the dregs last, not spent in seconds");
    }

    @Test
    void criticalAlwaysOutranksOrdinaryLoad() {
        for (int soc = 0; soc <= 100; soc += 5) {
            FeReserveReport r = at(soc, 0);
            assertTrue(r.criticalDraw() >= r.sustainedDraw(),
                    "critical must never be held to a tighter limit than ordinary load, at " + soc + "%");
            assertTrue(r.criticalBudget() >= r.runBudget(), "and the same for the budgets, at " + soc + "%");
        }
    }

    @Test
    void everyAllowanceIsCappedByTheWireHoweverDeepTheReserve() {
        FeReserveReport r = FeReserveBand.assess(0L, new FeReserveBand.Params(
                CAPACITY, CAPACITY, 1_000_000, 0, DISCHARGE, 512, FeReserveBand.Band.DEFAULT));
        assertEquals(512, r.runBudget(), "no amount of reserve gets more through a thin wire");
        assertEquals(512, r.criticalBudget());
    }

    @Test
    void aControllerWithNoStorageIsAPassThrough() {
        FeReserveReport r = FeReserveBand.assess(7L, new FeReserveBand.Params(
                0, 0, 2_400, 0, DISCHARGE, CEILING, FeReserveBand.Band.DEFAULT));
        assertEquals(0, r.sustainedDraw(), "there is no reserve to spend");
        assertEquals(2_400, r.runBudget(), "but everything generated still passes through");
        assertTrue(!r.isPresent(), "and it does not claim to be a reserve");
    }

    @Test
    void anEmptyReserveDoesNotPromiseWhatItCannotDeliver() {
        FeReserveReport r = at(0, 0);
        assertEquals(FeReserveReport.Stance.CRITICAL, r.stance());
        assertEquals(0, r.sustainedDraw());
        assertEquals(0, r.criticalDraw(), "nothing left means nothing offered");
        assertEquals(0, r.runBudget());
    }

    @Test
    void aBaseShortOfGenerationSettlesInsteadOfPulsing() {
        final int generation = 2_000;
        final int demand = 6_000;
        int stored = (int) (CAPACITY * 0.55);
        FeReserveReport.Stance stance = null;

        int directionChanges = 0;
        int lastDirection = 0;
        int previousStored = stored;

        for (int tick = 0; tick < 6_000; tick++) {
            final FeReserveReport r = FeReserveBand.assess(1L, new FeReserveBand.Params(
                    stored, CAPACITY, generation, 0, DISCHARGE, CEILING,
                    FeReserveBand.Band.DEFAULT, FeReserveBand.DEFAULT_RIDE_TICKS, stance));
            stance = r.stance();

            final int served = Math.min(demand, r.runBudget());
            final int fromReserve = Math.max(0, served - generation);
            final int spare = Math.max(0, generation - served);
            stored = Math.max(0, Math.min(CAPACITY, stored - fromReserve + spare));

            final int direction = Integer.compare(stored, previousStored);
            if (direction != 0 && lastDirection != 0 && direction != lastDirection) {
                directionChanges++;
            }
            if (direction != 0) {
                lastDirection = direction;
            }
            previousStored = stored;
        }

        assertTrue(directionChanges <= 2,
                "the reserve must glide, not sawtooth; it reversed " + directionChanges + " times");
        assertTrue(stored > 0, "and it must not be run flat");
        final int finalPct = (int) ((long) stored * 100L / CAPACITY);
        assertTrue(finalPct <= 12,
                "it should settle down at the floor rather than stalling high, ended at " + finalPct + "%");
    }

    @Test
    void theStanceLabelDoesNotFlickerOnABandEdge() {
        final int onTheEdge = (int) (CAPACITY * FeReserveBand.DEFAULT_CEILING_PCT / 100L);
        FeReserveReport.Stance stance = FeReserveReport.Stance.CONSERVE;
        for (int i = 0; i < 20; i++) {
            stance = FeReserveBand.assess(1L, new FeReserveBand.Params(
                    onTheEdge, CAPACITY, 0, 0, DISCHARGE, CEILING,
                    FeReserveBand.Band.DEFAULT, FeReserveBand.DEFAULT_RIDE_TICKS, stance)).stance();
            assertEquals(FeReserveReport.Stance.CONSERVE, stance,
                    "climbing out of conserve must cost more than sitting exactly on the edge");
        }
    }

    @Test
    void aBadlyConfiguredBandStillOrdersItself() {
        FeReserveBand.Band band = new FeReserveBand.Band(80, 20, 10);
        assertTrue(band.ceilingPct() >= band.floorPct());
        assertTrue(band.healthyPct() >= band.ceilingPct());
    }

    @Test
    void socIsReportedFromTheRealChargeRatherThanTheStance() {
        FeReserveReport r = at(37, 0);
        assertEquals(37, r.socPct());
        assertEquals(0.37f, r.fill(), 0.01f);
        assertTrue(r.isPresent());
    }

    private static final int FULL_RATE = 65_536 * 3;
    private static final int BIG = 500_000_000;

    private static FeReserveBand.Band sized(int capacity, int want) {
        return FeReserveBand.sized(FeReserveBand.Band.DEFAULT, FeReserveBand.Minutes.DEFAULT, capacity, want,
                FULL_RATE, FeReserveBand.DEFAULT_RIDE_TICKS);
    }

    private static FeReserveReport on(FeReserveBand.Band band, int capacity, int stored, int want) {
        return FeReserveBand.assess(1L, new FeReserveBand.Params(stored, capacity, 0, 0,
                Math.min(FULL_RATE, stored), CEILING * 4, band, FeReserveBand.DEFAULT_RIDE_TICKS, null, want));
    }

    @Test
    void aBigFarmKeepsWhatItsCriticalNeedsNotATenthOfEverything() {
        final FeReserveBand.Band band = sized(BIG, 2_000);
        assertEquals(5, band.floorPct(), "the floor is ten minutes of Critical, not 50M");
        assertEquals(15, band.ceilingPct(), "and the band's top thirty minutes, not 150M");

        assertEquals(53, band.healthyPct());

        final int at20 = BIG / 5;
        assertEquals(FeReserveReport.Stance.CONSERVE, on(FeReserveBand.Band.DEFAULT, BIG, at20, 2_000).stance(),
                "the old shape, kept so the fault stays visible");
        assertEquals(FeReserveReport.Stance.NOMINAL, on(band, BIG, at20, 2_000).stance());

        assertTrue(on(band, BIG, 40_000_000, 2_000).sustainedDraw() > 0, "40M is above a 25M floor");
        assertEquals(0, on(FeReserveBand.Band.DEFAULT, BIG, 40_000_000, 2_000).sustainedDraw());
    }

    @Test
    void aFarmTooSmallForTenMinutesOfItsCriticalKeepsThePercents() {
        assertEquals(FeReserveBand.Band.DEFAULT, sized(CAPACITY, 2_000));
        assertEquals(FeReserveBand.Band.DEFAULT, sized(CAPACITY, 50_000));
    }

    @Test
    void noBanksMeansThereIsNothingToSize() {
        assertEquals(FeReserveBand.Band.DEFAULT, sized(0, 2_000));
    }

    @Test
    void noCriticalMeansNoFloorAndTheBanksGlideInsteadOfDumping() {
        final FeReserveBand.Band small = sized(CAPACITY, 0);
        assertEquals(0, small.floorPct());
        assertEquals(0, small.ceilingPct());

        assertEquals(60, small.healthyPct());
        assertEquals(5_000_000 / FeReserveBand.DEFAULT_RIDE_TICKS,
                on(small, CAPACITY, 5_000_000, 0).sustainedDraw());
    }

    @Test
    void theAllowanceNeverOutrunsWhatTheChargeAboveTheFloorPaysForInARide() {
        final int[] farms = {300_000_000, BIG, 1_000_000_000, 2_000_000_000};
        final int[] wants = {0, 500, 2_000, 20_000};
        for (int capacity : farms) {
            for (int want : wants) {
                final FeReserveBand.Band band = sized(capacity, want);
                if (band.healthyPct() >= FeReserveBand.DEFAULT_HEALTHY_PCT) {
                    continue;
                }
                final long floorFe = (long) capacity * band.floorPct() / 100L;
                for (int soc = 0; soc <= 100; soc++) {
                    final int stored = (int) ((long) capacity * soc / 100L);
                    final long offered = (long) on(band, capacity, stored, want).sustainedDraw();
                    assertTrue(offered * FeReserveBand.DEFAULT_RIDE_TICKS <= Math.max(0, stored - floorFe)
                                    + FeReserveBand.DEFAULT_RIDE_TICKS,
                            "at " + soc + "% of " + capacity + " with " + want + " FE/t Critical, " + offered
                                    + " FE/t would empty the charge above the floor inside a ride");
                }
            }
        }
    }

    @Test
    void aSizedBandNeverHoldsMoreThanTheConfiguredOneAndStaysOrdered() {
        final java.util.Random rnd = new java.util.Random(198L);
        for (int i = 0; i < 20_000; i++) {
            final int capacity = 1 + rnd.nextInt(Integer.MAX_VALUE - 1);
            final int want = rnd.nextInt(4) == 0 ? 0 : rnd.nextInt(200_000);
            final FeReserveBand.Band caps = new FeReserveBand.Band(rnd.nextInt(96), rnd.nextInt(99), rnd.nextInt(101));
            final FeReserveBand.Minutes minutes = new FeReserveBand.Minutes(rnd.nextInt(120), rnd.nextInt(240),
                    rnd.nextInt(480));
            final FeReserveBand.Band band = FeReserveBand.sized(caps, minutes, capacity, want, rnd.nextInt(1_000_000),
                    1 + rnd.nextInt(72_000));
            final String at = capacity + " FE, " + want + " FE/t, caps " + caps + ", " + minutes;
            assertTrue(band.floorPct() <= caps.floorPct(), "floor over its cap: " + at);
            assertTrue(band.ceilingPct() <= caps.ceilingPct(), "top over its cap: " + at);
            assertTrue(band.healthyPct() <= caps.healthyPct(), "healthy over its cap: " + at);
            assertTrue(band.floorPct() <= band.ceilingPct() && band.ceilingPct() <= band.healthyPct(), "order: " + at);

            if (band.floorPct() < caps.floorPct()) {
                assertTrue((long) capacity * band.floorPct() / 100L + capacity / 100L
                        >= (long) want * minutes.floor() * FeReserveBand.TICKS_PER_MINUTE, "floor short: " + at);
            }
        }
    }

    @Test
    void criticalIsFundedInFullTheTickTheBanksCrossTheFloor() {
        final int floor = CAPACITY / 10;
        for (int want : new int[]{100, 500, 800, 1_000, 4_000}) {
            final int above = on(FeReserveBand.Band.DEFAULT, CAPACITY, floor + 1, want).criticalDraw();
            final int below = on(FeReserveBand.Band.DEFAULT, CAPACITY, floor - 1, want).criticalDraw();
            assertTrue(below >= Math.min(want, above) - 1,
                    want + " FE/t of Critical: " + above + " FE/t above the floor, " + below + " below");
            assertTrue(below <= above, "and never more below the floor than above it, at " + want);
            assertTrue(below <= want, "and never more than Critical asks, or the plan hands the rest to ordinary"
                    + " circuits under the floor, at " + want);
        }

        final int ride = FeReserveBand.DEFAULT_RIDE_TICKS;
        assertEquals(500, on(FeReserveBand.Band.DEFAULT, CAPACITY, 500 * ride + 1, 500).criticalDraw());
        assertEquals(250, on(FeReserveBand.Band.DEFAULT, CAPACITY, 250 * ride, 500).criticalDraw());
    }

    @Test
    void theOldTrickleStillShowsTheCliffItHad() {
        final int floor = CAPACITY / 10;
        assertEquals(833, on(FeReserveBand.Band.DEFAULT, CAPACITY, floor + 1, FeReserveBand.WANT_UNSTATED)
                .criticalDraw());
        assertEquals(83, on(FeReserveBand.Band.DEFAULT, CAPACITY, floor - 1, FeReserveBand.WANT_UNSTATED)
                .criticalDraw());
    }

    @Test
    void aCallerThatStatesNoWantChangesNothingAnywhere() {
        for (int soc = 0; soc <= 100; soc++) {
            final int stored = (int) ((long) CAPACITY * soc / 100L);
            final FeReserveReport before = FeReserveBand.assess(3L, new FeReserveBand.Params(stored, CAPACITY, 700,
                    0, DISCHARGE, CEILING, FeReserveBand.Band.DEFAULT, FeReserveBand.DEFAULT_RIDE_TICKS, null));
            final FeReserveReport after = FeReserveBand.assess(3L, new FeReserveBand.Params(stored, CAPACITY, 700,
                    0, DISCHARGE, CEILING, FeReserveBand.Band.DEFAULT, FeReserveBand.DEFAULT_RIDE_TICKS, null,
                    FeReserveBand.WANT_UNSTATED));
            assertEquals(before, after, "at " + soc + "%");
        }
    }

    @Test
    void aStatedWantOfNothingTakesNothingUnderTheFloor() {
        final int floor = CAPACITY / 10;
        assertEquals(0, on(FeReserveBand.Band.DEFAULT, CAPACITY, floor - 1, 0).criticalDraw());
        assertEquals(0, on(FeReserveBand.Band.DEFAULT, CAPACITY, floor - 1, 0).sustainedDraw());

        assertEquals(on(FeReserveBand.Band.DEFAULT, CAPACITY, floor * 3, FeReserveBand.WANT_UNSTATED),
                on(FeReserveBand.Band.DEFAULT, CAPACITY, floor * 3, 0));
    }

    @Test
    void aSizedFloorRunsItsCriticalForItsTenMinutes() {
        final FeReserveBand.Band band = sized(BIG, 2_000);
        int stored = (int) ((long) BIG * band.floorPct() / 100L);
        int whole = 0;
        while (true) {
            final int offered = on(band, BIG, stored, 2_000).criticalDraw();
            if (offered < 2_000) {
                break;
            }
            stored -= 2_000;
            whole++;
        }
        assertTrue(whole >= 9 * FeReserveBand.TICKS_PER_MINUTE, "ran " + whole + " ticks on its floor");
        assertTrue(stored <= 2_000 * FeReserveBand.DEFAULT_RIDE_TICKS, "and left no more than a minute of it: " + stored);
    }

    @Test
    void theWantRisesAtOnceAndFallsOnlyAfterADay() {
        final FeReserveBand.WantPeak peak = new FeReserveBand.WantPeak();
        assertEquals(2_000, peak.update(2_000, 100));
        assertEquals(5_000, peak.update(5_000, 101), "more Critical is protected at once");
        assertEquals(5_000, peak.update(1_000, 102), "a fall waits");
        assertEquals(5_000, peak.update(1_000, 101 + FeReserveBand.WANT_HOLD_TICKS - 1));
        assertEquals(1_000, peak.update(1_000, 101 + FeReserveBand.WANT_HOLD_TICKS), "and is followed after a day");

        final FeReserveBand.WantPeak lamp = new FeReserveBand.WantPeak();
        lamp.update(600, 0);
        for (long t = 1; t < FeReserveBand.WANT_HOLD_TICKS; t++) {
            assertEquals(600, lamp.update(0, t), "at tick " + t);
        }

        final FeReserveBand.WantPeak reloaded = new FeReserveBand.WantPeak();
        reloaded.restore(lamp.want(), lamp.since());
        assertEquals(600, reloaded.update(0, FeReserveBand.WANT_HOLD_TICKS - 1));
        assertEquals(0, reloaded.update(0, FeReserveBand.WANT_HOLD_TICKS));

        final FeReserveBand.WantPeak cycling = new FeReserveBand.WantPeak();
        for (long t = 0; t < 10 * FeReserveBand.WANT_HOLD_TICKS; t++) {
            assertEquals(3_000, cycling.update(t % 40 < 20 ? 3_000 : 1_000, t), "at tick " + t);
        }
    }
}
