package com.createstats.block;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GarageDoorApproachTest {
    private static final int W = 5;
    private static final int H = 4;
    private static final int D = GarageDoorApproach.DEFAULT_DEPTH;

    @Test
    @DisplayName("the approach box does not reach past the jambs")
    void boxStopsAtTheJambs() {
        assertTrue(GarageDoorApproach.inApproachBox(2.0, W / 2.0, 0.0, W, H, D));
        assertFalse(GarageDoorApproach.inApproachBox(2.0, W / 2.0 + 0.1, 0.0, W, H, D));
        assertFalse(GarageDoorApproach.inApproachBox(2.0, -(W / 2.0 + 0.1), 0.0, W, H, D));
    }

    @Test
    @DisplayName("the approach box does not reach past the lintel or under the threshold")
    void boxStopsAtTheHeadAndFoot() {
        assertTrue(GarageDoorApproach.inApproachBox(2.0, 0.0, H / 2.0, W, H, D));
        assertFalse(GarageDoorApproach.inApproachBox(2.0, 0.0, H / 2.0 + 0.1, W, H, D));
    }

    @Test
    @DisplayName("the approach box reaches the same distance out as in")
    void boxIsSymmetricAboutTheOpening() {
        assertTrue(GarageDoorApproach.inApproachBox(D, 0.0, 0.0, W, H, D));
        assertTrue(GarageDoorApproach.inApproachBox(-D, 0.0, 0.0, W, H, D));
        assertFalse(GarageDoorApproach.inApproachBox(D + 0.1, 0.0, 0.0, W, H, D));
        assertFalse(GarageDoorApproach.inApproachBox(-(D + 0.1), 0.0, 0.0, W, H, D));
    }

    @Test
    @DisplayName("something sitting still in the driveway does not hold the bay open")
    void parkedVehicleIsNotArriving() {
        assertFalse(GarageDoorApproach.closing(3.0, 0.0));

        assertFalse(GarageDoorApproach.closing(3.0, 0.003));
        assertFalse(GarageDoorApproach.closing(3.0, -0.003));
        assertFalse(GarageDoorApproach.arriving(3.0, 0.0, 0.0, 0.0, W, H, D));
    }

    @Test
    @DisplayName("something driving away does not count as arriving")
    void departingIsNotArriving() {
        assertFalse(GarageDoorApproach.closing(3.0, 0.4));
        assertFalse(GarageDoorApproach.closing(-3.0, -0.4));
    }

    @Test
    @DisplayName("arriving reads the same from outside and from inside")
    void arrivalIsSymmetric() {
        assertTrue(GarageDoorApproach.closing(3.0, -0.4), "a cart coming in from outside");
        assertTrue(GarageDoorApproach.closing(-3.0, 0.4), "the same cart leaving from inside");
        assertTrue(GarageDoorApproach.arriving(3.0, 0.0, 0.0, -0.4, W, H, D));
        assertTrue(GarageDoorApproach.arriving(-3.0, 0.0, 0.0, 0.4, W, H, D));
    }

    @Test
    @DisplayName("a slow contraption still registers, a resting entity still does not")
    void thresholdSitsBetweenCreepAndJitter() {
        assertTrue(GarageDoorApproach.closing(3.0, -0.08), "a contraption creeping in");
        assertFalse(GarageDoorApproach.closing(3.0, -0.01), "jitter");
    }

    @Test
    @DisplayName("anything already in the doorway holds the door open whatever it is doing")
    void stoppedInTheDoorwayStillCounts() {
        assertTrue(GarageDoorApproach.closing(0.0, 0.0));
        assertTrue(GarageDoorApproach.closing(0.4, 0.0));
        assertTrue(GarageDoorApproach.closing(-0.4, 0.0));
    }

    @Test
    @DisplayName("the photo-eye volume is the doorway alone, not the approach")
    void photoEyeIsThinnerThanTheApproach() {
        assertTrue(GarageDoorApproach.inDoorway(0.0, 0.0, 0.0, W, H));
        assertFalse(GarageDoorApproach.inDoorway(2.0, 0.0, 0.0, W, H));
        assertTrue(GarageDoorApproach.inApproachBox(2.0, 0.0, 0.0, W, H, D),
                "the approach box should still contain what the photo-eye ignores");
    }
}
