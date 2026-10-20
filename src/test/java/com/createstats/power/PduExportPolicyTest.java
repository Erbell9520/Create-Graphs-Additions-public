package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PduExportPolicyTest {
    private static final int CAPACITY = 512_000;
    private static final int FLOOR_PCT = 15;
    private static final int RESTORE_PCT = 45;
    private static final int LEG = 4096;

    private static int at(int pct, int spareGeneration) {
        return PduExportPolicy.allowance(Math.round(CAPACITY * (pct / 100.0f)), CAPACITY,
                FLOOR_PCT, RESTORE_PCT, spareGeneration, LEG);
    }

    @Test
    @DisplayName("nothing leaves below the reserve floor, however much is being asked for")
    void theRideThroughIsNotExportable() {
        assertEquals(0, at(0, 100_000));
        assertEquals(0, at(10, 100_000));
        assertEquals(0, at(15, 100_000), "exactly at the floor is not above it");
    }

    @Test
    @DisplayName("between the floor and the restore mark the allowance climbs toward live surplus")
    void theMiddleBandSpendsGenerationAndNotCharge() {
        assertEquals(0, at(15, 500), "at the floor, nothing");
        assertEquals(250, at(30, 500), "half way to the mark, half the surplus");
        assertEquals(500, at(45, 500), "at the mark, all of it");
        assertEquals(0, at(30, 0), "generation fully spoken for means nothing to export");
        assertEquals(0, at(30, -900), "and a panel already eating its buffer exports nothing at all");
    }

    @Test
    @DisplayName("above the restore mark the allowance climbs from the surplus up to the wire")
    void aHealthyPanelExportsFreely() {
        assertEquals(500, at(45, 500), "at the mark it is still exactly the surplus");
        assertTrue(at(60, 500) > 500 && at(60, 500) < LEG, "and climbs from there");
        assertEquals(LEG, at(100, 500), "reaching the wire only when the buffer is full");
    }

    @Test
    @DisplayName("the curve has no step anywhere, which is what stops the chain hunting")
    void theAllowanceIsContinuousAcrossTheWholeRange() {
        final int maxStep = LEG / 8;
        int previous = at(0, 500);
        for (int pct = 1; pct <= 100; pct++) {
            final int here = at(pct, 500);
            assertTrue(Math.abs(here - previous) <= maxStep,
                    "a cliff at " + pct + "%: " + previous + " -> " + here);
            assertTrue(here >= previous, "the allowance fell as the buffer rose, at " + pct + "%");
            previous = here;
        }
    }

    @Test
    @DisplayName("even a full panel never exports into its own ride-through")
    void theFloorSurvivesEveryBand() {
        final int allowance = PduExportPolicy.allowance(1000, 1000, 90, 95, 100_000, LEG);
        assertEquals(100, allowance, "1,000 less a 900 floor");
    }

    @Test
    @DisplayName("the leg's own rating still binds")
    void theWireIsAlwaysTheCeiling() {
        assertEquals(64, PduExportPolicy.allowance(CAPACITY, CAPACITY, FLOOR_PCT, RESTORE_PCT,
                100_000, 64));
        assertEquals(0, PduExportPolicy.allowance(CAPACITY, CAPACITY, FLOOR_PCT, RESTORE_PCT,
                100_000, 0), "no conduit on the boss is no export");
    }

    @Test
    @DisplayName("the bands are monotonic, so a falling buffer is a ramp and not a cliff")
    void fallingChargeNeverIncreasesTheAllowance() {
        int previous = Integer.MAX_VALUE;
        for (int pct = 100; pct >= 0; pct -= 5) {
            final int allowance = at(pct, 200);
            assertTrue(allowance <= previous,
                    "allowance rose as the buffer fell, at " + pct + "%: " + allowance + " > " + previous);
            previous = allowance;
        }
        assertEquals(0, previous);
    }

    @Test
    @DisplayName("a restore mark under the floor cannot invert the bands")
    void aMisconfiguredBandStillBehaves() {
        final int allowance = PduExportPolicy.allowance(Math.round(CAPACITY * 0.2f), CAPACITY,
                50, 10, 0, LEG);
        assertEquals(0, allowance, "under a 50 percent floor nothing leaves, whatever restore says");
    }

    @Test
    @DisplayName("spare generation is what is left after everything already spoken for")
    void spareIsWhatNobodyElseIsUsing() {
        assertEquals(1000, PduExportPolicy.spareGeneration(3240, 2000, 51, 189));
        assertEquals(0, PduExportPolicy.spareGeneration(900, 2000, 51, 0),
                "a panel running on its buffer has no spare generation to give away");
        assertEquals(0, PduExportPolicy.spareGeneration(0, 0, 0, 0));
    }

    @Test
    @DisplayName("the reported case: 3,240 generated, 911 running, and the export is what is left")
    void theScreenshotCase() {
        final int spare = PduExportPolicy.spareGeneration(3240, 911, 51, 0);
        assertEquals(2278, spare);

        assertTrue(at(77, spare) > LEG / 2, "a healthy board still feeds its chain");

        assertTrue(at(33, spare) > 0 && at(33, spare) < spare,
                "on the way down it is a share of the surplus, not all of it");

        assertEquals(0, at(33, PduExportPolicy.spareGeneration(3240, 3240, 51, 0)));
    }
}
