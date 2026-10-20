package com.createstats.vccmk4.air.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CruiseLawTest {
    private static final double MASS = 921.0;
    private static final double DRAG = 371.0;

    private static CruiseLaw law() {
        CruiseLaw c = new CruiseLaw();
        c.reset(MASS);
        c.restore(DRAG);
        return c;
    }

    @Test
    void draggedAheadAtTenTheEnginesNeverPushAhead() {
        for (Feel f : Feel.values()) {
            double push = law().force(10.0, 0.0, MASS, 1.0, f.accel, 4000.0, 2000.0, AerialPilot.DT);
            assertTrue(push <= 0.0, f + ": pushed " + push + " along the motion");
            assertEquals(-MASS * f.accel, push, 1.0e-6, f + ": the air brakes and the engines add the Feel's braking on top");
        }
    }

    @Test
    void theOldLawPushedAheadInTheSameCase() {
        double aDes = Math.max(-Feel.SPORTY.accel, Math.min(Feel.SPORTY.accel, CruiseLaw.K_SPEED * (0.0 - 10.0)));
        double old = MASS * aDes + DRAG * 10.0;
        assertTrue(old > 2500.0, "the 1.14.76 law's push: " + old);
    }

    @Test
    void slowingToASlowerOrderLetsTheAirDoItAndNeverPushesAlong() {
        double push = law().force(10.0, 5.0, MASS, 1.0, Feel.STANDARD.accel, 4000.0, 2000.0, AerialPilot.DT);
        assertTrue(push <= 0.0, "pushed " + push);
    }

    @Test
    void holdingASpeedStillPushesAgainstTheDrag() {
        double push = law().force(3.2, 3.0, MASS, 1.0, Feel.STANDARD.accel, 4000.0, 2000.0, AerialPilot.DT);
        assertTrue(push > 0.8 * DRAG * 3.0, "holds 3 b/s: " + push);
    }

    @Test
    void backingOffAnAsternOrderIsTheSameRuleTheOtherWay() {
        double push = law().force(-8.0, 0.0, MASS, 1.0, Feel.STANDARD.accel, 4000.0, 2000.0, AerialPilot.DT);
        assertTrue(push >= 0.0, "pushed " + push + " astern while backing faster than ordered");
    }

    @Test
    void anAnchorDraggedAwayPushesBackNotAway() {
        double[] f = AnchorLaw.force(-30.0, 0.0, 10.0, 0.0, MASS, Feel.SPORTY.accel, DRAG, 1.0);
        assertTrue(f[0] < 0.0, "pushed " + f[0] + " away from the spot");

        double[] g = AnchorLaw.force(-30.0, 20.0, 10.0, 0.0, MASS, Feel.SPORTY.accel, DRAG, 1.0);
        assertTrue(g[0] <= 1.0e-9, "never along the motion: " + g[0]);
        assertTrue(g[1] > 0.0, "and toward the spot across it: " + g[1]);
    }

    @Test
    void anAnchorHeldIsUnchanged() {
        double[] f = AnchorLaw.force(2.0, 0.0, 0.1, 0.0, MASS, Feel.STANDARD.accel, DRAG, 1.0);
        assertTrue(f[0] > 0.0);
    }
}
