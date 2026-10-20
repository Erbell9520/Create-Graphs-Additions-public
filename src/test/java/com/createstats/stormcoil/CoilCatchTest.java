package com.createstats.stormcoil;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoilCatchTest {
    @Test
    @DisplayName("a call comes every half to one and a half of its stage's mean: 90 s at stage 1, 30 s at stage 4")
    void callDelay() {
        final int[] mean = {1800, 1200, 900, 600};
        for (CoilStage st : CoilStage.values()) {
            final int m = mean[st.ordinal()];
            assertEquals(m / 2, CoilCatch.callDelay(st, 0.0), st + ": soonest");
            assertEquals(m, CoilCatch.callDelay(st, 0.5), st + ": the mean");
            assertEquals(m * 3 / 2, CoilCatch.callDelay(st, 1.0), st + ": latest");
            assertTrue(CoilCatch.callDelay(st, -5) > CoilCatch.PRIME_TICKS, "the build-up always shows whole");
        }
        assertTrue(CoilCatch.callDelay(CoilStage.FOUR, 0.5) < CoilCatch.callDelay(CoilStage.ONE, 0.5),
                "a taller coil draws lightning more often");
    }

    @Test
    @DisplayName("primed for exactly the three seconds before a call lands, never with none scheduled")
    void primed() {
        assertFalse(CoilCatch.primed(1000, 0), "0 means no call is scheduled, at any time");
        assertFalse(CoilCatch.primed(0, 0));
        assertFalse(CoilCatch.primed(1000, 1061), "61 ticks out: not yet");
        assertTrue(CoilCatch.primed(1000, 1060));
        assertTrue(CoilCatch.primed(1000, 1001));
        assertFalse(CoilCatch.primed(1000, 1000), "the tick it lands is the strike, not the build-up");
        assertFalse(CoilCatch.primed(1000, 900), "a call already past");
    }
}
