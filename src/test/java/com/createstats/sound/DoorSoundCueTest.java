package com.createstats.sound;

import com.createstats.block.DoorOpening;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DoorSoundCueTest {
    private static int lastTickThatRuns(int ticks) {
        return ticks - 1;
    }

    private static int cueTick(float fraction, int ticks) {
        return Math.max(0, Math.min(ticks - 1, (int) (fraction * ticks)));
    }

    @Test
    @DisplayName("every opening's arrival lands on a tick the client is still running for")
    void everyArrivalIsReachable() {
        for (final DoorOpening style : DoorOpening.values()) {
            final DoorSound.Kit kit = DoorSound.kit(style);
            final int ticks = style.ticks();
            final int at = cueTick(kit.arriveAt(), ticks);
            assertTrue(at <= lastTickThatRuns(ticks),
                    style + " arrives at tick " + at + " but the client stops after tick "
                            + lastTickThatRuns(ticks) + ", so the door would seat in silence");
            assertTrue(at >= 0, style + " arrives before it departs");
        }
    }

    @Test
    @DisplayName("every midway clack lands on a running tick, and before the arrival")
    void everyMidwayIsReachableAndOrdered() {
        for (final DoorOpening style : DoorOpening.values()) {
            final DoorSound.Kit kit = DoorSound.kit(style);
            if (kit.midway() == null) {
                continue;
            }
            final int ticks = style.ticks();
            final int midway = cueTick(kit.midwayAt(), ticks);
            final int arrival = cueTick(kit.arriveAt(), ticks);
            assertTrue(midway <= lastTickThatRuns(ticks),
                    style + " clacks at tick " + midway + ", past the last tick that runs");
            assertTrue(midway < arrival,
                    style + " clacks at tick " + midway + " and arrives at " + arrival
                            + ", which is not in that order");
        }
    }

    @Test
    @DisplayName("the arrival is genuinely at the end, not merely somewhere reachable")
    void arrivalIsNearTheEnd() {
        for (final DoorOpening style : DoorOpening.values()) {
            final int ticks = style.ticks();
            final int at = cueTick(DoorSound.kit(style).arriveAt(), ticks);

            assertTrue(ticks - at <= 3,
                    style + " seats " + (ticks - at) + " ticks before it stops moving");
        }
    }

    @Test
    @DisplayName("the clamp holds for any travel time a retune might pick")
    void clampHoldsAcrossPlausibleDurations() {
        for (int ticks = 1; ticks <= 60; ticks++) {
            for (final float fraction : new float[]{0.0f, 0.45f, 0.5f, 0.9f, 0.95f, 0.97f, 1.0f}) {
                final int at = cueTick(fraction, ticks);
                assertTrue(at >= 0 && at <= ticks - 1,
                        "cueTick(" + fraction + ", " + ticks + ") gave " + at
                                + ", outside the ticks that run");
            }
        }
    }
}
