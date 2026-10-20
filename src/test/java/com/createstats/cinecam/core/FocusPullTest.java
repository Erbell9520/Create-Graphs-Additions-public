package com.createstats.cinecam.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FocusPullTest {
    private static final double M11 = 1.0 / Math.tan(Math.toRadians(35.0));
    private static final double H = 1600.0;

    @Test
    void offIsTheDefaultAndBlursNothing() {
        assertEquals(FocusPull.OFF, FocusPull.LADDER[0], 0.0, "the ring starts at off");
        assertFalse(FocusPull.on(FocusPull.OFF));
        assertEquals(0.0, FocusPull.circleScale(FocusPull.OFF, 8.0 * M11, H), 0.0);
        assertEquals(0.0, FocusPull.circle(1.0 / 8.0, 0.3, FocusPull.OFF, M11, H), 0.0, "not even a close branch");
        assertTrue(FocusPull.on(FocusPull.DEFAULT_STOP));
    }

    @Test
    void theFocusPlaneIsSharpAndTheSignSaysWhichSide() {
        double f = 1.0 / 8.0;
        assertEquals(0.0, FocusPull.circle(f, 8.0, 2.8, M11, H), 1e-9);
        assertTrue(FocusPull.circle(f, 40.0, 2.8, M11, H) > 0.0, "behind the focus is positive");
        assertTrue(FocusPull.circle(f, 3.0, 2.8, M11, H) < 0.0, "in front of the focus is negative");
    }

    @Test
    void theCalibrationIsHalfAPercentOnTheHorizonBehindAnEightBlockSubject() {
        double horizon = FocusPull.circle(1.0 / 8.0, 1e9, FocusPull.DEFAULT_STOP, M11, H);
        assertEquals(0.0055 * H, horizon, 0.0003 * H, "about 9 px at 1600");
        double wide = FocusPull.circle(1.0 / 30.0, 200.0, 8.0, M11, H);
        assertTrue(wide < 1.0, "stopped down to T8, a wide view focused 30 blocks out is sharp to the hills: " + wide);
    }

    @Test
    void theDefocusIsLinearInDioptersAndTheIrisDividesIt() {
        double a = FocusPull.circle(0.1, 1.0 / 0.05, 2.8, M11, H);
        double b = FocusPull.circle(0.1, 1.0 / 0.0, 2.8, M11, H);
        assertEquals(2.0 * a, b, 1e-6, "twice the diopters, twice the circle");
        assertEquals(2.0 * FocusPull.circleScale(2.8, M11, H), FocusPull.circleScale(1.4, M11, H), 1e-9);
    }

    @Test
    void zoomingInDeepensTheBlurWithTheSquareOfTheMagnification() {
        double m11x4 = 1.0 / Math.tan(Math.toRadians(FocusPull.zoomFov(70.0, 4.0) / 2.0));
        assertEquals(4.0 * M11, m11x4, 1e-9, "x4 is four times the tangent");
        assertEquals(16.0 * FocusPull.circleScale(2.8, M11, H), FocusPull.circleScale(2.8, m11x4, H), 1e-6);
    }

    @Test
    void theCircleIsHeldToItsCap() {
        double cap = FocusPull.maxCircle(H);
        assertEquals(0.012 * H, cap, 1e-9);
        assertEquals(-cap, FocusPull.circle(1.0 / 60.0, 0.3, 1.4, 8.0 * M11, H), 1e-9);
        assertEquals(cap, FocusPull.circle(10.0, 1e6, 1.4, M11, H), 1e-9);
    }

    @Test
    void theDepthIsReadBackFromTheProjectionItWasWrittenWith() {
        double n = 0.05;
        double f = 1024.0;
        double m22 = -(f + n) / (f - n);
        double m32 = -2.0 * f * n / (f - n);
        for (double z : new double[]{0.05, 0.8, 8.0, 56.0, 900.0}) {
            double ndc = (m22 * -z + m32) / z;
            double depth = ndc * 0.5 + 0.5;
            assertEquals(z, FocusPull.viewDistance(depth, m22, m32), z * 1e-9, "z " + z);
        }
        assertEquals(f, FocusPull.viewDistance(1.0, m22, m32), 1e-6, "the cleared buffer reads as the far plane");
    }

    @Test
    void theEaseIsTheSameWhateverTheFrameRate() {
        double one = FocusPull.ease(0.1, FocusPull.TAU_FOCUS);
        double half = FocusPull.ease(0.05, FocusPull.TAU_FOCUS);
        assertEquals(one, 1.0 - (1.0 - half) * (1.0 - half), 1e-12, "two half frames cover what one frame does");
        assertEquals(0.0, FocusPull.ease(0.0, 0.25), 0.0);
        assertEquals(1.0, FocusPull.ease(0.016, 0.0), 0.0);
        double z = FocusPull.easeZoom(1.0, 4.0, 1e6, FocusPull.TAU_ZOOM);
        assertEquals(4.0, z, 1e-9);
        double mid = FocusPull.easeZoom(1.0, 4.0, FocusPull.TAU_ZOOM * Math.log(2.0), FocusPull.TAU_ZOOM);
        assertEquals(2.0, mid, 1e-9, "half the way in log space is x2 on the way to x4");
    }

    @Test
    void theRingClicksFromOffToWideOpenAFullStopAtATime() {
        assertEquals(22.0, FocusPull.stepStop(FocusPull.OFF, 1), 0.0, "the first click on is all but sharp");
        assertEquals(FocusPull.OFF, FocusPull.stepStop(22.0, -1), 0.0, "and one click back is off");
        assertEquals(FocusPull.OFF, FocusPull.stepStop(FocusPull.OFF, -3), 0.0, "off holds");
        assertEquals(2.0, FocusPull.stepStop(2.8, 1), 0.0, "opening");
        assertEquals(4.0, FocusPull.stepStop(2.8, -1), 0.0, "stopping down");
        assertEquals(1.0, FocusPull.stepStop(1.4, 5), 0.0, "wide open holds");
        assertEquals(0, FocusPull.rung(FocusPull.OFF));
        assertEquals(FocusPull.LADDER.length - 1, FocusPull.rung(1.0));
        for (int i = 2; i < FocusPull.LADDER.length; i++) {
            assertEquals(Math.sqrt(2.0), FocusPull.LADDER[i - 1] / FocusPull.LADDER[i], 0.07, "a full stop: " + i);
        }
    }

    @Test
    void anyNumberLandsOnTheRing() {
        assertEquals(2.8, FocusPull.clampStop(2.9), 0.0);
        assertEquals(FocusPull.OFF, FocusPull.clampStop(Double.NaN), 0.0, "a bad packet or save is off, never a blur");
        assertEquals(FocusPull.OFF, FocusPull.clampStop(0.0), 0.0);
        assertEquals(FocusPull.OFF, FocusPull.clampStop(-5.0), 0.0);
        assertEquals(22.0, FocusPull.clampStop(1000.0), 0.0);
        assertEquals(1.0, FocusPull.clampStop(0.2), 0.0);
    }

    @Test
    void noZoomLeavesTheFieldOfViewAlone() {
        assertEquals(70.0, FocusPull.zoomFov(70.0, 1.0), 1e-9);
        assertTrue(FocusPull.zoomFov(70.0, 8.0) > 70.0 / 8.0, "the tangent's zoom is wider than the angle's at x8");
    }
}
