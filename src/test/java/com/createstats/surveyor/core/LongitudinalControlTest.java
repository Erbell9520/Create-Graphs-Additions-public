package com.createstats.surveyor.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LongitudinalControlTest {
    static final PlantModel CARRIER = new PlantModel(12.03, 0.665, 0.127, 9809, 235, 0.0);

    @Test
    void theInversionMakesTheAccelerationAskedFor() {
        for (double v : new double[]{0.5, 2.0, 6.0, 11.0}) {
            for (double a : new double[]{-8.0, -3.0, -1.6, -0.5, 0.0, 0.8, 1.4}) {
                for (double grade : new double[]{-2.0, 0.0, 1.5}) {
                    LongitudinalControl.Out o = LongitudinalControl.invert(v, a, CARRIER, grade, a, v);
                    double made = CARRIER.accel(o.throttle(), o.brake(), v, grade);
                    boolean saturated = o.throttle() >= 1.0 || o.brake() >= 1.0;
                    if (!saturated) assertEquals(a, made, 1.0e-9, "v " + v + " a " + a + " grade " + grade + " " + o);
                    assertTrue(o.throttle() == 0.0 || o.brake() == 0.0, "never throttle and brake at once: " + o);
                }
            }
        }
    }

    @Test
    void driveCoastAndBrakeAreOneContinuousLine() {
        double v = 6.0;
        double coast = -v / CARRIER.tau();
        double last = Double.NaN;
        for (double a = coast + 0.5; a >= coast - 0.5; a -= 0.01) {
            LongitudinalControl.Out o = LongitudinalControl.invert(v, a, CARRIER, 0.0, a, v);
            double made = CARRIER.accel(o.throttle(), o.brake(), v, 0.0);
            if (!Double.isNaN(last)) assertTrue(Math.abs(made - last) < 0.02, "no step at coast: " + made + " after " + last);
            last = made;
        }
    }

    @Test
    void theReferenceKeepsItsLimitsAndLandsOnTheGoal() {
        LongitudinalControl foot = new LongitudinalControl();
        double v = 0.0;
        double lastA = 0.0;
        double worstJerk = 0.0;
        double worstA = 0.0;
        double peak = 0.0;
        Comfort c = Comfort.NORMAL;
        for (int t = 0; t < 20 * 20; t++) {
            LongitudinalControl.Out o = foot.drive(v, 6.0, c.accel, c.decel, c.jerk, CARRIER, 0.0);
            worstJerk = Math.max(worstJerk, Math.abs(o.aRef() - lastA) / 0.05);
            worstA = Math.max(worstA, o.aRef());
            lastA = o.aRef();
            v = o.vRef();
            peak = Math.max(peak, v);
        }
        assertTrue(worstJerk <= c.jerk + 1.0e-9, "jerk limited: " + worstJerk);
        assertTrue(worstA <= c.accel + 1.0e-9, "acceleration limited: " + worstA);
        assertTrue(peak <= 6.0 * 1.01, "no overshoot: " + peak);
        assertEquals(6.0, v, 0.02, "reached the goal");
    }

    @Test
    void aStopEndsAtItsDistance() {
        Comfort c = Comfort.NORMAL;
        for (double d : new double[]{1.0, 5.0, 20.0, 60.0}) {
            double v = LongitudinalControl.stopSpeed(d, c.decel, c.jerk);
            assertEquals(d, v * v / (2.0 * c.decel) + v * c.decel / (2.0 * c.jerk), 1.0e-9, "d " + d);
        }
        assertEquals(0.0, LongitudinalControl.stopSpeed(0.0, c.decel, c.jerk));
        assertEquals(0.0, LongitudinalControl.stopSpeed(-1.0, c.decel, c.jerk));
    }

    @Test
    void thePrecisionStopAsksForTheDecelerationTheDistanceLeaves() {
        LongitudinalControl foot = new LongitudinalControl();
        LongitudinalControl.Out o = foot.stopWithin(1.25, 0.65, 10.0, CARRIER, 0.0);
        assertEquals(-1.25 * 1.25 / (2 * 0.65), o.aCmd(), 1.0e-9);
        assertEquals(LongitudinalControl.Mode.HOLD, foot.stopWithin(0.01, 0.4, 10.0, CARRIER, 0.0).mode(), "still: hold");
        assertEquals(LongitudinalControl.Mode.HOLD, foot.stopWithin(1.0, 0.0, 10.0, CARRIER, 0.0).mode(), "there: hold");
        assertTrue(foot.stopWithin(8.0, 1.0, 2.0, CARRIER, 0.0).aCmd() >= -2.0, "capped at the most it may ask");
    }
}
