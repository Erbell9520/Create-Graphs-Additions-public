package com.createstats.vccmk4.air.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelegraphTest {
    private static void tap(Telegraph t, double axis) {
        t.update(axis);
        t.update(axis);
        t.update(0.0);
    }

    @Test
    void tapsRingEveryNotchAheadAndAstern() {
        Telegraph t = new Telegraph();
        Telegraph.Notch[] ahead = {Telegraph.Notch.SLOW, Telegraph.Notch.HALF, Telegraph.Notch.FULL, Telegraph.Notch.FULL};
        for (Telegraph.Notch n : ahead) {
            tap(t, 1.0);
            assertEquals(n.value, t.position(), 1.0e-12);
        }
        Telegraph.Notch[] back = {Telegraph.Notch.HALF, Telegraph.Notch.SLOW, Telegraph.Notch.STOP,
                Telegraph.Notch.SLOW_ASTERN, Telegraph.Notch.HALF_ASTERN, Telegraph.Notch.FULL_ASTERN,
                Telegraph.Notch.FULL_ASTERN};
        for (Telegraph.Notch n : back) {
            tap(t, -1.0);
            assertEquals(n.value, t.position(), 1.0e-12);
        }
    }

    @Test
    void theOrderStaysWhenTheKeyIsReleased() {
        Telegraph t = new Telegraph();
        tap(t, 1.0);
        tap(t, 1.0);
        for (int i = 0; i < 200; i++) t.update(0.0);
        assertEquals(Telegraph.Notch.HALF.value, t.position(), 1.0e-12);
        assertEquals("HALF", t.label());
        assertEquals(Telegraph.Notch.HALF, t.notch());
    }

    @Test
    void holdingSweepsAndPausesOnEachNotch() {
        Telegraph t = new Telegraph();
        t.update(1.0);
        assertEquals(Telegraph.Notch.SLOW.value, t.position(), 1.0e-12);
        for (int i = 0; i < Telegraph.DETENT_TICKS; i++) t.update(1.0);
        assertEquals(Telegraph.Notch.SLOW.value, t.position(), 1.0e-12);
        t.update(1.0);
        assertEquals(Telegraph.Notch.SLOW.value + Telegraph.RATE, t.position(), 1.0e-12);
        assertEquals("SLOW+", t.label());
        assertNull(t.notch(), "between notches");
        int ticks = 0;
        while (t.position() < Telegraph.Notch.HALF.value - 1.0e-9 && ticks < 100) {
            t.update(1.0);
            ticks++;
        }
        assertEquals(Telegraph.Notch.HALF.value, t.position(), 1.0e-12);
        for (int i = 0; i < Telegraph.DETENT_TICKS; i++) t.update(1.0);
        assertEquals(Telegraph.Notch.HALF.value, t.position(), 1.0e-12);
    }

    @Test
    void neverPastTheEnds() {
        Telegraph t = new Telegraph();
        for (int i = 0; i < 500; i++) t.update(1.0);
        assertEquals(Telegraph.Notch.FULL.value, t.position(), 1.0e-12);
        for (int i = 0; i < 500; i++) t.update(-1.0);
        assertEquals(Telegraph.Notch.FULL_ASTERN.value, t.position(), 1.0e-12);
        t.set(7.0);
        assertEquals(Telegraph.Notch.FULL.value, t.position(), 1.0e-12);
    }

    @Test
    void everyNotchRingsTheBellOnce() {
        Telegraph t = new Telegraph();
        int before = t.rings();
        tap(t, 1.0);
        tap(t, 1.0);
        assertEquals(before + 2, t.rings());
        t.ring(Telegraph.Notch.FULL_ASTERN);
        assertEquals(before + 3, t.rings());
        assertTrue(t.position() < 0.0);
    }
}
