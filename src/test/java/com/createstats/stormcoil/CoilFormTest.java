package com.createstats.stormcoil;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoilFormTest {
    @Test
    @DisplayName("the toroid stays inside the column's cells and grows with height; rods home only at 4")
    void form() {
        double lastMajor = 0;
        double lastTop = 0;
        for (int i = 0; i <= 300; i++) {
            final double h = 1.0 + 3.0 * i / 300.0;
            final CoilForm f = CoilForm.at(h);
            assertTrue(f.top <= 16.0 * Math.ceil(h - 1e-9) + 1e-9, "the toroid pokes above its top cell at " + h);
            assertTrue(f.toroidY - f.tube > CoilForm.DECK, "the toroid sits on the deck at " + h);
            assertTrue(f.major >= lastMajor - 1e-12 && f.top >= lastTop - 1e-12, "it shrank while rising at " + h);
            lastMajor = f.major;
            lastTop = f.top;
            assertTrue(f.section(0)[0] < CoilForm.DECK, "the widest section's cap must be inside the deck plate");
            for (int k = 0; k < 3; k++) {
                assertTrue(f.section(k)[1] > f.section(k)[0], "section " + k + " is inside out at " + h);
            }
        }
        assertEquals(0.0, CoilForm.at(3.0).rods, 1e-12);
        assertEquals(1.0, CoilForm.at(4.0).rods, 1e-12);
        assertEquals(CoilForm.at(4.0).top, CoilForm.at(7.0).top, 1e-12, "no form past 4");
    }

    @Test
    @DisplayName("with no headroom the orb sinks into the donut's hole, under the cell above, and keeps its size")
    void eyeDucks() {
        for (int stage = 1; stage <= 4; stage++) {
            final CoilForm f = CoilForm.at(stage);
            assertEquals(f.orbY, f.eyeY(0.0), 1e-12, "with headroom it stands where it always stood");
            final double y = f.eyeY(1.0);
            assertTrue(y + f.orb <= 16.0 * stage - 0.25 + 1e-9, "stage " + stage + ": the orb pokes into the cell above");
            assertTrue(f.orb < f.major - f.tube, "stage " + stage + ": the orb is wider than the donut's hole");
            assertTrue(y - f.orb > CoilForm.DECK, "stage " + stage + ": the ducked orb sinks through the deck");
            assertTrue(f.eyeY(0.5) < f.orbY && f.eyeY(0.5) > y, "it travels, it does not jump");
        }
    }

    @Test
    @DisplayName("each stud's eye is picked back as that stud at every facing; a miss is no stud")
    void studs() {
        for (int turns = 0; turns < 4; turns++) {
            for (int stud = 0; stud < StormCoilStuds.COUNT; stud++) {
                final double[] eye = StormCoilStuds.anchor(turns, stud);
                assertEquals(stud, StormCoilStuds.pick(turns, eye[0], eye[1], eye[2]), "turns " + turns);
            }
            final double[] far = StormCoilStuds.turn(turns, 15.0, 8.0);
            assertEquals(-1, StormCoilStuds.pick(turns, far[0] / 16, 0.3, far[1] / 16), "the far flank is no stud");
        }
        final double[] north = StormCoilStuds.anchor(0, StormCoilStuds.IN);
        assertTrue(north[0] < 0.1 && north[2] < 0.5, "IN is on the west flank, toward the front");
    }
}
