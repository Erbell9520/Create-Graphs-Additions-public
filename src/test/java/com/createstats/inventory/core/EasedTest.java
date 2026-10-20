package com.createstats.inventory.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EasedTest {
    @Test
    void theFirstTargetIsTakenAsItIs() {
        final Eased e = new Eased();
        assertFalse(e.isSet());
        assertEquals(0.7f, e.at(0.7f, 100.0f, 10.0f), 1e-6);
        assertTrue(e.isSet());
    }

    @Test
    void aMoveTakesTheGivenTicksAndIsHalfWayAtHalfTime() {
        final Eased e = new Eased();
        e.at(0.0f, 0.0f, 10.0f);
        assertEquals(0.0f, e.at(1.0f, 0.0f, 10.0f), 1e-6);
        assertEquals(0.5f, e.at(1.0f, 5.0f, 10.0f), 1e-6);
        assertEquals(1.0f, e.at(1.0f, 10.0f, 10.0f), 1e-6);
        assertEquals(1.0f, e.at(1.0f, 50.0f, 10.0f), 1e-6);
    }

    @Test
    void aRetargetStartsFromWhereTheValueIs() {
        final Eased e = new Eased();
        e.at(0.0f, 0.0f, 10.0f);
        e.at(1.0f, 0.0f, 10.0f);
        final float mid = e.at(1.0f, 5.0f, 10.0f);

        assertEquals(mid, e.at(0.0f, 5.0f, 10.0f), 1e-6);
        assertEquals(0.0f, e.at(0.0f, 15.0f, 10.0f), 1e-6);
    }

    @Test
    void anOldWorldStillEasesSmoothly() {
        final double t0 = 50_000_000.0;
        final Eased e = new Eased();
        e.at(0.0f, t0, 10.0f);
        e.at(1.0f, t0, 10.0f);
        assertEquals(0.5f, e.at(1.0f, t0 + 5.0, 10.0f), 1e-6);
        final float a = e.at(1.0f, t0 + 5.25, 10.0f);
        final float b = e.at(1.0f, t0 + 5.5, 10.0f);
        assertTrue(b > a && a > 0.5f, "partial ticks still move the value");
    }

    @Test
    void valueParksWithoutMoving() {
        final Eased e = new Eased();
        e.at(0.3f, 0.0f, 6.0f);
        e.at(0.9f, 1.0f, 6.0f);
        final float parked = e.at(0.9f, 4.0f, 6.0f);
        assertEquals(parked, e.value(), 1e-6);
    }
}
