package com.createstats.blockentity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywheelBankTest {
    private static final float EFFICIENCY = 0.90f;
    private static final float RATED = 32_768f;
    private static final float RESERVE = 3_276_800f;
    private static final float FRICTION = 0.005f * RATED;
    private static final float CUTOFF = 250f;

    private static float sustain(float reserve, float delivered) {
        return FlywheelBankPlan.sustainSeconds(reserve - CUTOFF, delivered, EFFICIENCY, FRICTION);
    }

    @Test
    void fullLoadRunsForTheRatedNinetySeconds() {
        assertEquals(90f, sustain(RESERVE, RATED), 2f);
    }

    @Test
    void lighterLoadsRunProportionallyLonger() {
        float full = sustain(RESERVE, RATED);
        float quarter = sustain(RESERVE, RATED / 4f);
        float twentieth = sustain(RESERVE, RATED / 20f);

        assertTrue(quarter > full * 3.5f, "quarter load should run well over three times as long, was "
            + quarter / full + "x");
        assertTrue(twentieth > full * 14f, "five per cent load should run over fourteen times as long, was "
            + twentieth / full + "x");
    }

    @Test
    void anUnusedBankCoastsForHours() {
        float idle = sustain(RESERVE, 0f);
        assertTrue(idle > 4 * 3600f, "an undrawn bank should coast for hours, was " + idle / 3600f + "h");
    }

    @Test
    void theDrainAndTheEstimateAreTheSameNumber() {
        float delivered = RATED * 0.37f;
        float drain = FlywheelBankPlan.drainPerSecond(delivered, EFFICIENCY, FRICTION);
        float usable = RESERVE - CUTOFF;
        float seconds = FlywheelBankPlan.sustainSeconds(usable, delivered, EFFICIENCY, FRICTION);

        assertEquals(usable, drain * seconds, usable * 1e-4f);
    }

    @Test
    void oneBankTakesTheWholeDemandUpToItsRating() {
        float[] alloc = FlywheelBankPlan.share(20_000f, new float[] {RESERVE}, new float[] {RATED});
        assertEquals(20_000f, alloc[0], 1f);

        float[] capped = FlywheelBankPlan.share(50_000f, new float[] {RESERVE}, new float[] {RATED});
        assertEquals(RATED, capped[0], 1f);
    }

    @Test
    void sharesAlwaysAddUpToTheDemand() {
        float[] weight = {RESERVE, RESERVE * 0.4f, RESERVE * 0.05f};
        float[] cap = {RATED, RATED, RATED};
        float demand = 61_000f;

        float[] alloc = FlywheelBankPlan.share(demand, weight, cap);
        assertEquals(demand, alloc[0] + alloc[1] + alloc[2], 1f);
    }

    @Test
    void aCappedBankHandsTheRemainderToTheOthers() {
        float[] weight = {RESERVE, RESERVE, RESERVE};
        float[] cap = {RATED, RATED, RATED / 8f};
        float demand = RATED * 2f;

        float[] alloc = FlywheelBankPlan.share(demand, weight, cap);
        assertEquals(RATED / 8f, alloc[2], 1f, "the small bank should be pinned at its rating");
        assertEquals(demand, alloc[0] + alloc[1] + alloc[2], 1f);
        assertTrue(alloc[0] <= RATED + 1f && alloc[1] <= RATED + 1f);
    }

    @Test
    void aBankAtItsCutoffCarriesNothing() {
        float[] weight = {RESERVE, 0f};
        float[] cap = {RATED, 0f};
        float[] alloc = FlywheelBankPlan.share(10_000f, weight, cap);

        assertEquals(0f, alloc[1], 1e-3f);
        assertEquals(10_000f, alloc[0], 1f);
    }

    @Test
    void demandBeyondTheGroupsRatingIsCappedNotInvented() {
        float[] weight = {RESERVE, RESERVE};
        float[] cap = {RATED, RATED};
        float[] alloc = FlywheelBankPlan.share(RATED * 10f, weight, cap);

        assertEquals(RATED * 2f, alloc[0] + alloc[1], 1f);
    }

    @Test
    void twoUnevenBanksConvergeAndEmptyTogether() {
        float[] reserve = {RESERVE, RESERVE * 0.30f};
        float demand = RATED * 1.2f;

        float startGap = Math.abs(reserve[0] - reserve[1]) / RESERVE;
        int ticks = 0;
        while (reserve[0] > CUTOFF && reserve[1] > CUTOFF && ticks < 20 * 600) {
            float[] weight = {Math.max(0f, reserve[0] - CUTOFF), Math.max(0f, reserve[1] - CUTOFF)};
            float[] cap = {RATED, RATED};
            float[] alloc = FlywheelBankPlan.share(demand, weight, cap);
            for (int i = 0; i < 2; i++) {
                reserve[i] -= FlywheelBankPlan.drainPerSecond(alloc[i], EFFICIENCY, FRICTION) / 20f;
            }
            ticks++;
        }

        float endGap = Math.abs(reserve[0] - reserve[1]) / RESERVE;
        assertTrue(endGap < startGap * 0.2f,
            "the gap should close, went from " + startGap + " to " + endGap);
        assertTrue(endGap < 0.06f, "both banks should reach the cutoff together, gap was " + endGap);
    }

    @Test
    void aCapacitySplitWouldNotHaveConverged() {
        float[] reserve = {RESERVE, RESERVE * 0.30f};
        float demand = RATED * 1.2f;
        float startGap = Math.abs(reserve[0] - reserve[1]);

        for (int t = 0; t < 20 * 60 && reserve[1] > CUTOFF; t++) {
            float half = demand / 2f;
            for (int i = 0; i < 2; i++) {
                reserve[i] -= FlywheelBankPlan.drainPerSecond(half, EFFICIENCY, FRICTION) / 20f;
            }
        }

        float endGap = Math.abs(reserve[0] - reserve[1]);
        assertTrue(endGap > startGap * 0.98f,
            "an equal split leaves the gap where it started, which is the old bug: "
                + startGap + " to " + endGap);
    }

    @Test
    void surplusIsSplitByRoomSoAGroupTopsOffTogether() {
        float surplus = 12_000f;
        float roomA = RESERVE * 0.10f;
        float roomB = RESERVE * 0.90f;
        float total = roomA + roomB;

        float a = FlywheelBankPlan.chargeShare(surplus, roomA, total);
        float b = FlywheelBankPlan.chargeShare(surplus, roomB, total);

        assertEquals(surplus, a + b, 1f);
        assertTrue(b > a * 8f, "the emptier bank should take most of the surplus");

        assertEquals(a / roomA, b / roomB, 1e-4f);
    }

    @Test
    void aFullBankTakesNoneOfTheSurplus() {
        assertEquals(0f, FlywheelBankPlan.chargeShare(12_000f, 0f, 12_000f), 1e-4f);
    }

    private static final float ENGAGE = 0.98f;
    private static final float RELEASE = 0.85f;

    private static int settleLatch(int banks, float stress, float gridCapacity, int ticks) {
        boolean[] engaged = new boolean[banks];
        int flips = 0;
        for (int t = 0; t < ticks; t++) {
            for (int i = 0; i < banks; i++) {
                boolean next = FlywheelBankPlan.assistLatch(engaged[i], stress, gridCapacity, ENGAGE, RELEASE);
                if (next != engaged[i]) {
                    flips++;
                }
                engaged[i] = next;
            }
        }
        return flips;
    }

    @Test
    void aGridThatCanCopeNeverCallsForAssist() {
        assertFalse(FlywheelBankPlan.assistLatch(false, 5_000f, 10_000f, ENGAGE, RELEASE));
    }

    @Test
    void aGridOverItsCapacityCallsForAssist() {
        assertTrue(FlywheelBankPlan.assistLatch(false, 10_500f, 10_000f, ENGAGE, RELEASE));
    }

    @Test
    void anEngagedBankDoesNotLetGoAtTheThresholdItEngagedAt() {
        assertTrue(FlywheelBankPlan.assistLatch(true, 9_900f, 10_000f, ENGAGE, RELEASE));
        assertTrue(FlywheelBankPlan.assistLatch(true, 8_600f, 10_000f, ENGAGE, RELEASE));
        assertFalse(FlywheelBankPlan.assistLatch(true, 8_400f, 10_000f, ENGAGE, RELEASE));
    }

    @Test
    void oneBankSettlesAndStopsFlipping() {
        assertEquals(1, settleLatch(1, 10_500f, 10_000f, 400));
    }

    @Test
    void threeBanksOnOneGridAllSettleTogether() {
        assertEquals(3, settleLatch(3, 10_500f, 10_000f, 400));
    }

    @Test
    void theLatchReleasesOnceTheGridCanCopeAgain() {
        boolean engaged = FlywheelBankPlan.assistLatch(false, 10_500f, 10_000f, ENGAGE, RELEASE);
        assertTrue(engaged);

        engaged = FlywheelBankPlan.assistLatch(engaged, 6_000f, 10_000f, ENGAGE, RELEASE);
        assertFalse(engaged);
    }

    @Test
    void aDeadGridNeverLatchesOnItsOwnCapacity() {
        assertTrue(FlywheelBankPlan.assistLatch(false, 1f, 0f, ENGAGE, RELEASE));
        assertTrue(FlywheelBankPlan.assistLatch(true, 1f, 0f, ENGAGE, RELEASE));
    }

    private static final float CHARGE_ENGAGE = RELEASE;
    private static final float CHARGE_RELEASE = ENGAGE;

    @Test
    void aGridWithRoomToSpareIsChargedFrom() {
        assertTrue(FlywheelBankPlan.chargeLatch(false, 5_000f, 10_000f, CHARGE_ENGAGE, CHARGE_RELEASE));
    }

    @Test
    void aGridThatNeededAssistIsNeverChargedFrom() {
        assertFalse(FlywheelBankPlan.chargeLatch(false, 9_800f, 10_000f, CHARGE_ENGAGE, CHARGE_RELEASE));
        assertFalse(FlywheelBankPlan.chargeLatch(false, 12_000f, 10_000f, CHARGE_ENGAGE, CHARGE_RELEASE));

        assertFalse(FlywheelBankPlan.chargeLatch(false, 8_600f, 10_000f, CHARGE_ENGAGE, CHARGE_RELEASE));
    }

    @Test
    void aGridOnTheEdgeIsNotChargedFrom() {
        assertFalse(FlywheelBankPlan.chargeLatch(false, 9_500f, 10_000f, CHARGE_ENGAGE, CHARGE_RELEASE));
        assertTrue(FlywheelBankPlan.chargeLatch(false, 8_400f, 10_000f, CHARGE_ENGAGE, CHARGE_RELEASE));
    }

    @Test
    void aChargingBankDoesNotDropOutTheMomentItsOwnDrawLandsOnTheLine() {
        boolean engaged = FlywheelBankPlan.chargeLatch(false, 8_000f, 10_000f, CHARGE_ENGAGE, CHARGE_RELEASE);
        assertTrue(engaged);
        assertTrue(FlywheelBankPlan.chargeLatch(engaged, 9_500f, 10_000f, CHARGE_ENGAGE, CHARGE_RELEASE));
    }

    @Test
    void aChargingBankLetsGoWhenTheGridRunsOutOfHeadroom() {
        boolean engaged = FlywheelBankPlan.chargeLatch(false, 5_000f, 10_000f, CHARGE_ENGAGE, CHARGE_RELEASE);
        assertTrue(FlywheelBankPlan.chargeLatch(engaged, 9_900f, 10_000f, CHARGE_ENGAGE, CHARGE_RELEASE) == false);
    }

    @Test
    void aDeadGridIsNeverChargedFrom() {
        assertFalse(FlywheelBankPlan.chargeLatch(false, 0f, 0f, CHARGE_ENGAGE, CHARGE_RELEASE));
        assertFalse(FlywheelBankPlan.chargeLatch(true, 0f, 0f, CHARGE_ENGAGE, CHARGE_RELEASE));
    }

    @Test
    void theAssistAndChargeLatchesCannotBothBeOnAtOnce() {
        for (int pct = 0; pct <= 130; pct++) {
            float load = 10_000f * pct / 100f;
            boolean assisting = FlywheelBankPlan.assistLatch(false, load, 10_000f, ENGAGE, RELEASE);
            boolean charging = FlywheelBankPlan.chargeLatch(false, load, 10_000f, CHARGE_ENGAGE, CHARGE_RELEASE);
            assertFalse(assisting && charging, "both latches engaged at " + pct + "% load");
        }
    }

    private static final float RATED_RPM = 256f;
    private static final float EPS = 1f;

    @Test
    void underALiveSourceTheBankNeverOutrunsTheLine() {
        for (float live = 1.5f; live <= RATED_RPM; live += 0.37f) {
            for (float target : new float[] {8f, 30f, 64f, 94.72f, 256f, 400f}) {
                float held = FlywheelBankPlan.heldSpeed(target, RATED_RPM, 1f, true, live, EPS);
                assertTrue(held <= live, "held " + held + " RPM on a line turning at " + live);
                assertTrue(held <= Math.min(target, RATED_RPM) + 1e-4f);
            }
        }
    }

    @Test
    void theCasesThatUsedToRoundUpNowMatchTheLine() {
        assertEquals(30f, FlywheelBankPlan.heldSpeed(30f, RATED_RPM, 1f, true, 30f, EPS), 1e-4f);
        assertEquals(94.72f, FlywheelBankPlan.heldSpeed(94.72f, RATED_RPM, 1f, true, 94.72f, EPS), 1e-4f);
        assertEquals(6f, FlywheelBankPlan.heldSpeed(6f, RATED_RPM, 1f, true, 6f, EPS), 1e-4f);
    }

    @Test
    void theDerateFactorOnlyAppliesWhenTheBanksSetTheSpeed() {
        assertEquals(64f, FlywheelBankPlan.heldSpeed(128f, RATED_RPM, 0.5f, false, 0f, EPS), 1e-4f);

        assertEquals(96f, FlywheelBankPlan.heldSpeed(128f, RATED_RPM, 0.5f, true, 96f, EPS), 1e-4f);

        assertEquals(RATED_RPM, FlywheelBankPlan.heldSpeed(1000f, RATED_RPM, 1f, false, 0f, EPS), 1e-4f);
    }

    @Test
    void aLoadTooHeavyAtFullSpeedIsCarriedSlower() {
        float load = 9 * 19_660.8f;
        float factor = FlywheelBankPlan.fitFactor(RATED, load, 0.9f);
        assertEquals(RATED * 0.9f / load, factor, 1e-5f);
        assertTrue(factor * RATED_RPM >= 4f, "still a useful speed, was " + factor * RATED_RPM + " RPM");
        assertEquals(1f, FlywheelBankPlan.fitFactor(RATED, RATED * 0.5f, 0.9f), 1e-6f);
        assertEquals(1f, FlywheelBankPlan.fitFactor(RATED, 0f, 0.9f), 1e-6f);
        assertEquals(0f, FlywheelBankPlan.fitFactor(0f, 1_000f, 0.9f), 1e-6f);
    }

    @Test
    void backupStartsAtFullSpeedUnlessTheLineWouldOverloadIt() {
        assertEquals(1f, FlywheelBankPlan.startFactor(RATED, RATED * 0.95f, 0.9f, 0.97f), 1e-6f);
        assertEquals(0.6f, FlywheelBankPlan.startFactor(RATED, RATED * 1.5f, 0.9f, 0.97f), 1e-5f);
    }

    private static final class Line {
        float factor = 1f;
        int stable;
        int changes;

        float stress(float loadAtFull) {
            return loadAtFull * factor;
        }

        void run(float capacity, float loadAtFull, int ticks) {
            for (int t = 0; t < ticks; t++) {
                float s = stress(loadAtFull);
                float next = FlywheelBankPlan.steerFactor(factor, capacity, s, s > capacity, stable,
                    0.9f, 0.97f, 0.75f, 60);
                if (next != factor) {
                    factor = next;
                    stable = 0;
                    changes++;
                } else {
                    stable++;
                }
            }
        }
    }

    @Test
    void anOverloadedLineIsSlowedOnceAndThenLeftAlone() {
        Line line = new Line();
        line.run(RATED, RATED * 3f, 400);
        assertEquals(1, line.changes, "one change to fit the load, then nothing");
        assertTrue(line.stress(RATED * 3f) <= RATED, "the slowed line must fit");
        assertEquals(0.3f, line.factor, 1e-5f);
    }

    @Test
    void aLighterLoadLetsTheLineBackUpAfterAPause() {
        Line line = new Line();
        line.run(RATED, RATED * 3f, 1);
        float slowed = line.factor;

        line.run(RATED, RATED * 1.5f, 59);
        assertEquals(slowed, line.factor, 1e-6f, "no raise before the quiet period");
        line.run(RATED, RATED * 1.5f, 200);
        assertEquals(0.6f, line.factor, 1e-5f);
        assertTrue(line.stress(RATED * 1.5f) <= RATED * 0.97f);
        assertEquals(2, line.changes, "one change down, one back up, no hunting");
    }

    @Test
    void theSteeringNeverOscillatesAcrossTheWholeLoadRange() {
        for (float multiple = 0.1f; multiple <= 20f; multiple += 0.1f) {
            Line line = new Line();
            line.run(RATED, RATED * multiple, 400);
            assertTrue(line.changes <= 1, "hunted " + line.changes + " times at " + multiple + "x rated");
            assertTrue(line.stress(RATED * multiple) <= RATED * 0.97f + 1f,
                "settled overloaded at " + multiple + "x rated");
            assertTrue(line.factor <= 1f);
        }
    }

    @Test
    void theFactorNeverRisesAboveFullSpeed() {
        Line line = new Line();
        line.run(RATED, RATED * 2f, 50);
        line.run(RATED, RATED * 0.01f, 400);
        assertEquals(1f, line.factor, 1e-6f);
    }
}
