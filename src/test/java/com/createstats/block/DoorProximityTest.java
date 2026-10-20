package com.createstats.block;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DoorProximityTest {
    private static final int RADIUS = 3;

    private static final int LEFT_X = 0;
    private static final int RIGHT_X = 1;
    private static final int Z = 0;
    private static final int Y = 0;

    private static double doorwayY() {
        return Y + 1.0;
    }

    private static boolean sees(int selfX, int partnerX, double px, double py, double pz) {
        return DoorProximity.inRange(px, py, pz,
                DoorProximity.axisCentre(selfX, partnerX), doorwayY(),
                DoorProximity.axisCentre(Z, Z), RADIUS);
    }

    @Test
    @DisplayName("neither leaf of a pair ever sees somebody the other one cannot")
    void leavesOfAPairNeverDisagree() {
        int disagreements = 0;
        double witnessX = 0.0;
        double witnessZ = 0.0;
        for (int ix = -60; ix <= 70; ix++) {
            for (int iz = -60; iz <= 60; iz++) {
                final double px = ix / 10.0;
                final double pz = iz / 10.0;
                final boolean left = sees(LEFT_X, RIGHT_X, px, doorwayY(), pz);
                final boolean right = sees(RIGHT_X, LEFT_X, px, doorwayY(), pz);
                if (left != right) {
                    disagreements++;
                    witnessX = px;
                    witnessZ = pz;
                }
            }
        }
        assertEquals(0, disagreements,
                "the two leaves disagree at " + disagreements + " points, e.g. ("
                        + witnessX + ", " + witnessZ + "): one of them will shut the door the "
                        + "other is holding open");
    }

    @Test
    @DisplayName("a pair senses from the seam between its leaves, so both agree by construction")
    void aPairSensesFromItsSeam() {
        assertEquals(1.0, DoorProximity.axisCentre(LEFT_X, RIGHT_X), 1.0e-9);
        assertEquals(DoorProximity.axisCentre(LEFT_X, RIGHT_X),
                DoorProximity.axisCentre(RIGHT_X, LEFT_X), 1.0e-9,
                "the centre has to be symmetric or the leaves are back to disagreeing");
    }

    @Test
    @DisplayName("a leaf with no partner senses from its own centre, exactly as before")
    void aLoneLeafIsUnchanged() {
        assertEquals(0.5, DoorProximity.axisCentre(LEFT_X, LEFT_X), 1.0e-9);
        assertEquals(-6.5, DoorProximity.axisCentre(-7, -7), 1.0e-9);
    }

    @Test
    @DisplayName("the radius is a sphere about the doorway, and zero means off")
    void radiusBounds() {
        final double cx = DoorProximity.axisCentre(LEFT_X, LEFT_X);
        final double cy = doorwayY();
        final double cz = DoorProximity.axisCentre(Z, Z);
        assertTrue(DoorProximity.inRange(cx, cy, cz, cx, cy, cz, RADIUS), "dead centre is in range");
        assertTrue(DoorProximity.inRange(cx + RADIUS, cy, cz, cx, cy, cz, RADIUS),
                "exactly on the radius counts, so the boundary is not a gap");
        assertFalse(DoorProximity.inRange(cx + RADIUS + 0.01, cy, cz, cx, cy, cz, RADIUS));
        assertFalse(DoorProximity.inRange(cx, cy, cz, cx, cy, cz, 0),
                "a radius of zero is proximity switched off, not a sphere of nothing");
    }

    private static final double[] BAY = {0.0, 0.0, 0.0, 12.0, 6.0, 1.0};

    private static boolean nearBay(double x, double y, double z, int radius) {
        return DoorProximity.nearBox(x, y, z, BAY[0], BAY[1], BAY[2], BAY[3], BAY[4], BAY[5],
                radius);
    }

    @Test
    @DisplayName("a wide bay is sensed from its nearest part, not from the middle of it")
    void aWideOpeningIsSensedFromItsEdge() {
        assertTrue(nearBay(0.5, 0.5, -2.0, RADIUS),
                "standing two blocks off the left hand end is two blocks from the door");
        assertTrue(nearBay(11.5, 0.5, -2.0, RADIUS), "and the same at the right hand end");
        assertFalse(nearBay(0.5, 0.5, -2.0 - RADIUS, RADIUS),
                "and beyond the radius it is still out of range, so this is not simply wider");
    }

    @Test
    @DisplayName("standing in the doorway is a distance of zero, whatever else is true")
    void insideTheOpeningIsAlwaysInRange() {
        assertTrue(nearBay(6.0, 3.0, 0.5, 1),
                "a driver halfway through the bay must hold it open on the smallest radius there is");
        assertTrue(nearBay(0.0, 0.0, 0.0, 1), "and so must the corner of it");
    }

    @Test
    @DisplayName("off means off here too, and the boundary is inclusive on every axis")
    void boxBounds() {
        assertFalse(nearBay(0.5, 0.5, -1.0, 0), "a radius of zero is proximity switched off");
        assertTrue(nearBay(0.5, 0.5, -RADIUS, RADIUS), "exactly on the radius counts");
        assertFalse(nearBay(0.5, 0.5, -RADIUS - 0.01, RADIUS));

        assertFalse(nearBay(-RADIUS, 0.5, -RADIUS, RADIUS),
                "the corner is measured as a distance, not as two separate axis tests");
    }
}
