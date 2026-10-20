package com.createstats.power;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PduStudsTest {
    private static final Direction[] FACINGS =
            {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    @Test
    @DisplayName("there are eight studs, because the model draws eight")
    void eightStuds() {
        assertEquals(8, PduStuds.COUNT);
    }

    @Test
    @DisplayName("aiming at a stud's own centre picks that stud, at every facing")
    void centresPickThemselves() {
        for (Direction facing : FACINGS) {
            for (int stud = 0; stud < PduStuds.COUNT; stud++) {
                final double[] world = PduStuds.toBlockSpace(facing,
                        PduStuds.localX(stud), PduStuds.localZ(stud));
                assertEquals(stud, PduStuds.pick(facing, world[0], world[1]),
                        "stud " + stud + " missed itself facing " + facing);
            }
        }
    }

    @Test
    @DisplayName("the two transforms are inverses, which is what stops the highlight mirroring")
    void transformsRoundTrip() {
        for (Direction facing : FACINGS) {
            for (int stud = 0; stud < PduStuds.COUNT; stud++) {
                final double mx = PduStuds.localX(stud);
                final double mz = PduStuds.localZ(stud);
                final double[] world = PduStuds.toBlockSpace(facing, mx, mz);
                final double[] back = PduStuds.toModelSpace(facing, world[0], world[1]);
                assertEquals(mx, back[0], 1e-9, "x did not round trip facing " + facing);
                assertEquals(mz, back[1], 1e-9, "z did not round trip facing " + facing);
            }
        }
    }

    @Test
    @DisplayName("a north facing panel needs no transform at all")
    void northIsTheAuthoredOrientation() {
        final double[] p = PduStuds.toModelSpace(Direction.NORTH, 0.3, 0.7);
        assertEquals(0.3, p[0], 1e-9);
        assertEquals(0.7, p[1], 1e-9);
    }

    @Test
    @DisplayName("rotating the panel really does move where a stud is, or nothing was transformed")
    void rotationActuallyMovesStuds() {
        final double[] north = PduStuds.toBlockSpace(Direction.NORTH,
                PduStuds.localX(0), PduStuds.localZ(0));
        final double[] east = PduStuds.toBlockSpace(Direction.EAST,
                PduStuds.localX(0), PduStuds.localZ(0));
        assertTrue(Math.abs(north[0] - east[0]) > 1e-6 || Math.abs(north[1] - east[1]) > 1e-6,
                "a quarter turn left stud 0 exactly where it was, so nothing is being rotated");
    }

    @Test
    @DisplayName("no two studs share a place, at any facing")
    void studsAreDistinct() {
        for (Direction facing : FACINGS) {
            for (int a = 0; a < PduStuds.COUNT; a++) {
                for (int b = a + 1; b < PduStuds.COUNT; b++) {
                    final double[] pa = PduStuds.toBlockSpace(facing,
                            PduStuds.localX(a), PduStuds.localZ(a));
                    final double[] pb = PduStuds.toBlockSpace(facing,
                            PduStuds.localX(b), PduStuds.localZ(b));
                    assertTrue(Math.hypot(pa[0] - pb[0], pa[1] - pb[1]) > 1e-6,
                            "studs " + a + " and " + b + " coincide facing " + facing);
                }
            }
        }
    }

    @Test
    @DisplayName("aiming off the bus bar picks nothing rather than the nearest stud anyway")
    void missesAreMisses() {
        assertEquals(-1, PduStuds.pick(Direction.NORTH, 0.02, 0.02));
        assertEquals(-1, PduStuds.pick(Direction.NORTH, 0.98, 0.98));
    }

    @Test
    @DisplayName("every stud sits inside the block, so the highlight never floats off the panel")
    void studsAreInsideTheBlock() {
        for (Direction facing : FACINGS) {
            for (int stud = 0; stud < PduStuds.COUNT; stud++) {
                final double[] box = PduStuds.boxOf(facing, stud);
                assertTrue(box[0] >= 0.0 && box[1] >= 0.0 && box[2] <= 1.0 && box[3] <= 1.0,
                        "stud " + stud + " box escapes the block facing " + facing);
                assertTrue(box[2] > box[0] && box[3] > box[1], "stud " + stud + " box is inside out");
            }
        }
    }
}
