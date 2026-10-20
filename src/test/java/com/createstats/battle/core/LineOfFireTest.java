package com.createstats.battle.core;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class LineOfFireTest {
    private static final Trajectory STRAIGHT = new Trajectory(0.0, 0.0, false);

    private static final Trajectory FALLING = new Trajectory(-0.05, 0.0, false);

    private static final LineOfFire.ToLocal SAME = (world, out) -> {
        out[0] = world[0];
        out[1] = world[1];
        out[2] = world[2];
    };

    @Test
    void aStraightShotStopsAtTheWallCellItMeets() {
        LineOfFire.Occupancy occ = solid(new int[][]{{0, 5, 6}, {1, 5, 6}, {-1, 5, 6}, {0, 5, 7}, {0, 6, 6}});
        int[] hit = LineOfFire.ownShip(STRAIGHT, new double[]{0.5, 5.5, 0.5}, new double[]{0, 0, 2}, null, SAME, occ,
                new int[]{-10, 0, -10}, new int[]{10, 10, 10}, null, null);
        assertArrayEquals(new int[]{0, 5, 6}, hit);

        LineOfFire.Occupancy gap = solid(new int[][]{{1, 5, 6}, {-1, 5, 6}, {0, 6, 6}, {0, 4, 6}});
        assertNull(LineOfFire.ownShip(STRAIGHT, new double[]{0.5, 5.5, 0.5}, new double[]{0, 0, 2}, null, SAME, gap,
                new int[]{-10, 0, -10}, new int[]{10, 10, 10}, null, null));
    }

    @Test
    void gravityCarriesTheShotUnderAWallItWasLevelWith() {
        double[] muzzle = {0.5, 5.2, 0.5};
        double[] v0 = {0, 0, 1};
        int[] min = {-10, 0, -10};
        int[] max = {10, 10, 10};
        assertNull(LineOfFire.ownShip(FALLING, muzzle, v0, null, SAME, solid(new int[][]{{0, 5, 5}}), min, max,
                null, null));
        assertArrayEquals(new int[]{0, 4, 5}, LineOfFire.ownShip(FALLING, muzzle, v0, null, SAME,
                solid(new int[][]{{0, 5, 5}, {0, 4, 5}}), min, max, null, null));
    }

    @Test
    void theMuzzleCellsAndTheGunsOwnMountAreNeverTheBlock() {
        double[] muzzle = {0.5, 5.5, 0.5};
        double[] v0 = {0, 0, 2};
        int[] min = {-10, 0, -10};
        int[] max = {10, 10, 10};
        LineOfFire.Occupancy occ = solid(new int[][]{{0, 5, 0}, {0, 5, 1}, {0, 5, 2}, {0, 5, 3}, {0, 5, 4}});
        assertArrayEquals(new int[]{0, 5, 2}, LineOfFire.ownShip(STRAIGHT, muzzle, v0, null, SAME, occ, min, max,
                null, null));

        assertArrayEquals(new int[]{0, 5, 4}, LineOfFire.ownShip(STRAIGHT, muzzle, v0, null, SAME, occ, min, max,
                new int[]{0, 5, 2}, new int[]{0, 6, 3}));
    }

    @Test
    void aBroadsideClearFromAStillShipIsBlockedWhenTheShipMovesAtABlockATick() {
        double[] muzzle = {0.5, 1.5, 0.5};
        double[] v0 = {0, 0, 2};
        LineOfFire.Occupancy envelope = (x, y, z) -> x >= -8 && x <= -2 && y >= 0 && y <= 3 && z >= 3 && z <= 6;
        int[] min = {-10, -2, -4};
        int[] max = {10, 5, 6};

        assertNull(LineOfFire.ownShip(FALLING, muzzle, v0, null, SAME, envelope, min, max, null, null));

        assertArrayEquals(new int[]{-2, 1, 3}, LineOfFire.ownShip(FALLING, muzzle, v0, new double[]{1, 0, 0}, SAME,
                envelope, min, max, null, null));
    }

    @Test
    void aShipFasterThanItsShellOvertakesIt() {
        double[] muzzle = {0.5, 1.5, 0.5};
        double[] v0 = {1, 0, 0};
        LineOfFire.Occupancy bulkhead = (x, y, z) -> x == -3 && y >= -2 && y <= 4 && z >= -2 && z <= 2;
        int[] min = {-10, -2, -2};
        int[] max = {10, 4, 2};
        assertArrayEquals(new int[]{-3, 1, 0}, LineOfFire.ownShip(STRAIGHT, muzzle, v0, new double[]{1.5, 0, 0},
                SAME, bulkhead, min, max, null, null));
        assertNull(LineOfFire.ownShip(STRAIGHT, muzzle, v0, null, SAME, bulkhead, min, max, null, null));

        LineOfFire.Occupancy stem = (x, y, z) -> x == 10 && y >= -2 && y <= 4 && z >= -2 && z <= 2;
        double[] tip = {15.5, 1.5, 0.5};
        assertArrayEquals(new int[]{10, 1, 0}, LineOfFire.ownShip(STRAIGHT, tip, v0, new double[]{1.5, 0, 0}, SAME,
                stem, min, max, null, null));
        assertNull(LineOfFire.ownShip(STRAIGHT, tip, v0, null, SAME, stem, min, max, null, null));
    }

    @Test
    void aTurnedShipIsWalkedInItsOwnPlot() {
        int qx = 20_480_000;
        int qz = 20_480_000;
        LineOfFire.ToLocal pose = (w, out) -> {
            out[0] = qx - (w[2] - 200.0);
            out[1] = 64.0 + (w[1] - 64.0);
            out[2] = qz + (w[0] - 100.0);
        };
        LineOfFire.Occupancy bulkhead = (x, y, z) -> x == qx + 6 && y >= 60 && y <= 70 && z >= qz - 5 && z <= qz + 5;
        double[] muzzle = {100.5, 65.5, 199.5};
        double[] v0 = {0, 0, -2};
        int[] min = {qx - 10, 60, qz - 10};
        int[] max = {qx + 10, 70, qz + 10};

        assertArrayEquals(new int[]{qx + 6, 65, qz}, LineOfFire.ownShip(STRAIGHT, muzzle, v0, null, pose, bulkhead,
                min, max, null, null));

        assertArrayEquals(new int[]{qx + 6, 65, qz - 3}, LineOfFire.ownShip(STRAIGHT, muzzle, v0,
                new double[]{1, 0, 0}, pose, bulkhead, min, max, null, null));
    }

    @Test
    void theWalkStopsWhereThePathLeavesTheGrownBounds() {
        double[] muzzle = {5.5, 1.5, 0.5};
        double[] v0 = {2, 0, 0};
        int[] min = {0, 0, 0};
        int[] max = {10, 3, 3};
        assertArrayEquals(new int[]{12, 1, 0}, LineOfFire.ownShip(STRAIGHT, muzzle, v0, null, SAME,
                solid(new int[][]{{12, 1, 0}}), min, max, null, null));
        assertNull(LineOfFire.ownShip(STRAIGHT, muzzle, v0, null, SAME, solid(new int[][]{{13, 1, 0}}), min, max,
                null, null));

        assertArrayEquals(new int[]{2, 1, 0}, LineOfFire.ownShip(STRAIGHT, new double[]{-5.5, 1.5, 0.5}, v0, null,
                SAME, solid(new int[][]{{2, 1, 0}}), min, max, null, null));

        assertNull(LineOfFire.ownShip(STRAIGHT, new double[]{-5.5, 1.5, 0.5}, new double[]{-2, 0, 0}, null, SAME,
                solid(new int[][]{{2, 1, 0}}), min, max, null, null));

        Trajectory slowing = new Trajectory(0.0, 0.1, false);
        assertNull(LineOfFire.ownShip(slowing, new double[]{9.5, 1.5, 0.5}, new double[]{1.5, 0, 0},
                new double[]{0.5, 0, 0}, SAME, solid(new int[][]{{3, 1, 0}}), min, max, null, null));
    }

    @Test
    void aWalkOutOfCellsIsUnsureButOneOutOfTicksIsClear() {
        double[] muzzle = {0.5, 1.5, 0.5};
        int[] min = {-1000, 0, -10};
        int[] max = {1000, 3, 10};
        double[] fast = {12, 0, 0};
        assertArrayEquals(new int[]{511, 1, 0}, LineOfFire.ownShip(STRAIGHT, muzzle, fast, null, SAME,
                solid(new int[][]{{511, 1, 0}}), min, max, null, null));
        assertSame(LineOfFire.UNSURE, LineOfFire.ownShip(STRAIGHT, muzzle, fast, null, SAME,
                solid(new int[][]{{512, 1, 0}}), min, max, null, null));
        assertSame(LineOfFire.UNSURE, LineOfFire.ownShip(STRAIGHT, muzzle, fast, null, SAME, solid(new int[0][]),
                min, max, null, null), "an empty hull that long is still not known to be clear");

        double[] slow = {0.25, 0, 0};
        assertArrayEquals(new int[]{16, 1, 0}, LineOfFire.ownShip(STRAIGHT, muzzle, slow, null, SAME,
                solid(new int[][]{{16, 1, 0}}), min, max, null, null));
        assertNull(LineOfFire.ownShip(STRAIGHT, muzzle, slow, null, SAME, solid(new int[][]{{17, 1, 0}}), min, max,
                null, null));
        assertEquals(64, LineOfFire.MAX_TICKS);
        assertEquals(512, LineOfFire.MAX_CELLS);
    }

    @Test
    void firstBoxFindsTheNearestBoxAlongThePathWithinItsLength() {
        double[] muzzle = {0.5, 70.0, 0.5};
        double[] v0 = {0, 0, 3};
        double[] far = {-1, 69, 30, 2, 72, 31};
        double[] near = {-1, 69, 10, 2, 72, 11};
        double[] aside = {20, 69, 5, 22, 72, 6};
        assertEquals(1, LineOfFire.firstBox(STRAIGHT, muzzle, v0, 64.0, new double[][]{far, near, aside}),
                "the nearer box is met first, whatever its index");
        assertEquals(0, LineOfFire.firstBox(STRAIGHT, muzzle, v0, 64.0, new double[][]{far, aside}));
        assertEquals(-1, LineOfFire.firstBox(STRAIGHT, muzzle, v0, 64.0, new double[][]{aside}));
        assertEquals(-1, LineOfFire.firstBox(STRAIGHT, muzzle, v0, 64.0, new double[0][]));

        assertEquals(-1, LineOfFire.firstBox(STRAIGHT, muzzle, v0, 9.4, new double[][]{far, near}));
        assertEquals(1, LineOfFire.firstBox(STRAIGHT, muzzle, v0, 9.6, new double[][]{far, near}));

        assertEquals(-1, LineOfFire.firstBox(STRAIGHT, muzzle, v0, 20.0, new double[][]{far}));
        assertEquals(0, LineOfFire.firstBox(STRAIGHT, muzzle, v0, 30.0, new double[][]{far}));

        assertEquals(0, LineOfFire.firstBox(STRAIGHT, muzzle, v0, 64.0, new double[][]{{0, 69, 0, 1, 71, 1}, near}));

        assertEquals(1, LineOfFire.firstBox(STRAIGHT, muzzle, new double[]{0, 0, 40}, 64.0,
                new double[][]{far, near}));

        double[] level = {0, 0, 1};
        assertEquals(0, LineOfFire.firstBox(FALLING, muzzle, level, 64.0, new double[][]{{-1, 58, 19, 2, 62, 21}}));
        assertEquals(-1, LineOfFire.firstBox(FALLING, muzzle, level, 64.0, new double[][]{{-1, 68, 19, 2, 72, 21}}));
    }

    private static LineOfFire.Occupancy solid(int[][] cells) {
        Set<String> set = new HashSet<>();
        for (int[] c : cells) {
            set.add(c[0] + "," + c[1] + "," + c[2]);
        }
        return (x, y, z) -> set.contains(x + "," + y + "," + z);
    }
}
