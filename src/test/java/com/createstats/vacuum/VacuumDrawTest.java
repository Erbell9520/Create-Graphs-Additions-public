package com.createstats.vacuum;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VacuumDrawTest {
    @Test
    void theCollectorFanGrowsWithTheDamper() {
        assertEquals(6, VacuumDraw.collectorFan(1));
        assertEquals(20, VacuumDraw.collectorFan(8));
        for (int r = VacuumDraw.COLLECTOR_MIN_RADIUS; r < VacuumDraw.COLLECTOR_MAX_RADIUS; r++) {
            assertTrue(VacuumDraw.collectorFan(r + 1) > VacuumDraw.collectorFan(r),
                    "a wider damper passes more air and must cost more: radius " + r);
        }
    }

    @Test
    void aCorruptRadiusNeverQuotesANonsenseDraw() {
        assertEquals(VacuumDraw.collectorFan(1), VacuumDraw.collectorFan(-40));
        assertEquals(VacuumDraw.collectorFan(8), VacuumDraw.collectorFan(9999));
    }

    @Test
    void everyWorstCaseFitsOneOutlet() {
        for (int r = VacuumDraw.COLLECTOR_MIN_RADIUS; r <= VacuumDraw.COLLECTOR_MAX_RADIUS; r++) {
            assertTrue(VacuumDraw.collectorPeak(r) <= VacuumDraw.OUTLET_RATING, "collector at " + r);
        }
        assertTrue(VacuumDraw.cleanerPeak() <= VacuumDraw.OUTLET_RATING);
        assertTrue(VacuumDraw.snifferPeak() <= VacuumDraw.OUTLET_RATING);
    }

    @Test
    void theOutletRatingMirrorIsTheOutlet() {
        assertEquals(com.createstats.blockentity.WallOutletBE.RATED_FE_PER_TICK, VacuumDraw.OUTLET_RATING);
    }

    @Test
    void everyBufferRidesOutASecondAtPeak() {
        assertTrue(VacuumDraw.COLLECTOR_BUFFER >= 20 * VacuumDraw.collectorPeak(8));
        assertTrue(VacuumDraw.CLEANER_BUFFER >= 20 * VacuumDraw.cleanerPeak());
        assertTrue(VacuumDraw.SNIFFER_BUFFER >= 20 * VacuumDraw.snifferPeak());
    }

    @Test
    void theCleanersWorstTickIncludesALiftedBlock() {
        assertEquals(VacuumDraw.CLEANER_SUCTION + 2 * VacuumDraw.CLEANER_PER_ITEM + VacuumDraw.CLEANER_PER_BLOCK,
                VacuumDraw.cleanerPeak());
        assertTrue(VacuumDraw.CLEANER_PER_BLOCK > VacuumDraw.CLEANER_PER_ITEM,
                "a block's worth of material costs more to lift than one item");
    }

    @Test
    void nothingIsCaughtOnCredit() {
        assertTrue(VacuumDraw.canAfford(52, 20, 32));
        assertFalse(VacuumDraw.canAfford(51, 20, 32));
        assertFalse(VacuumDraw.canAfford(0, 0, 1));
    }

    @Test
    void aCollectorSwitchedOffAtItsButtonDrawsNothing() {
        for (int r = VacuumDraw.COLLECTOR_MIN_RADIUS; r <= VacuumDraw.COLLECTOR_MAX_RADIUS; r++) {
            assertEquals(0, VacuumDraw.collectorRated(false, false, false, r), "switched off, radius " + r);
            assertEquals(VacuumDraw.collectorFan(r), VacuumDraw.collectorRated(true, false, false, r),
                    "switched on, radius " + r);
            assertEquals(0, VacuumDraw.collectorRated(true, true, false, r), "held by redstone, radius " + r);
            assertEquals(0, VacuumDraw.collectorRated(true, false, true, r), "forced off at a panel, radius " + r);
        }
    }
}
