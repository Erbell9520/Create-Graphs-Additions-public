package com.createstats.driverhelmet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashLineTest {
    private static final double EPS = 1e-9;

    @Test
    void aPlainDrivingLine() {
        final DashLine d = DashLine.parse("VCC  D  12.3 b/s  drive +80%  steer -35%");
        assertNotNull(d);
        assertEquals('D', d.gear());
        assertEquals(12.3, d.speed(), EPS);
        assertEquals(0.80, d.throttle(), EPS);
        assertEquals(-0.35, d.steer(), EPS);
        assertFalse(d.boosting());
        assertEquals("", d.extras());
        assertEquals("", d.problem());
        assertEquals(12.3 * 3.6, d.kmh(), EPS);
    }

    @Test
    void everyOptionalPartAtOnce() {
        final DashLine d = DashLine.parse(
                "VCC  B  -3.0 b/s  drive +0%  steer +100%  BOOST  lift +20%  Aux A +  2 machines  | S1 is unlocked");
        assertNotNull(d);
        assertTrue(d.braking());
        assertEquals(-3.0, d.speed(), EPS);
        assertEquals(1.0, d.steer(), EPS);
        assertTrue(d.boosting());
        assertEquals("lift +20%  Aux A +  2 machines", d.extras());
        assertEquals("S1 is unlocked", d.problem());
    }

    @Test
    void heldAndReversing() {
        assertTrue(DashLine.parse("VCC  H  0.0 b/s  drive +0%  steer +0%").holding());
        final DashLine r = DashLine.parse("VCC  R  -2.5 b/s  drive -40%  steer +10%");
        assertEquals('R', r.gear());
        assertEquals(-0.40, r.throttle(), EPS);
    }

    @Test
    void onlyTheFirstBarStartsTheProblem() {
        assertEquals("a | b", DashLine.parse("VCC  N  0.0 b/s  drive +0%  steer +0%  | a | b").problem());
    }

    @Test
    void anythingElseOnTheActionBarIsNotADashboard() {
        assertNull(DashLine.parse(null));
        assertNull(DashLine.parse("You are driving. Right-click the console for the keys."));
        assertNull(DashLine.parse("VCC  D  12.3 b/s"));
        assertNull(DashLine.parse("Selected: Iron Ingot"));
        assertNull(DashLine.parse("VCC D 12.3 b/s drive +80% steer -35%"), "single spaces are not the Mk IV's");
    }
}
