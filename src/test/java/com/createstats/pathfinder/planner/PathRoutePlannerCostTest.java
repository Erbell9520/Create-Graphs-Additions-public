package com.createstats.pathfinder.planner;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathRoutePlannerCostTest {
    private static final int Y = 64;

    private static Map<Long, PathRoutePlanner.Cell> field(int x0, int z0, int x1, int z1) {
        Map<Long, PathRoutePlanner.Cell> m = new HashMap<>();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                m.put(PathRoutePlanner.cellKey(x, z), new PathRoutePlanner.Cell(x, Y, z, 0));
            }
        }
        return m;
    }

    private static PathRoutePlanner.Cell at(Map<Long, PathRoutePlanner.Cell> cells, int x, int z) {
        return cells.get(PathRoutePlanner.cellKey(x, z));
    }

    @Test
    void slopeStepPenaltyEscalatesDeeperIntoAClimb() {
        double first = PathRoutePlanner.slopeStepPenalty(0, 1);
        double later = PathRoutePlanner.slopeStepPenalty(5, 1);
        double muchLater = PathRoutePlanner.slopeStepPenalty(12, 1);
        assertTrue(later > first, "a step deeper into a sustained climb must cost more than the first step");
        assertTrue(muchLater >= later, "penalty must be monotonic in the accumulated climb run");
    }

    @Test
    void slopeStepPenaltyIsCappedPerBlock() {
        assertEquals(PathRoutePlanner.slopeStepPenalty(1000, 1),
            PathRoutePlanner.slopeStepPenalty(4000, 1), 1e-9, "per-block slope charge must saturate");
    }

    @Test
    void slopeStepPenaltyGrowsWithRise() {
        assertTrue(PathRoutePlanner.slopeStepPenalty(0, 2) > PathRoutePlanner.slopeStepPenalty(0, 1),
            "a 2-block rise must cost more than a 1-block rise");
    }

    @Test
    void longSustainedClimbCostsFarMoreThanIsolatedBumps() {
        double sustained = 0.0;
        for (int run = 0; run < 10; run++) {
            sustained += PathRoutePlanner.slopeStepPenalty(run, 1);
        }
        double isolatedBumps = 10 * PathRoutePlanner.slopeStepPenalty(0, 1);
        assertTrue(sustained > 1.8 * isolatedBumps,
            "a long steep face must be punished far harder than the same number of short bumps (sustained="
                + sustained + " isolated=" + isolatedBumps + ")");
    }

    @Test
    void obstacleBerthIsZeroInTheOpen() {
        Map<Long, PathRoutePlanner.Cell> cells = field(0, 0, 40, 40);
        assertEquals(0.0, PathRoutePlanner.obstacleBerthPenalty(cells, at(cells, 20, 20), 4.0), 1e-9,
            "an open-field cell far from anything blocked must pay no berth cost");
    }

    @Test
    void obstacleBerthGrowsCloserToTheObstacle() {
        Map<Long, PathRoutePlanner.Cell> cells = field(0, 0, 40, 40);

        cells.remove(PathRoutePlanner.cellKey(20, 20));
        double adjacent = PathRoutePlanner.obstacleBerthPenalty(cells, at(cells, 21, 20), 4.0);
        double mid = PathRoutePlanner.obstacleBerthPenalty(cells, at(cells, 23, 20), 4.0);
        double beyond = PathRoutePlanner.obstacleBerthPenalty(cells, at(cells, 27, 20), 4.0);
        assertTrue(adjacent > mid, "passing right next to a trunk must cost more than passing farther out");
        assertTrue(mid > 0.0, "passing within the berth must cost something");
        assertEquals(0.0, beyond, 1e-9, "outside the berth ring there is no cost");
    }

    @Test
    void obstacleBerthDisabledWhenClearanceTooSmall() {
        Map<Long, PathRoutePlanner.Cell> cells = field(0, 0, 40, 40);
        cells.remove(PathRoutePlanner.cellKey(20, 20));
        assertEquals(0.0, PathRoutePlanner.obstacleBerthPenalty(cells, at(cells, 21, 20), 0.0), 1e-9,
            "no berth is applied when the preferred clearance is below one block");
    }
}
