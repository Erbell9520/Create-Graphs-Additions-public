package com.createstats.vccmk4.air.core;

import com.createstats.aerial.V3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HullShapeTest {
    private static final double FLAT = 64.0;

    @Test
    void theKeelGridNeverPassesItsLimit() {
        for (int[] s : new int[][] {{1, 1}, {65, 106}, {16, 16}, {17, 15}, {2048, 2048}, {2048, 1}, {300, 7}}) {
            int cell = HullShape.keelCell(s[0], s[1]);
            long cells = (long) ((s[0] + cell - 1) / cell) * ((s[1] + cell - 1) / cell);
            assertTrue(cells <= HullShape.KEEL_MAX, s[0] + " by " + s[1] + " at " + cell + ": " + cells + " cells");
            assertTrue(cell == 1 || (long) ((s[0] + cell - 2) / (cell - 1)) * ((s[1] + cell - 2) / (cell - 1)) > HullShape.KEEL_MAX,
                    "the smallest cell that fits");
        }
        assertEquals(1, HullShape.keelCell(16, 16), "a small ship keeps every column");
    }

    @Test
    void onlyABoxInsideOnePlotCanBeWalked() {
        assertTrue(HullShape.fits(new int[] {0, -64, 0, 2047, 319, 2047}));
        assertFalse(HullShape.fits(new int[] {0, 0, 0, 2048, 0, 0}));
        assertFalse(HullShape.fits(new int[] {5, 0, 0, 4, 0, 0}));
        assertFalse(HullShape.fits(null));
        assertThrows(IllegalArgumentException.class, () -> new HullShape(new int[] {0, 0, 0, -1, 0, 0}));
    }

    @Test
    void blocksComeBackAboutTheCentreOfMassAndTheEnvelopeIsCountedApart() {
        int[] box = {20480997, 119, 20503501, 20481061, 188, 20503606};
        HullShape hull = new HullShape(box);
        hull.add(20480997, 119, 20503501, false);
        hull.add(20481061, 188, 20503606, true);
        hull.add(20481000, 150, 20503550, true);
        assertEquals(3, hull.blocks());
        assertEquals(2, hull.airtightBlocks());
        List<V3> all = new ArrayList<>(hull.centres(20481029.5, 148.0, 20503555.0));
        assertEquals(3, all.size());
        assertEquals(new V3(20480997.5 - 20481029.5, 119.5 - 148.0, 20503501.5 - 20503555.0), all.get(0));
        assertEquals(new V3(20481061.5 - 20481029.5, 188.5 - 148.0, 20503606.5 - 20503555.0), all.get(1), "the far corner");
        List<V3> env = new ArrayList<>(hull.envelope(20481029.5, 148.0, 20503555.0));
        assertEquals(List.of(all.get(1), all.get(2)), env);
        assertEquals(2, hull.envelope(0, 0, 0).size());
    }

    @Test
    void theKeelKeepsTheLowestBlockOfEachCell() {
        HullShape hull = new HullShape(new int[] {0, 0, 0, 3, 9, 3});
        hull.add(1, 5, 1, false);
        hull.add(1, 2, 1, false);
        hull.add(1, 7, 1, false);
        hull.add(2, 4, 3, false);
        double[][] keel = hull.keel();
        assertEquals(2, keel.length);
        assertEquals(2.0, keel[0][1], "the underside of the lowest block");
        assertEquals(1.5, keel[0][0]);
        assertEquals(4.0, keel[1][1]);
    }

    @Test
    void noGroundKnownIsNoKeelButTheLowestPointStillIs() {
        double[][] points = {{0.5, 3.0, 0.5}, {4.5, 1.0, 0.5}};
        HullShape.Keel k = HullShape.keel(points, (x, y, z, out) -> {
            out[0] = x;
            out[1] = y + 100.0;
            out[2] = z;
        }, (x, z) -> Double.NaN);
        assertTrue(Double.isNaN(k.clearance()));
        assertEquals(101.0, k.lowestY());
        assertEquals(HullShape.Keel.NONE, HullShape.keel(new double[0][], (x, y, z, out) -> { }, (x, z) -> 0.0));
    }

    @Test
    void theKeelIsTheLeastHeightOverTheGroundUnderEachPoint() {
        double[][] points = {{0.5, 0.0, 0.5}, {10.5, 0.0, 0.5}};

        HullShape.Keel k = HullShape.keel(points, (x, y, z, out) -> {
            out[0] = x;
            out[1] = y + 74.0;
            out[2] = z;
        }, (x, z) -> x > 5.0 ? 68.0 : FLAT);
        assertEquals(6.0, k.clearance(), 1.0e-9);
        assertEquals(74.0, k.pointY(), 1.0e-9);
        assertEquals(68.0, k.pointY() - k.clearance(), 1.0e-9, "the ground under the governing point");
    }

    @Test
    void aTurnedShipThreeBlocksUpIsNotReadAsResting() {
        int len = 60;
        int beam = 20;
        int[] box = {0, 100, 0, len - 1, 120, beam - 1};
        HullShape hull = new HullShape(box);
        List<int[]> blocks = new ArrayList<>();
        for (int x = 0; x < len; x++) {
            double t = (x - 29.5) / 29.5;
            int bottom = 100 + (int) Math.round(t * t * 8.0);
            for (int z = 0; z < beam; z++) {
                for (int y = bottom; y <= 120; y++) {
                    hull.add(x, y, z, y > 104);
                    blocks.add(new int[] {x, y, z});
                }
            }
        }
        double com = 30.0;
        double comY = 110.0;
        double angle = Math.toRadians(10.0);
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);

        double[] lift = {0.0};
        HullShape.ToWorld turn = (x, y, z, out) -> {
            double dx = x - com;
            double dy = y - comY;
            out[0] = dx * cos - dy * sin;
            out[1] = dx * sin + dy * cos + lift[0];
            out[2] = z;
        };
        double[] w = new double[3];
        double lowestUnderside = Double.POSITIVE_INFINITY;
        for (int[] b : blocks) {
            turn.apply(b[0] + 0.5, b[1], b[2] + 0.5, w);
            lowestUnderside = Math.min(lowestUnderside, w[1]);
        }
        lift[0] = FLAT + 3.0 - lowestUnderside;

        HullShape.Keel keel = HullShape.keel(hull.keel(), turn, (x, z) -> FLAT);
        assertEquals(3.0, keel.clearance(), 0.6, "the hull's keel reads the height it flies at");
        assertTrue(keel.clearance() > AerialPilot.LAND_HEIGHT);

        double boxBottom = Double.POSITIVE_INFINITY;
        for (int cx : new int[] {box[0], box[3] + 1}) {
            for (int cy : new int[] {box[1], box[4] + 1}) {
                turn.apply(cx, cy, 0.0, w);
                boxBottom = Math.min(boxBottom, w[1]);
            }
        }
        double boxKeel = boxBottom - FLAT;
        assertTrue(boxKeel < AerialPilot.LAND_HEIGHT, "the box keel read " + boxKeel + ": resting, so moored");
        assertTrue(keel.clearance() - boxKeel > 3.0);
    }
}
