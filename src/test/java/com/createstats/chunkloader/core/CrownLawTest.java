package com.createstats.chunkloader.core;

import com.createstats.power.FeRates;
import com.createstats.power.WireTier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrownLawTest {
    private static final CrownLaw LAW = CrownLaw.DEFAULT;

    @Test
    @DisplayName("the totals are the design table: 1,024 rising 10% a chunk to 100,707 at 25")
    void theTotalsAreTheAgreedTable() {
        assertEquals(1_024, LAW.totalDraw(1));
        assertEquals(7_901, LAW.totalDraw(6));
        assertEquals(13_905, LAW.totalDraw(9));
        assertEquals(16_320, LAW.totalDraw(10));
        assertEquals(32_535, LAW.totalDraw(15));
        assertEquals(36_813, LAW.totalDraw(16));
        assertEquals(58_650, LAW.totalDraw(20));
        assertEquals(65_539, LAW.totalDraw(21), "the design's '21 chunks is 65,539, 3 over'");
        assertEquals(100_707, LAW.totalDraw(25));
        assertEquals(0, LAW.totalDraw(0));
    }

    @Test
    @DisplayName("the next chunk's price is what the draw rises by, and is the table's 'that chunk alone'")
    void theNextPriceIsTheRise() {
        assertEquals(1_024, LAW.nextPrice(0));
        assertEquals(1_649, LAW.nextPrice(5));
        assertEquals(2_195, LAW.nextPrice(8));
        assertEquals(2_415, LAW.nextPrice(9));
        assertEquals(3_889, LAW.nextPrice(14));
        assertEquals(4_278, LAW.nextPrice(15));
        assertEquals(6_263, LAW.nextPrice(19));
        assertEquals(10_086, LAW.nextPrice(24));
        assertEquals(0, LAW.nextPrice(25), "a full station has no next chunk");
        for (int n = 0; n < 25; n++) {
            assertEquals(LAW.totalDraw(n + 1) - LAW.totalDraw(n), LAW.nextPrice(n), "price and draw agree at " + n);
            assertEquals((double) Math.round(LAW.chunkCost(n + 1)), (double) LAW.nextPrice(n), 1.0,
                    "the rise is the chunk's own cost to within the one FE of rounding, at " + (n + 1));
        }
    }

    @Test
    @DisplayName("summing rounded chunks would drift from the table, so the total is rounded once")
    void theTotalIsRoundedOnce() {
        long summed = 0;
        for (int k = 1; k <= 6; k++) {
            summed += Math.round(LAW.chunkCost(k));
        }
        assertEquals(7_900, summed, "the drift this guards against: 7,900 against the table's 7,901");
        assertEquals(7_901, LAW.totalDraw(6));
    }

    @Test
    @DisplayName("one HV Line carries 20 chunks but not 21, and a default full station fits two HV spans")
    void aFullStationFitsTwoHvSpans() {
        final int hv = FeRates.wire(WireTier.HV);
        assertEquals(65_536, hv, "FE-REF §3.1: an HV Line is eight times the 8,192 base");
        assertTrue(LAW.totalDraw(20) <= hv, "20 chunks on one HV Line");
        assertTrue(LAW.totalDraw(21) > hv, "the 21st chunk is the second stud's");
        assertTrue(LAW.totalDraw(LAW.maxPerStation()) <= 2 * hv, "25 chunks on two HV Lines");
        assertEquals(131_072, 2 * hv);
    }

    @Test
    @DisplayName("the store holds 5 seconds of the picks' draw: about 10.07 MFE for a full station")
    void theStoreIsFiveSecondsOfTheDraw() {
        assertEquals(100L, LAW.rideTicks());
        assertEquals(102_400L, LAW.storeCapacity(1));
        assertEquals(10_070_700L, LAW.storeCapacity(25));
        assertEquals(0L, LAW.storeCapacity(0));
    }

    @Test
    @DisplayName("the light climbs with the draw, 15 at a full station, never 0 while anything is held")
    void theLightClimbsWithTheDraw() {
        assertEquals(0, LAW.lightLevel(0));
        assertEquals(1, LAW.lightLevel(1));
        assertEquals(15, LAW.lightLevel(25));
        int last = 0;
        for (int n = 1; n <= 25; n++) {
            final int l = LAW.lightLevel(n);
            assertTrue(l >= last, "never dimmer for more chunks, at " + n);
            assertTrue(l >= 1 && l <= 15);
            last = l;
        }
    }

    @Test
    @DisplayName("config: the station cap never passes the crown's 25, growth 0 is flat, a sum saturates")
    void configIsClampedAndSaturates() {
        assertEquals(25, new CrownLaw(1_024, 10, 99, 50, 5).maxPerStation());
        assertEquals(10 * 1_024, new CrownLaw(1_024, 0, 25, 50, 5).totalDraw(10));
        assertEquals(Integer.MAX_VALUE, new CrownLaw(Integer.MAX_VALUE, 100, 25, 50, 5).totalDraw(25),
                "FE-REF INV-14: a total saturates, it never wraps negative");
        assertEquals(1, new CrownLaw(0, -5, 0, 0, 0).baseFePerTick());
    }
}
