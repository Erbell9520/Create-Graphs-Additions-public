package com.createstats.repeller;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepelCourseTest {
    private static final double EPS = 1.0E-9;

    @Test
    void theGoalAndTheSpawnCheckAgreeOnInside() {
        for (RepellerShape shape : RepellerShape.values()) {
            for (double x = -20.25; x <= 20; x += 1.5) {
                for (double y = 40.25; y <= 90; y += 2.5) {
                    for (double z = -20.75; z <= 20; z += 1.75) {
                        final boolean inside = shape.contains(0.5, 64.5, 0.5, 16, x, y, z);
                        final double depth = RepelCourse.depth(shape, 0.5, 64.5, 0.5, 16, x, y, z);
                        assertEquals(inside, depth >= 0.0, shape + " at " + x + ", " + y + ", " + z);
                    }
                }
            }
        }
    }

    @Test
    void depthIsTheDistanceToTheEdge() {
        assertEquals(6.0, RepelCourse.depth(RepellerShape.SPHERE, 0.5, 64.5, 0.5, 16, 10.5, 64.5, 0.5), EPS);
        assertEquals(-4.0, RepelCourse.depth(RepellerShape.SPHERE, 0.5, 64.5, 0.5, 16, 0.5, 84.5, 0.5), EPS,
                "twenty up is four outside a sphere of sixteen");
        assertEquals(4.0, RepelCourse.depth(RepellerShape.COLUMN, 0.5, 64.5, 0.5, 16, 0.5, 300.0, 12.5), EPS,
                "a column ignores height");
    }

    @Test
    void aLeavingMobKeepsGoingUntilItIsClearOfTheEdge() {
        assertTrue(RepelCourse.stillLeaving(5.0));
        assertTrue(RepelCourse.stillLeaving(0.0), "on the edge");
        assertTrue(RepelCourse.stillLeaving(-2.9), "just out still steps on");
        assertFalse(RepelCourse.stillLeaving(-RepelCourse.EXIT_MARGIN), "clear by the margin");
        assertFalse(RepelCourse.stillLeaving(-10.0));
    }

    @Test
    void outIsFlatAndAwayFromTheAxis() {
        final RepelCourse.Heading h = RepelCourse.outward(0.0, 0.0, 3.0, 4.0, 0.0);
        assertEquals(0.6, h.x(), EPS);
        assertEquals(0.8, h.z(), EPS);
        final RepelCourse.Heading w = RepelCourse.outward(10.0, -5.0, -20.0, -5.0, 0.0);
        assertEquals(-1.0, w.x(), EPS);
        assertEquals(0.0, w.z(), EPS);
    }

    @Test
    void mobsStandingOnTheAxisSpreadOutInsteadOfQueueing() {
        final Set<Long> directions = new HashSet<>();
        for (int seed = 0; seed < 12; seed++) {
            final RepelCourse.Heading h = RepelCourse.outward(0.5, 0.5, 0.5, 0.5, RepelCourse.fallbackAngle(seed));
            assertEquals(1.0, h.x() * h.x() + h.z() * h.z(), 1.0E-9, "still a unit heading on the axis");
            directions.add(Math.round(Math.toDegrees(Math.atan2(h.z(), h.x()))) / 10);
        }
        assertTrue(directions.size() >= 10, "twelve mobs on the axis leave nearly twelve ways: " + directions);
        final double negative = RepelCourse.fallbackAngle(-7);
        assertTrue(negative >= 0.0 && negative < Math.PI * 2.0, "any entity id gives an angle");
    }

    @Test
    void thePointBeyondIsOutsideAtEveryHeightAndStraightOut() {
        for (RepellerShape shape : RepellerShape.values()) {
            final RepelCourse.Spot s = RepelCourse.beyond(100.5, -40.5, 24, 110.5, -40.5, RepelCourse.STEER_BEYOND, 0.0);
            assertEquals(100.5 + 24 + RepelCourse.STEER_BEYOND, s.x(), EPS);
            assertEquals(-40.5, s.z(), EPS);
            for (double y = -60; y <= 320; y += 20) {
                assertFalse(shape.contains(100.5, 70.5, -40.5, 24, s.x(), y, s.z()), shape + " at y " + y);
                assertFalse(RepelCourse.stillLeaving(RepelCourse.depth(shape, 100.5, 70.5, -40.5, 24, s.x(), y, s.z())),
                        "a mob sent there is clear by the time it arrives");
            }
        }
    }

    @Test
    void aPhantomsAnchorKeepsItsWholeCircleOutside() {
        final RepelCourse.Spot a = RepelCourse.beyond(0.0, 0.0, 64, 30.0, 0.0, RepelCourse.ANCHOR_BEYOND, 0.0);
        final double nearest = Math.hypot(a.x(), a.z()) - 15.0;
        assertTrue(nearest > 64 + RepelCourse.EXIT_MARGIN, "the circle's nearest point is " + nearest);
    }

    @Test
    void aPushTopsASpeedUpAndNeverFlingsOrPullsBack() {
        assertEquals(RepelCourse.PUSH_PER_TICK, RepelCourse.pushStep(0.0), EPS);
        assertEquals(0.05, RepelCourse.pushStep(0.25), EPS);
        assertEquals(0.0, RepelCourse.pushStep(RepelCourse.PUSH_SPEED), EPS);
        assertEquals(0.0, RepelCourse.pushStep(1.5), EPS, "a mob already leaving fast is left alone");
        assertEquals(RepelCourse.PUSH_PER_TICK, RepelCourse.pushStep(-2.0), EPS, "one heading in gets no bigger shove");
    }

    @Test
    void aPushedMobMovesOutOnTheGroundAndInTheAirWithoutRunaway() {
        for (double slowdown : new double[] {0.546, 0.91}) {
            double v = 0.0;
            for (int tick = 0; tick < 200; tick++) {
                v = (v + RepelCourse.pushStep(v)) * slowdown;
                assertTrue(v <= RepelCourse.PUSH_SPEED + EPS, "never past the push speed");
            }
            assertTrue(v >= 0.1, "a steady outward drift of at least two blocks a second at " + slowdown + ": " + v);
        }
    }

    @Test
    void aStepIsWorthTakingOnlyIfItGetsNearerTheEdge() {
        assertTrue(RepelCourse.worthTaking(10.0, 2.0));
        assertTrue(RepelCourse.worthTaking(10.0, 10.0 - RepelCourse.PROGRESS_STEP));
        assertFalse(RepelCourse.worthTaking(10.0, 9.8), "a sidestep");
        assertFalse(RepelCourse.worthTaking(10.0, 12.0), "deeper in");
    }

    @Test
    void aMobIsStuckAfterStallTicksOfGameTimeWithoutProgress() {
        final RepelCourse.Watch w = new RepelCourse.Watch();
        w.start(100, 10.0);
        for (int ask = 0; ask < 50; ask++) {
            w.observe(120, 9.8);
        }
        assertFalse(w.stalled(100 + RepelCourse.STALL_TICKS), "asked fifty times in one tick is still one tick");
        assertTrue(w.stalled(101 + RepelCourse.STALL_TICKS));
        w.observe(141, 9.4);
        assertFalse(w.stalled(150), "half a block nearer the edge is progress");
        w.observe(160, 12.0);
        assertFalse(w.stalled(170), "being pushed back in does not reset the clock");
        assertTrue(w.stalled(142 + RepelCourse.STALL_TICKS));
    }
}
