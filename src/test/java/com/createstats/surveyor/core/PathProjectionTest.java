package com.createstats.surveyor.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathProjectionTest {
    private static PathSpline straight() {
        return PathSpline.of(Pathway.create(UUID.randomUUID(), "t", 0, new UUID(0, 1))
                .appendAll(List.of(new double[]{0, 64, 0}, new double[]{40, 64, 0})));
    }

    @Test
    void lateralIsPositiveToTheRightOfTheLine() {
        PathSpline sp = straight();

        PathProjection.Result r = PathProjection.window(sp, 12.0, 64, 2.0, 12.0, 3, 6);
        assertTrue(r.found());
        assertEquals(12.0, r.s(), 1.0e-6);
        assertEquals(2.0, r.lateral(), 1.0e-6);
        assertEquals(-3.0, PathProjection.window(sp, 12.0, 64, -3.0, 12.0, 3, 6).lateral(), 1.0e-6);
    }

    @Test
    void theWindowNeverJumpsToAFarStretch() {
        PathSpline sp = PathSpline.of(Pathway.create(UUID.randomUUID(), "t", 0, new UUID(0, 1))
                .appendAll(List.of(new double[]{0, 64, 0}, new double[]{30, 64, 0}, new double[]{32, 64, 2},
                        new double[]{30, 64, 4}, new double[]{0, 64, 4})));

        PathProjection.Result r = PathProjection.window(sp, 10.0, 64, 2.2, 10.0, 3, 6);
        assertEquals(10.0, r.s(), 0.5, "stays on its own stretch");
        PathProjection.Result g = PathProjection.global(sp, 10.0, 64, 2.2, 0, 0, 30, 4, 180);
        assertTrue(g.s() > 30, "the global search would have taken the return leg: " + g.s());
    }

    @Test
    void globalJoinWantsTheLineRunningTheWayTheVehicleFaces() {
        PathSpline sp = straight();
        assertTrue(PathProjection.global(sp, 10, 64, 1, 1, 0, 32, 4, 90).found(), "facing east along it");
        assertFalse(PathProjection.global(sp, 10, 64, 1, -1, 0, 32, 4, 90).found(), "facing west against it");
        assertTrue(PathProjection.global(sp, 10, 64, 1, 0, 0, 32, 4, 180).found(), "any way");
    }

    @Test
    void globalJoinIgnoresTheLineAboveOrBelow() {
        PathSpline sp = straight();
        assertFalse(PathProjection.global(sp, 10, 72, 0, 1, 0, 32, 4, 90).found(), "eight blocks up: a bridge over it");
        assertFalse(PathProjection.global(sp, 10, 64, 40, 1, 0, 32, 4, 90).found(), "too far");
    }
}
