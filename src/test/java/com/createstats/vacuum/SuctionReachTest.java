package com.createstats.vacuum;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SuctionReachTest {
    @Test
    void theCollectorReachesOutOfItsMouthAndNotBehindIt() {
        final SuctionReach.Box box = SuctionReach.collector(0, 64, 0, 0, -1, 4);
        assertTrue(box.contains(0.5, 64.2, -3.5), "four blocks out of the mouth");
        assertTrue(box.contains(4.5, 64.2, -2.5), "four blocks to the side");
        assertTrue(box.contains(-3.5, 64.2, 0.5), "beside it, on its own layer");
        assertFalse(box.contains(0.5, 64.2, 1.5), "behind it");
        assertFalse(box.contains(0.5, 64.2, -4.5), "five blocks out");
        assertFalse(box.contains(0.5, 62.5, -1.5), "two blocks below its feet");
        assertTrue(box.contains(0.5, 63.5, -1.5), "one block below its feet");
    }

    @Test
    void theCollectorTurnsWithItsFacing() {
        final SuctionReach.Box east = SuctionReach.collector(10, 0, 10, 1, 0, 3);
        assertTrue(east.contains(13.5, 0.5, 10.5));
        assertFalse(east.contains(9.5, 0.5, 10.5));
        final SuctionReach.Box west = SuctionReach.collector(10, 0, 10, -1, 0, 3);
        assertTrue(west.contains(7.5, 0.5, 10.5));
        assertFalse(west.contains(11.5, 0.5, 10.5));
    }

    @Test
    void theCollectorBoxGrowsWithTheRadius() {
        double last = 0;
        for (int r = 1; r <= 8; r++) {
            final double v = SuctionReach.collector(0, 0, 0, 0, -1, r).volume();
            assertTrue(v > last, "radius " + r);
            last = v;
        }
    }

    @Test
    void theSnifferLooksAllRoundIt() {
        final SuctionReach.Box box = SuctionReach.sniffer(0, 10, 0);
        for (int[] d : new int[][] {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
            final String way = d[0] + ", " + d[1] + ", " + d[2];
            assertTrue(box.contains(0.5 + 6 * d[0], 10.5 + 6 * d[1], 0.5 + 6 * d[2]), "six blocks " + way);
            assertFalse(box.contains(0.5 + 7 * d[0], 10.5 + 7 * d[1], 0.5 + 7 * d[2]), "seven blocks " + way);
        }
        assertTrue(box.contains(-5.5, 4.5, 6.5), "a far corner, below and behind");
    }

    @Test
    void aLineLeavesItsOwnBlockWhereItCrossesAFace() {
        assertEquals(0.25, SuctionReach.exitFraction(0.5, 0.5, 0.5, 0.5, 2.5, 0.5, 0, 0, 0), 1e-9);

        assertEquals(0.5 / 3.0, SuctionReach.exitFraction(0.5, 0.5, 0.5, 3.5, 1.5, 0.5, 0, 0, 0), 1e-9);

        assertEquals(0.25, SuctionReach.exitFraction(0.5, 0.625, 0.5, 2.5, 0.125, 0.5, 0, 0, 0), 1e-9);
        assertTrue(SuctionReach.exitFraction(0.5, 0.5, 0.5, 0.9, 0.2, 0.5, 0, 0, 0) >= 1.0,
                "an item inside the block itself never leaves it");
        assertEquals(0.0, SuctionReach.exitFraction(3.0, 0.5, 0.5, 0.5, 0.5, 0.5, 0, 0, 0), 1e-9,
                "a line that starts outside the block is not cut at all");
    }

    @Test
    void theCleanerIsOneBlockEitherWayOfWhereYouLook() {
        final SuctionReach.Box box = SuctionReach.cleaner(5.0, 64.0, 5.0);
        assertEquals(2.0 * SuctionReach.CLEANER_SPREAD, box.maxX() - box.minX(), 1e-9);
        assertTrue(box.contains(5.9, 63.1, 4.1));
        assertFalse(box.contains(6.1, 64.0, 5.0));
    }
}
