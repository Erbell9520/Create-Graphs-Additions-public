package com.createstats.block;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DoorTravelTest {
    private static final int TICKS = 20;
    private static final long NOW = 4_000L;
    private static final float EPSILON = 1.0e-3f;

    private static float at(long start, boolean open, int ticks) {
        return DoorTravel.openness(NOW - start, ticks, open);
    }

    @Test
    @DisplayName("a fresh move from a settled door starts exactly where the door was")
    void freshMoveStartsAtNow() {
        assertEquals(NOW, DoorTravel.resumeStart(NOW, TICKS, 0.0f, true));

        assertEquals(NOW, DoorTravel.resumeStart(NOW, TICKS, 1.0f, false));
    }

    private static float tolerance(int ticks) {
        return 0.5f / ticks + EPSILON;
    }

    @Test
    @DisplayName("reversing mid-travel leaves the leaf where it was, to within half a tick")
    void reversalIsContinuous() {
        for (int step = 0; step <= 100; step++) {
            final float where = step / 100.0f;
            for (final boolean wasOpening : new boolean[]{true, false}) {
                final boolean nowOpening = !wasOpening;
                final long resumed = DoorTravel.resumeStart(NOW, TICKS, where, nowOpening);
                final float after = at(resumed, nowOpening, TICKS);
                assertEquals(where, after, tolerance(TICKS),
                        "reversing at " + where + " (was "
                                + (wasOpening ? "opening" : "closing") + ") moved the leaf to " + after);
            }
        }
    }

    @Test
    @DisplayName("the residual never all falls one way, which is what truncating used to do")
    void reversalErrorIsSymmetric() {
        float sum = 0.0f;
        int samples = 0;
        for (int step = 0; step <= 100; step++) {
            final float where = step / 100.0f;
            final long resumed = DoorTravel.resumeStart(NOW, TICKS, where, false);
            sum += at(resumed, false, TICKS) - where;
            samples++;
        }
        final float bias = sum / samples;
        assertTrue(Math.abs(bias) < 0.5f / TICKS,
                "reversal error is biased by " + bias + ", so it is being truncated rather than rounded");
    }

    @Test
    @DisplayName("a reversal takes the time it has left, not a full travel")
    void reversalFinishesEarly() {
        final long resumed = DoorTravel.resumeStart(NOW, TICKS, 0.4f, false);
        final int left = DoorTravel.remaining(NOW, resumed, TICKS);
        assertEquals(8, left, "expected 40% of " + TICKS + " ticks left, got " + left);
        assertTrue(left < TICKS, "a reversal must not replay a whole travel");
    }

    @Test
    @DisplayName("a leaf barely off its stop comes straight back")
    void reversalNearTheStartIsQuick() {
        final long resumed = DoorTravel.resumeStart(NOW, TICKS, 0.02f, false);
        assertTrue(DoorTravel.remaining(NOW, resumed, TICKS) <= 1,
                "a door reversed the instant it started should be shut again almost at once");
    }

    @Test
    @DisplayName("openness is pinned at both ends and clamps outside the travel")
    void opennessIsPinnedAndClamped() {
        assertEquals(0.0f, DoorTravel.openness(0.0f, TICKS, true), EPSILON);
        assertEquals(1.0f, DoorTravel.openness(TICKS, TICKS, true), EPSILON);
        assertEquals(1.0f, DoorTravel.openness(0.0f, TICKS, false), EPSILON);
        assertEquals(0.0f, DoorTravel.openness(TICKS, TICKS, false), EPSILON);

        assertEquals(1.0f, DoorTravel.openness(TICKS * 10, TICKS, true), EPSILON);
        assertEquals(0.0f, DoorTravel.openness(-50.0f, TICKS, true), EPSILON);
    }

    @Test
    @DisplayName("remaining never goes negative, however long ago the move finished")
    void remainingIsNeverNegative() {
        assertEquals(0, DoorTravel.remaining(NOW, NOW - 10_000L, TICKS));
        assertEquals(TICKS, DoorTravel.remaining(NOW, NOW, TICKS));
    }

    @Test
    @DisplayName("settled agrees with remaining, at the boundary tick as well as either side of it")
    void settledAgreesWithRemaining() {
        for (int elapsed = 0; elapsed <= TICKS + 3; elapsed++) {
            final long start = NOW - elapsed;
            final boolean settled = DoorTravel.settled(NOW, start, TICKS);
            final int left = DoorTravel.remaining(NOW, start, TICKS);
            assertEquals(settled, left == 0,
                    "at " + elapsed + " ticks in, settled=" + settled + " but remaining=" + left);
        }
    }

    @Test
    @DisplayName("every real opening's clock survives the same reversal treatment")
    void everyOpeningReversesCleanly() {
        for (final DoorOpening style : DoorOpening.values()) {
            final int ticks = style.ticks();
            for (int step = 1; step < 20; step++) {
                final float where = step / 20.0f;
                final long resumed = DoorTravel.resumeStart(NOW, ticks, where, false);
                assertEquals(where, at(resumed, false, ticks), tolerance(ticks),
                        style + " lost position reversing at " + where);
                assertTrue(DoorTravel.remaining(NOW, resumed, ticks) <= ticks,
                        style + " gained time by reversing at " + where);
            }
        }
    }

    @Test
    @DisplayName("a zero or negative clock cannot divide by zero or stall the door")
    void degenerateClocksAreSurvivable() {
        assertEquals(1.0f, DoorTravel.openness(1.0f, 0, true), EPSILON);
        assertEquals(NOW, DoorTravel.resumeStart(NOW, 0, 0.0f, true));
        assertEquals(0, DoorTravel.remaining(NOW, NOW - 5, 0));
        assertTrue(DoorTravel.settled(NOW, NOW - 5, 0));
    }
}
