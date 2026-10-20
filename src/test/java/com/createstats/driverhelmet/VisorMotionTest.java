package com.createstats.driverhelmet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisorMotionTest {
    private static final double EPS = 1e-9;

    @Test
    void theOpenStrokeReachesTheStopAtAThirdOfASecondAndOvershootsIt() {
        assertTrue(VisorMotion.angleAt(true, 0.30) < VisorMotion.OPEN_MAX, "not at the stop yet at 0.30 s");
        assertTrue(VisorMotion.angleAt(true, 0.3126) > 75.5, "at the stop by 0.313 s");
        final double peak = VisorMotion.angleAt(true, 0.3168);
        assertTrue(peak > VisorMotion.OPEN_MAX + 0.5 && peak < VisorMotion.OPEN_MAX + 1.5,
                "overshoot about a degree, not a slam through the stop: " + peak);
    }

    @Test
    void theOpenStrokeWobblesOffTheStopAndSettlesThere() {
        assertTrue(VisorMotion.angleAt(true, 0.3334) < VisorMotion.OPEN_MAX, "bounces back off the stop");
        assertTrue(VisorMotion.angleAt(true, 0.3542) > VisorMotion.OPEN_MAX, "and onto it again");
        final double rest = VisorMotion.angleAt(true, 5.0);
        assertEquals(75.85, rest, EPS);
        assertEquals(rest, VisorMotion.angleAt(true, VisorMotion.settleSeconds(true)), EPS);
        assertTrue(VisorMotion.settleSeconds(true) < 0.65, "still within 13 ticks");
    }

    @Test
    void theCloseStrokeSquashesTheGasketAndHopsBackOffIt() {
        assertEquals(VisorMotion.OPEN_MAX, VisorMotion.angleAt(false, 0.0), EPS);
        final double squash = VisorMotion.angleAt(false, 0.25);
        assertTrue(squash < -1.0 && squash > -2.0, "gasket compressed about 1.3 degrees: " + squash);
        assertTrue(VisorMotion.angleAt(false, 0.2668) > 0.5, "hops back up off the gasket");
        assertEquals(-0.04, VisorMotion.angleAt(false, 5.0), EPS);
        assertTrue(VisorMotion.settleSeconds(false) < 0.45, "latched and still within 9 ticks");
    }

    @Test
    void theTablesRunForwardInTimeAndStartAtRest() {
        for (double[][] table : new double[][][]{VisorMotion.OPEN, VisorMotion.CLOSE}) {
            assertEquals(0.0, table[0][0], EPS);
            for (int i = 1; i < table.length; i++) {
                assertTrue(table[i][0] > table[i - 1][0], "keyframe " + i + " goes back in time");
            }
        }
        assertEquals(0.0, VisorMotion.OPEN[0][1], EPS);
        assertEquals(VisorMotion.OPEN_MAX, VisorMotion.CLOSE[0][1], EPS);
    }

    @Test
    void interpolationIsLinearBetweenKeyframes() {
        assertEquals((38.37 + 65.34) / 2, VisorMotion.angleAt(true, (0.2168 + 0.2834) / 2), 1e-6);
    }

    @Test
    void aStrokeStampedAheadOfTheClientClockHoldsAtItsStart() {
        assertEquals(0.0, VisorMotion.angle(true, 1000L, 999.5), EPS);
        assertEquals(VisorMotion.OPEN_MAX, VisorMotion.angle(false, 1000L, 990.0), EPS);

        assertEquals(75.85, VisorMotion.angle(true, 1000L, 1020.0), EPS);
    }

    @Test
    void theCamSlidesAQuarterUnitOverTheFirstSixDegreesSteepestAtTheStart() {
        assertEquals(0.0, VisorMotion.camPush(0.0), EPS);
        assertEquals(0.0, VisorMotion.camPush(-1.34), EPS, "a squashed gasket does not unseat the visor");
        assertEquals(0.25, VisorMotion.camPush(6.0), EPS);
        assertEquals(0.25, VisorMotion.camPush(VisorMotion.OPEN_MAX), EPS);
        final double firstDegree = VisorMotion.camPush(1.0) - VisorMotion.camPush(0.0);
        final double lastDegree = VisorMotion.camPush(6.0) - VisorMotion.camPush(5.0);
        assertTrue(firstDegree > 3 * lastDegree,
                "steep at the start, or the glass digs into the gasket before the cam catches up");
    }

    @Test
    void aFlickCannotLandBeforeTheLastStrokeHasReachedItsEnd() {
        final double openStopSeconds = 0.3126;
        final double latchSeconds = 0.25;
        assertTrue(VisorMotion.STROKE_LOCK_TICKS / 20.0 >= openStopSeconds,
                "the lock must outlast the open stroke's swing to the stop");
        assertTrue(VisorMotion.STROKE_LOCK_TICKS / 20.0 >= latchSeconds,
                "and the close stroke's swing to the latch");
    }
}
