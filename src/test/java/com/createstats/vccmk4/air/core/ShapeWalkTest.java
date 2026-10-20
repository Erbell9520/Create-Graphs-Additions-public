package com.createstats.vccmk4.air.core;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShapeWalkTest {
    private static final int[] V3_BOX = {20480997, 119, 20503501, 20481061, 188, 20503606};

    private static final class Solid implements ShapeWalk.Sections {
        final Set<Long> seen = new HashSet<>();
        long cells;
        int spentThisStep;
        int worstStep;

        @Override
        public int read(int sx, int sy, int sz, int x0, int y0, int z0, int x1, int y1, int z1) {
            assertTrue(x0 >> 4 == sx && x1 >> 4 == sx && y0 >> 4 == sy && y1 >> 4 == sy && z0 >> 4 == sz && z1 >> 4 == sz,
                    "the bounds stay inside their section");
            assertTrue(seen.add(((long) sx << 42) ^ ((long) sy << 21) ^ sz), "a section read twice");
            int n = (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1);
            cells += n;
            spentThisStep += Math.max(ShapeWalk.SKIP_COST, n);
            return n;
        }

        boolean step(ShapeWalk walk, int budget) {
            spentThisStep = 0;
            boolean done = walk.step(budget, this);
            worstStep = Math.max(worstStep, spentThisStep);
            return done;
        }
    }

    private static long volume(int[] b) {
        return (long) (b[3] - b[0] + 1) * (b[4] - b[1] + 1) * (b[5] - b[2] + 1);
    }

    @Test
    void theOwnersShipIsReadWholeInUnderASecond() {
        assertTrue(volume(V3_BOX) > 1 << 18, "over the cap that drew the v3 no sketch until 1.15.27");
        ShapeWalk walk = new ShapeWalk(V3_BOX);
        Solid solid = new Solid();
        int ticks = 0;
        while (!solid.step(walk, ShapeWalk.CELLS_PER_TICK)) ticks++;
        ticks++;
        assertEquals(volume(V3_BOX), solid.cells, "every cell of the box, once");
        assertEquals(walk.sections(), solid.seen.size());
        assertTrue(ticks <= 16, ticks + " ticks for 482,300 cells");
        assertEquals(ticks, walk.steps());
        assertTrue(walk.done());
        assertTrue(walk.step(ShapeWalk.CELLS_PER_TICK, solid), "a finished walk stays finished");
    }

    @Test
    void noTickRunsPastItsBudgetByMoreThanOneSection() {
        Solid solid = new Solid();
        ShapeWalk walk = new ShapeWalk(V3_BOX);
        while (!solid.step(walk, ShapeWalk.CELLS_PER_TICK)) {
        }
        assertTrue(solid.worstStep < ShapeWalk.CELLS_PER_TICK + ShapeWalk.SECTION_CELLS, "worst tick " + solid.worstStep);
    }

    @Test
    void airIsPassedOverCheaplyButNotWithoutLimit() {
        int[] plot = {0, -64, 0, 2047, 319, 2047};
        ShapeWalk walk = new ShapeWalk(plot);
        int[] visited = {0};
        ShapeWalk.Sections empty = (sx, sy, sz, x0, y0, z0, x1, y1, z1) -> {
            visited[0]++;
            return 0;
        };
        int worst = 0;
        int steps = 0;
        while (true) {
            int before = visited[0];
            boolean done = walk.step(ShapeWalk.CELLS_PER_TICK, empty);
            worst = Math.max(worst, visited[0] - before);
            steps++;
            if (done) break;
        }
        assertEquals(walk.sections(), visited[0]);
        assertEquals(ShapeWalk.CELLS_PER_TICK / ShapeWalk.SKIP_COST, worst, "sections looked at in one tick");
        assertTrue(steps < 1000, steps + " ticks to pass over a whole empty plot");
    }

    @Test
    void aBoxInsideOneSectionIsOneRead() {
        int[] box = {3, 70, 5, 9, 74, 12};
        Solid solid = new Solid();
        ShapeWalk walk = new ShapeWalk(box);
        assertTrue(solid.step(walk, ShapeWalk.CELLS_PER_TICK));
        assertEquals(1, solid.seen.size());
        assertEquals(volume(box), solid.cells);
    }

    @Test
    void negativeCoordinatesSplitOnTheRightSections() {
        int[] box = {-17, -3, 0, 0, 1, 15};
        Solid solid = new Solid();
        ShapeWalk walk = new ShapeWalk(box);
        assertTrue(solid.step(walk, ShapeWalk.CELLS_PER_TICK));
        assertEquals(volume(box), solid.cells);
        assertEquals(3 * 2 * 1, solid.seen.size(), "x sections -2..0, y -1..0, z 0");
    }

    @Test
    void anEmptyBoxIsDoneAtOnce() {
        ShapeWalk walk = new ShapeWalk(new int[] {5, 5, 5, 4, 5, 5});
        assertTrue(walk.done());
        assertFalse(walk.steps() > 0);
    }
}
