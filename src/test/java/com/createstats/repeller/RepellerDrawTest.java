package com.createstats.repeller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepellerDrawTest {
    @Test
    void theUsersAnchorAndTheSquareLaw() {
        assertEquals(1_024, RepellerDraw.drawFor(64));
        assertEquals(576, RepellerDraw.drawFor(48));
        assertEquals(256, RepellerDraw.drawFor(32));
        assertEquals(64, RepellerDraw.drawFor(16));
        assertEquals(16, RepellerDraw.drawFor(8));
        assertEquals(0, RepellerDraw.drawFor(0));
    }

    @Test
    void everyLegalRadiusCostsExactlyTheLaw() {
        for (int r = RepellerDraw.MIN_RADIUS; r <= RepellerDraw.MAX_RADIUS; r += RepellerDraw.RADIUS_STEP) {
            assertEquals(r * r / 4, RepellerDraw.drawFor(r), "radius " + r);

            assertEquals(r, RepellerDraw.radiusPaidBy(RepellerDraw.drawFor(r)), 1e-9);
        }

        assertEquals(21, RepellerDraw.drawFor(9));
    }

    @Test
    void aHalfFedRepellerStillCoversAboutFortyFiveBlocks() {
        assertEquals(45.25, RepellerDraw.radiusPaidBy(512), 0.01);
        assertEquals(45.25, RepellerDraw.effectiveRadius(64, 512), 0.01);

        assertEquals(16.0, RepellerDraw.effectiveRadius(16, 100_000), 1e-9);
        assertEquals(0.0, RepellerDraw.effectiveRadius(64, 0), 1e-9);
    }

    @Test
    void aCordCanFeedAboutRadiusFortyFiveAndFullReachNeedsCable() {
        final int cord = com.createstats.blockentity.WallOutletBE.RATED_FE_PER_TICK;
        assertEquals(512, cord);
        assertEquals(45.25, RepellerDraw.radiusPaidBy(cord), 0.01);

        assertTrue(RepellerDraw.drawFor(44) <= cord);
        assertTrue(RepellerDraw.drawFor(48) > cord);
        assertTrue(RepellerDraw.drawFor(RepellerDraw.MAX_RADIUS) > cord);
    }

    @Test
    void theRadiusMovesInFifteenNotchesAndClampsAtBothEnds() {
        assertEquals(15, RepellerDraw.notches());
        assertEquals(8, RepellerDraw.clampRadius(-5));
        assertEquals(64, RepellerDraw.clampRadius(500));
        assertEquals(12, RepellerDraw.clampRadius(13), "snapped down onto a notch");
        assertEquals(20, RepellerDraw.stepRadius(16, 1));
        assertEquals(64, RepellerDraw.stepRadius(60, 3), "the wheel stops at 64, it never wraps");
        assertEquals(8, RepellerDraw.stepRadius(12, -5));

        assertEquals(8, RepellerDraw.cycleRadius(64));
        assertEquals(12, RepellerDraw.cycleRadius(8));
        int r = RepellerDraw.MIN_RADIUS;
        for (int i = 0; i < RepellerDraw.notches(); i++) {
            r = RepellerDraw.cycleRadius(r);
        }
        assertEquals(RepellerDraw.MIN_RADIUS, r, "a full lap of clicks comes back where it started");
        assertTrue(RepellerDraw.DEFAULT_RADIUS == RepellerDraw.clampRadius(RepellerDraw.DEFAULT_RADIUS));
    }

    @Test
    void theBufferRidesOutASecondAndTheNodeCanRefillIt() {
        final int peak = RepellerDraw.drawFor(RepellerDraw.MAX_RADIUS);

        assertTrue(RepellerDraw.BUFFER >= peak * 30, "buffer " + RepellerDraw.BUFFER);

        assertTrue(RepellerDraw.BUFFER <= peak * 60);

        assertTrue(RepellerDraw.TRANSFER_RATE > peak);
    }
}
