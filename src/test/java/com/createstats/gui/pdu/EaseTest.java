package com.createstats.gui.pdu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EaseTest {
    @Test
    void convergesToTheTargetFromBelow() {
        float v = 0.0f;
        for (int i = 0; i < 1000; i++) {
            v = Ease.ease(v, 100.0f, 0.3f, 1.0f);
        }
        assertEquals(100.0f, v, 0.001f);
    }

    @Test
    void convergesToTheTargetFromAbove() {
        float v = 100.0f;
        for (int i = 0; i < 1000; i++) {
            v = Ease.ease(v, 0.0f, 0.3f, 1.0f);
        }
        assertEquals(0.0f, v, 0.001f);
    }

    @Test
    void aSingleStepMovesTowardTheTargetWithoutReachingIt() {
        final float step = Ease.ease(0.0f, 100.0f, 0.3f, 1.0f);
        assertTrue(step > 0.0f, "should move toward the target");
        assertTrue(step < 100.0f, "a slow glide should not arrive in one step");
    }

    @Test
    void aLargerFrameDeltaStepsFurther() {
        final float shortFrame = Ease.ease(0.0f, 100.0f, 0.1f, 0.5f);
        final float longFrame = Ease.ease(0.0f, 100.0f, 0.1f, 1.0f);
        assertTrue(longFrame > shortFrame);
    }

    @Test
    void aStalledFrameArrivesExactlyAndNeverOvershoots() {
        final float v = Ease.ease(0.0f, 100.0f, 5.0f, 1.0f);
        assertEquals(100.0f, v, 0.0001f);
    }

    @Test
    void aValueAtTargetStaysPut() {
        assertEquals(50.0f, Ease.ease(50.0f, 50.0f, 0.3f, 1.0f), 0.0001f);
    }

    @Test
    void aNonPositiveFrameDeltaStillProgressesRatherThanStalling() {
        final float v = Ease.ease(0.0f, 100.0f, 0.3f, 0.0f);
        assertTrue(v > 0.0f);
    }
}
