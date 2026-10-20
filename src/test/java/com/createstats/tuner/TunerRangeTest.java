package com.createstats.tuner;

import com.createstats.repeller.RepellerDraw;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TunerRangeTest {
    private static final TunerRange REPELLER = new TunerRange(RepellerDraw.MIN_RADIUS, RepellerDraw.MAX_RADIUS,
            RepellerDraw.RADIUS_STEP, RepellerDraw::drawFor);

    @Test
    void theRepellersRangeIsFifteenNotchesWithTheSquareLawForItsReadout() {
        assertEquals(15, REPELLER.notches());
        assertEquals(1_024, REPELLER.costAt(64));
        assertEquals(64, REPELLER.costAt(16));
        assertEquals(64, REPELLER.costAt(17), "a setting off a notch is priced at the notch it snaps to");
        assertEquals(0, REPELLER.notchOf(8));
        assertEquals(14, REPELLER.notchOf(64));
    }

    @Test
    void theWheelStopsAtTheEndsAndTheClickWraps() {
        assertEquals(64, REPELLER.step(60, 5));
        assertEquals(8, REPELLER.step(12, -9));
        assertEquals(8, REPELLER.cycle(64));
        assertEquals(36, REPELLER.cycle(32));
        assertEquals(8, REPELLER.clamp(Integer.MIN_VALUE));
        assertEquals(64, REPELLER.clamp(Integer.MAX_VALUE));
    }

    @Test
    void aNonsenseRangeIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new TunerRange(10, 5, 1, v -> 0));
        assertThrows(IllegalArgumentException.class, () -> new TunerRange(0, 5, 0, v -> 0));
        assertTrue(new TunerRange(3, 3, 1, v -> 7).costAt(99) == 7);
    }
}
