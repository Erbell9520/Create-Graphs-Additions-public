package com.createstats.power;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeReserveReportCombineTest {
    private static FeReserveReport report(long pos, int stored, int capacity, int generation,
                                          int sustained, int critical, int claim,
                                          FeReserveReport.Stance stance) {
        return new FeReserveReport(pos, stored, capacity, generation, 0, sustained, critical, claim,
                32_768, 10, 30, stance);
    }

    @Test
    void nothingPresentIsNoLink() {
        assertSame(FeReserveReport.NONE, FeReserveReport.combine(List.of()));

        assertSame(FeReserveReport.NONE, FeReserveReport.combine(List.of(
                report(1L, 0, 0, 500, 0, 0, 0, FeReserveReport.Stance.CONSERVE))));
    }

    @Test
    void oneReportIsPassedThroughUntouched() {
        final FeReserveReport only =
                report(7L, 500, 1_000, 400, 30, 90, 0, FeReserveReport.Stance.NOMINAL);
        assertSame(only, FeReserveReport.combine(List.of(only)));
    }

    @Test
    void quantitiesAddUpBecauseTwoWiresCarryTwoWires() {
        final FeReserveReport a =
                report(1L, 1_000, 4_000, 600, 40, 120, 50, FeReserveReport.Stance.NOMINAL);
        final FeReserveReport b =
                report(2L, 3_000, 6_000, 400, 20, 80, 25, FeReserveReport.Stance.NOMINAL);
        final FeReserveReport both = FeReserveReport.combine(List.of(a, b));

        assertEquals(4_000, both.stored());
        assertEquals(10_000, both.capacity());
        assertEquals(40, both.socPct(), "charge against pooled capacity, not an average of two");
        assertEquals(1_000, both.generation());
        assertEquals(60, both.sustainedDraw());
        assertEquals(200, both.criticalDraw());
        assertEquals(75, both.chargeClaim());
        assertEquals(65_536, both.outputCeiling(), "two conductors in parallel");
    }

    @Test
    void theWorstStanceWins() {
        final FeReserveReport healthy =
                report(1L, 9_000, 10_000, 600, 900, 900, 0, FeReserveReport.Stance.SURPLUS);
        final FeReserveReport flat =
                report(2L, 100, 10_000, 100, 0, 5, 0, FeReserveReport.Stance.CRITICAL);
        assertEquals(FeReserveReport.Stance.CRITICAL,
                FeReserveReport.combine(List.of(healthy, flat)).stance());
        assertEquals(FeReserveReport.Stance.CRITICAL,
                FeReserveReport.combine(List.of(flat, healthy)).stance(),
                "and the order the controllers ticked in cannot change it");
    }

    @Test
    void theBandIsQuotedFromTheDeepestContributor() {
        final FeReserveReport small = new FeReserveReport(1L, 500, 1_000, 100, 0, 10, 10, 0,
                1_024, 5, 15, FeReserveReport.Stance.NOMINAL);
        final FeReserveReport big = new FeReserveReport(2L, 5_000, 90_000, 100, 0, 10, 10, 0,
                1_024, 20, 60, FeReserveReport.Stance.NOMINAL);
        final FeReserveReport both = FeReserveReport.combine(List.of(small, big));
        assertEquals(20, both.floorPct());
        assertEquals(60, both.ceilingPct());
        assertEquals(2L, both.controllerPos(), "and it is named as the one the band came from");
    }

    @Test
    void theClaimStaysInsideTheGenerationItIsClaimedFrom() {
        final FeReserveReport a =
                report(1L, 1_000, 2_000, 100, 0, 0, 100, FeReserveReport.Stance.CONSERVE);
        final FeReserveReport b =
                report(2L, 1_000, 2_000, 100, 0, 0, 100, FeReserveReport.Stance.CONSERVE);
        final FeReserveReport both = FeReserveReport.combine(List.of(a, b));
        assertTrue(both.chargeClaim() <= both.generation());
        assertEquals(0, both.runBudget(), "everything generated is going into the banks");
    }

    @Test
    void aBlendedReportStillGuaranteesCriticalOutranksOrdinary() {
        final FeReserveReport a =
                report(1L, 1_000, 2_000, 0, 500, 0, 0, FeReserveReport.Stance.NOMINAL);
        final FeReserveReport b =
                report(2L, 1_000, 2_000, 0, 500, 0, 0, FeReserveReport.Stance.NOMINAL);
        final FeReserveReport both = FeReserveReport.combine(List.of(a, b));
        assertTrue(both.criticalDraw() >= both.sustainedDraw());
    }
}
