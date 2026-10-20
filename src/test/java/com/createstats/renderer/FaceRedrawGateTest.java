package com.createstats.renderer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FaceRedrawGateTest {
    private static final int MIN = 2;

    @Test
    void aFaceThatHasNeverDrawnIsAlwaysDue() {
        for (long now : new long[]{0L, 1L, 2L, 1_234L, 20L * 60 * 60 * 24 * 365}) {
            assertTrue(FaceRedrawGate.due(now, FaceRedrawGate.NEVER, MIN),
                    "a face with nothing in it must rasterise at game time " + now
                            + ", whatever the throttle says");
        }

        assertTrue(1_234L - FaceRedrawGate.NEVER < 0,
                "if this ever stops overflowing, the comment above is wrong, not the code");
    }

    @Test
    void aFaceDrawnThisTickIsNotDueAgain() {
        assertFalse(FaceRedrawGate.due(1_000L, 1_000L, MIN));
        assertFalse(FaceRedrawGate.due(1_001L, 1_000L, MIN));
    }

    @Test
    void aFaceIsDueOnceTheFloorHasPassed() {
        assertTrue(FaceRedrawGate.due(1_002L, 1_000L, MIN));
        assertTrue(FaceRedrawGate.due(1_050L, 1_000L, MIN));
    }

    @Test
    void aClockThatWentBackwardsDoesNotFreezeTheFace() {
        assertTrue(FaceRedrawGate.due(5L, 4_000_000L, MIN),
                "waiting for the clock to catch up would freeze the face for as long as the"
                        + " previous world had been running");
    }

    @Test
    void aZeroFloorNeverBlocks() {
        assertTrue(FaceRedrawGate.due(1_000L, 1_000L, 0));
        assertTrue(FaceRedrawGate.due(1_000L, 999L, 1));
    }
}
