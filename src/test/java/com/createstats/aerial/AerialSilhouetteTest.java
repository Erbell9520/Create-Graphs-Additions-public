package com.createstats.aerial;

import java.util.ArrayList;
import java.util.List;

import com.createstats.aerial.AerialSnapshot.Silhouette;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AerialSilhouetteTest {
    private static List<V3> box(int dx, int dy, int dz) {
        final List<V3> out = new ArrayList<>();
        for (int x = 0; x < dx; x++) {
            for (int y = 0; y < dy; y++) {
                for (int z = 0; z < dz; z++) {
                    out.add(new V3(x + 0.5 - dx / 2.0, y + 0.5 - dy / 2.0, z + 0.5 - dz / 2.0));
                }
            }
        }
        return out;
    }

    @Test
    void aBoxProjectsToItsOwnSize() {
        final Silhouette s = AerialSilhouette.of(box(4, 3, 10), new V3(0.0, 0.0, -1.0));
        assertEquals(1.0, s.cell(), 0.0);
        assertEquals(10, s.cols());
        assertEquals(3, s.sideRows());
        assertEquals(4, s.topRows());
        assertEquals(-1.5, s.originU(), 1.0e-9, "the keel is 1.5 m under the CoM");
        for (int c = 0; c < 10; c++) {
            for (int r = 0; r < 3; r++) {
                assertTrue(s.sideAt(c, r));
            }
        }
        assertFalse(s.sideAt(10, 0));
        assertFalse(s.sideAt(-1, 0));
        assertEquals(10.0, s.length(), 0.0);
    }

    @Test
    void theNoseDecidesWhichWayIsLong() {
        final Silhouette s = AerialSilhouette.of(box(10, 3, 4), new V3(1.0, 0.0, 0.0));
        assertEquals(10, s.cols());
        assertEquals(4, s.topRows());
    }

    @Test
    void aShipTooBigForTheGridGrowsItsCells() {
        final List<V3> long0 = new ArrayList<>();
        for (int z = 0; z < 300; z++) {
            long0.add(new V3(0.5, 0.5, z + 0.5 - 150.0));
        }
        final Silhouette s = AerialSilhouette.of(long0, new V3(0.0, 0.0, -1.0));
        assertTrue(s.cols() <= Silhouette.MAX_CELLS);
        assertEquals(4.0, s.cell(), 0.0);
        assertEquals(75, s.cols());
    }

    @Test
    void theSideViewSeesTheHullShape() {
        final List<V3> blocks = new ArrayList<>();
        for (int z = -5; z < 5; z++) {
            blocks.add(new V3(0.5, 0.5, z + 0.5));
        }
        for (int y = 1; y < 6; y++) {
            blocks.add(new V3(0.5, y + 0.5, 4.5));
        }
        final Silhouette s = AerialSilhouette.of(blocks, new V3(0.0, 0.0, -1.0));

        assertTrue(s.sideAt(0, 5));
        assertFalse(s.sideAt(9, 5));
        assertTrue(s.sideAt(9, 0));
    }
}
