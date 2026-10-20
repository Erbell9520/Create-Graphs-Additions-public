package com.createstats.vccmk4.air.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeadingLawTest {
    private static final double DT = 0.05;
    private static final double INERTIA = 60000.0;
    private static final double DAMPING = 2000.0;
    private static final double MAX_MOMENT = 20000.0;

    private double heading;
    private double rate;

    private void step(HeadingLaw law, double steer, double disturbance) {
        double m = Math.max(-MAX_MOMENT, Math.min(MAX_MOMENT, law.moment(steer, heading, rate, INERTIA, 8.0, DT)));
        double was = rate;
        rate += (m + disturbance - DAMPING * rate) / INERTIA * (180.0 / Math.PI) * DT;
        heading = HeadingLaw.normalise(heading + rate * DT);
        law.observe(was, (rate - was) / DT, m, INERTIA);
    }

    @Test
    void turnsAtTheStickRateAndHoldsWhereItSettles() {
        HeadingLaw law = new HeadingLaw();
        for (int i = 0; i < 60; i++) step(law, 0.0, 0.0);
        for (int i = 0; i < 100; i++) step(law, 1.0, 0.0);
        assertEquals(8.0, rate, 1.0, "turn rate on full stick");
        double released = heading;
        int t = 0;
        while (Double.isNaN(law.hold()) && t++ < 200) step(law, 0.0, 0.0);
        double hold = law.hold();
        assertTrue(Double.isFinite(hold), "a heading was taken");
        assertTrue(Math.abs(HeadingLaw.wrap(hold - released)) < 15.0, "taken near where it was let go: " + hold + " vs " + released);
        double worst = 0.0;
        for (int i = 0; i < 400; i++) {
            step(law, 0.0, 0.0);
            worst = Math.max(worst, Math.abs(HeadingLaw.wrap(heading - hold)));
        }
        assertTrue(worst < 3.0, "swung " + worst + " degrees off the held heading");
        assertEquals(0.0, HeadingLaw.wrap(heading - hold), 1.0);
    }

    @Test
    void learnsHowHardTheAirResistsTurning() {
        HeadingLaw law = new HeadingLaw();
        for (int i = 0; i < 200; i++) step(law, 1.0, 0.0);
        assertEquals(DAMPING, law.damping(), DAMPING * 0.05);
    }

    @Test
    void aSteadyPullIsTakenUp() {
        HeadingLaw law = new HeadingLaw();
        law.holdHeading(90.0);
        heading = 90.0;
        for (int i = 0; i < 1200; i++) step(law, 0.0, 3000.0);
        assertEquals(90.0, heading, 1.0);
    }

    @Test
    void bearingIsCompassDegrees() {
        assertEquals(0.0, HeadingLaw.bearing(0.0, -1.0), 1.0e-9);
        assertEquals(90.0, HeadingLaw.bearing(1.0, 0.0), 1.0e-9);
        assertEquals(180.0, HeadingLaw.bearing(0.0, 1.0), 1.0e-9);
        assertEquals(270.0, HeadingLaw.bearing(-1.0, 0.0), 1.0e-9);
    }

    @Test
    void wrapAndNormalise() {
        assertEquals(-170.0, HeadingLaw.wrap(190.0), 1.0e-9);
        assertEquals(180.0, HeadingLaw.wrap(-180.0), 1.0e-9);
        assertEquals(350.0, HeadingLaw.normalise(-10.0), 1.0e-9);
        assertEquals(10.0, HeadingLaw.normalise(370.0), 1.0e-9);
    }

    @Test
    void aRudderAloneHoldsTheHeadingWithoutHunting() {
        HeadingLaw law = new HeadingLaw();
        double fullRudder = 15000.0;
        heading = 0.0;
        rate = 0.0;
        law.holdHeading(20.0);
        int flips = 0;
        double last = 0.0;
        double worstLate = 0.0;
        for (int i = 0; i < 1200; i++) {
            law.moment(0.0, heading, rate, INERTIA, 8.0, DT);
            double rudder = law.rudder(rate, 8.0);
            assertTrue(Math.abs(rudder) <= 1.0, "never past full throw");
            if (i > 600) {
                if (Math.signum(rudder) != Math.signum(last) && Math.abs(rudder) > 0.05) flips++;
                worstLate = Math.max(worstLate, Math.abs(rudder));
            }
            last = rudder;
            rate += (fullRudder * rudder - DAMPING * rate) / INERTIA * (180.0 / Math.PI) * DT;
            heading = HeadingLaw.normalise(heading + rate * DT);
        }
        assertEquals(20.0, heading, 1.0, "settled on the heading held");
        assertTrue(flips <= 1, "the rudder hunted: " + flips + " swings across in the last half minute");
        assertTrue(worstLate < 0.2, "the rudder still working hard once settled: " + worstLate);
        assertEquals(0.0, new HeadingLaw().rudder(Double.NaN, 8.0), 1.0e-12);
        assertEquals(0.0, new HeadingLaw().rudder(0.0, 0.0), 1.0e-12);
    }
}
