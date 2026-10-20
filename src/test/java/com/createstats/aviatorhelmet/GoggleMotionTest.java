package com.createstats.aviatorhelmet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoggleMotionTest {
    private static final double[][] GENERATOR = {
        {0.0, 0.000000000000, 0.000000000000},
        {0.05, 0.093750000000, 0.132812500000},
        {0.1, 0.300000000000, 0.425000000000},
        {0.2, 0.600000000000, 0.850000000000},
        {0.35, 3.865625000000, 0.850000000000},
        {0.5, 11.050000000000, 0.850000000000},
        {0.65, 18.234375000000, 0.850000000000},
        {0.8, 21.500000000000, 0.850000000000},
        {0.9, 22.750000000000, 0.445000000000},
        {0.95, 23.609375000000, 0.166562500000},
        {1.0, 24.000000000000, 0.040000000000},
    };

    @Test
    void thePathIsTheGeneratorsOwn() {
        for (double[] row : GENERATOR) {
            final double[] p = GoggleMotion.path(row[0]);
            assertEquals(row[1], p[0], 1.0e-9, "angle at u " + row[0]);
            assertEquals(row[2], p[1], 1.0e-9, "push at u " + row[0]);
        }
        assertEquals(9.2, GoggleMotion.PIVOT_Y);
        assertEquals(13.25, GoggleMotion.PIVOT_Z);
    }

    @Test
    void aStrokeStartsAndEndsAtItsStops() {
        assertEquals(1.0, GoggleMotion.u(true, 0.0), 1.0e-12);
        assertEquals(0.0, GoggleMotion.u(true, GoggleMotion.STROKE_SECONDS), 1.0e-12);
        assertEquals(0.0, GoggleMotion.u(false, 0.0), 1.0e-12);
        assertEquals(1.0, GoggleMotion.u(false, GoggleMotion.STROKE_SECONDS), 1.0e-12);

        final double[] down = GoggleMotion.pose(true, 5.0);
        final double[] rest = GoggleMotion.rest(true);
        assertEquals(rest[0], down[0], 1.0e-12);
        assertEquals(rest[1], down[1], 1.0e-9);
        assertEquals(GoggleMotion.UP_ANGLE, GoggleMotion.pose(false, 5.0)[0], 1.0e-12);
    }

    @Test
    void theHandStartsAndStopsGently() {
        final double h = 1.0e-4;
        assertEquals(0.0, (GoggleMotion.u(false, h) - GoggleMotion.u(false, 0.0)) / h, 1.0e-3);
        final double end = GoggleMotion.STROKE_SECONDS;
        assertEquals(0.0, (GoggleMotion.u(false, end) - GoggleMotion.u(false, end - h)) / h, 1.0e-3);

        double last = -1.0;
        for (int i = 0; i <= 100; i++) {
            final double u = GoggleMotion.u(false, GoggleMotion.STROKE_SECONDS * i / 100.0);
            assertTrue(u >= last);
            last = u;
        }
    }

    @Test
    void theCushionsGiveOnlyAfterTheyLand() {
        assertEquals(0.0, GoggleMotion.squash(0.0));
        assertEquals(0.0, GoggleMotion.squash(GoggleMotion.LAND_SECONDS - 1.0e-6));
        double most = 0.0;
        for (int i = 0; i <= 200; i++) {
            most = Math.max(most, Math.abs(GoggleMotion.squash(GoggleMotion.LAND_SECONDS + i * 0.002)));
        }
        assertTrue(most > 0.02 && most <= 0.06, "the give is a few hundredths of a pixel: " + most);
        assertTrue(Math.abs(GoggleMotion.squash(GoggleMotion.LAND_SECONDS + 0.5)) < 1.0e-3, "and it dies away");
    }

    @Test
    void theLensesSweepOverTheEyesAndOff() {
        assertEquals(0.0, GoggleMotion.lensCover(true, 0.0), 1.0e-12);
        assertEquals(1.0, GoggleMotion.lensCover(true, GoggleMotion.STROKE_SECONDS), 1.0e-12);
        assertEquals(1.0, GoggleMotion.lensCover(false, 0.0), 1.0e-12);
        assertEquals(0.0, GoggleMotion.lensCover(false, GoggleMotion.STROKE_SECONDS), 1.0e-12);
    }

    @Test
    void theLampLightsAsTheGogglesLandAndHeatsThrough() {
        assertFalse(LampWarmth.lit(true, 0.0), "dark while the goggles come down");
        assertFalse(LampWarmth.lit(true, GoggleMotion.LAND_SECONDS - 1.0e-6));
        assertTrue(LampWarmth.lit(true, GoggleMotion.LAND_SECONDS));

        assertEquals(LampWarmth.FIRST_LIGHT, LampWarmth.level(true, GoggleMotion.LAND_SECONDS), 1.0e-12);
        assertEquals(0.0, LampWarmth.warm(true, GoggleMotion.LAND_SECONDS), 1.0e-12);

        final double hot = GoggleMotion.LAND_SECONDS + LampWarmth.WARM_SECONDS;
        assertEquals(1.0, LampWarmth.level(true, hot), 1.0e-12);
        assertEquals(1.0, LampWarmth.warm(true, hot), 1.0e-12);

        assertFalse(LampWarmth.lit(false, 10.0));
        assertEquals(0.0, LampWarmth.level(false, 10.0));
    }

    @Test
    void aNewStrokeWaitsForTheLastToLand() {
        assertTrue(GoggleMotion.STROKE_LOCK_TICKS / 20.0 >= GoggleMotion.STROKE_SECONDS,
                "the lock covers the whole stroke, or a second V starts one from the middle of the first");
        assertFalse(AviatorModes.acceptsFlick(100, 100 + GoggleMotion.STROKE_LOCK_TICKS - 1));
        assertTrue(AviatorModes.acceptsFlick(100, 100 + GoggleMotion.STROKE_LOCK_TICKS));
    }
}
