package com.createstats.vccmk4.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuxAxisTest {
    @Test
    void latchedCreepsAndStays() {
        AuxAxis a = new AuxAxis();
        AuxAxis.Setting s = AuxAxis.Setting.LATCHED;
        for (int i = 0; i < 5; i++) a.step(s, 1.0);
        assertEquals(5 * AuxAxis.SLEW_DEG, a.angle(), 1.0e-9);
        for (int i = 0; i < 5; i++) a.step(s, 0.0);
        assertEquals(5 * AuxAxis.SLEW_DEG, a.angle(), 1.0e-9, "released: stays put");
        for (int i = 0; i < 100; i++) a.step(s, 1.0);
        assertEquals(s.travelDeg(), a.angle(), 1.0e-9, "never past the travel");
        for (int i = 0; i < 100; i++) a.step(s, -1.0);
        assertEquals(-s.travelDeg(), a.angle(), 1.0e-9);
    }

    @Test
    void sprungFollowsTheKeyAndReturns() {
        AuxAxis a = new AuxAxis();
        AuxAxis.Setting s = AuxAxis.Setting.SPRUNG;
        int ticks = 0;
        while (a.step(s, 1.0) < s.travelDeg() - 1.0e-9) {
            if (++ticks > 200) throw new AssertionError("never deployed");
        }
        assertEquals((int) Math.ceil(s.travelDeg() / AuxAxis.SLEW_DEG) - 1, ticks, "slews, does not teleport");
        for (int i = 0; i < 100; i++) a.step(s, 0.0);
        assertEquals(0.0, a.angle(), 1.0e-9, "released: back to centre");
    }

    @Test
    void invertedSwapsTheKeys() {
        AuxAxis a = new AuxAxis();
        a.step(AuxAxis.Setting.LATCHED.withInverted(true), 1.0);
        assertTrue(a.angle() < 0.0);
    }

    @Test
    void travelIsClampedIntoRange() {
        assertEquals(AuxAxis.MAX_TRAVEL_DEG, AuxAxis.Setting.LATCHED.withTravel(9999).travelDeg());
        assertEquals(AuxAxis.MIN_TRAVEL_DEG, AuxAxis.Setting.LATCHED.withTravel(0).travelDeg());
        AuxAxis a = new AuxAxis();
        a.set(170.0);
        a.step(AuxAxis.Setting.LATCHED.withTravel(45), 0.0);
        assertTrue(a.angle() < 170.0, "an angle past a reduced travel is walked back");
    }
}
