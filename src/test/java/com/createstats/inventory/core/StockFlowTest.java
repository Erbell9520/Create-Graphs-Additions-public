package com.createstats.inventory.core;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StockFlowTest {
    private static Map<String, Long> counts(Object... kv) {
        final Map<String, Long> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], ((Number) kv[i + 1]).longValue());
        }
        return m;
    }

    @Test
    void movesInTheBaselineSecondAreNotRated() {
        final StockFlow f = new StockFlow();
        f.observe(0, counts("iron", 0L));
        f.observe(5, counts("iron", 4L));
        assertEquals(0.0, f.rate("iron", 30 * 20).inPerMinute());
    }

    @Test
    void theFirstScanIsABaselineOnly() {
        final StockFlow f = new StockFlow();
        assertTrue(f.observe(0, counts("iron", 500L)).isEmpty(), "what was there is not IN");
        assertEquals(0.0, f.rate("iron", 2000).inPerMinute());
    }

    @Test
    void insertAndExtractAreCountedEachWay() {
        final StockFlow f = new StockFlow();
        f.observe(0, counts("iron", 10L));
        assertEquals(Map.of("iron", 5L), f.observe(25, counts("iron", 15L)));
        assertEquals(Map.of("iron", -3L), f.observe(45, counts("iron", 12L)));
        final StockFlow.Rate r = f.rate("iron", 25 * 20);
        assertTrue(r.inPerMinute() > 0.0 && r.outPerMinute() > 0.0);
    }

    @Test
    void churnAcrossScansCountsBothWays() {
        final StockFlow f = new StockFlow();
        f.observe(0, counts("coal", 10L));
        f.observe(25, counts("coal", 11L));
        f.observe(30, counts("coal", 10L));
        f.observe(35, counts("coal", 11L));
        f.observe(40, counts("coal", 10L));

        final StockFlow.Rate r = f.rate("coal", 21 * 20);
        assertEquals(r.inPerMinute(), r.outPerMinute(), 1e-9, "two in, two out");
        assertTrue(r.inPerMinute() > 0.0);
    }

    @Test
    void churnInsideOneScanIsInvisibleAndThatIsDocumented() {
        final StockFlow f = new StockFlow();
        f.observe(0, counts("coal", 10L));

        assertTrue(f.observe(5, counts("coal", 10L)).isEmpty(), "gross IN and OUT are lower bounds");
        assertEquals(0.0, f.rate("coal", 61 * 20).inPerMinute());
    }

    @Test
    void aVanishedKeyIsOutAndANewKeyIsIn() {
        final StockFlow f = new StockFlow();
        f.observe(0, counts("bread", 16L));
        final Map<String, Long> d = f.observe(5, counts("bone", 4L));
        assertEquals(-16L, d.get("bread"));
        assertEquals(4L, d.get("bone"));
    }

    @Test
    void aSteadyFlowReadsExactlyItsRateOnceAMinuteIsIn() {
        final StockFlow f = new StockFlow();
        long count = 0;
        f.observe(0, counts("cobble", count));

        for (long t = 5; t <= 20 * 200; t += 5) {
            if (t % 20 == 0) {
                count++;
            }
            f.observe(t, counts("cobble", count));
        }
        final long now = 20 * 200 + 10;
        assertFalse(f.gathering(now));
        assertEquals(60.0, f.rate("cobble", now).inPerMinute(), 1e-9, "exactly one a second");
        assertEquals(60.0, f.total(now).inPerMinute(), 1e-9);
    }

    @Test
    void theWindowAgesOut() {
        final StockFlow f = new StockFlow();
        f.observe(0, counts("sand", 0L));
        f.observe(25, counts("sand", 64L));
        assertTrue(f.rate("sand", 30 * 20).inPerMinute() > 0.0);
        for (long t = 30; t <= 20 * 200; t += 5) {
            f.observe(t, counts("sand", 64L));
        }
        assertEquals(0.0, f.rate("sand", 20 * 200).inPerMinute(), "sixty seconds later it is gone");
    }

    @Test
    void aPartialWindowIsScaledToAMinuteAndFlaggedGathering() {
        final StockFlow f = new StockFlow();
        f.observe(0, counts("sand", 0L));
        f.observe(20, counts("sand", 10L));

        final long now = 31 * 20;
        assertEquals(30, f.coveredSeconds(now));
        assertTrue(f.gathering(now));
        assertEquals(30, f.secondsUntilRates(now));
        assertEquals(20.0, f.rate("sand", now).inPerMinute(), 1e-9, "10 in 30 s is 20 a minute");
    }

    @Test
    void gatheringEndsAfterSixtyCompletedSeconds() {
        final StockFlow f = new StockFlow();
        f.observe(5, counts());
        assertTrue(f.gathering(61 * 20 - 1));
        assertFalse(f.gathering(61 * 20));
        assertEquals(0, f.secondsUntilRates(61 * 20));
    }

    @Test
    void keysPastTheCapFoldIntoOther() {
        final StockFlow f = new StockFlow(3);
        f.observe(0, counts());
        f.observe(25, counts("a", 1L, "b", 1L, "c", 1L, "d", 1L, "e", 1L));
        final long now = 2 * 20;
        assertEquals(3, f.movingKeys().stream().filter(k -> !k.equals(StockFlow.OTHER)).count());
        assertTrue(f.movingKeys().contains(StockFlow.OTHER));
        assertEquals(5.0 * 60, f.total(now).inPerMinute(), 1e-9, "nothing is lost, only its name");
    }

    @Test
    void aTargetChangeRebaselines() {
        final StockFlow f = new StockFlow();
        f.observe(0, counts("iron", 5L));
        f.observe(5, counts("iron", 9L));
        f.rebaseline();
        assertTrue(f.awaitingBaseline());
        assertTrue(f.observe(10, counts("gold", 900L)).isEmpty(), "the new container's stock is not IN");
        assertTrue(f.gathering(20 * 30));
    }

    @Test
    void theFastestMoversComeFirstAndOtherIsNeverOne() {
        final StockFlow f = new StockFlow(2);
        f.observe(0, counts());
        f.observe(25, counts("slow", 1L, "fast", 9L, "late", 4L));
        final List<StockFlow.Mover> top = f.top(2, true, 2 * 20);
        assertEquals("fast", top.get(0).key());
        assertTrue(top.stream().noneMatch(m -> m.key().equals(StockFlow.OTHER)));
    }
}
