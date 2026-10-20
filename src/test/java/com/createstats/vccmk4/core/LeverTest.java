package com.createstats.vccmk4.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LeverTest {
    @Test
    void heldTheLeverStaysWhereItIsLeft() {
        Lever l = new Lever();
        for (int i = 0; i < Lever.FULL_TICKS / 2; i++) l.step(true, 1.0);
        assertEquals(0.5, l.value(), 1.0e-9);
        for (int i = 0; i < 40; i++) l.step(true, 0.0);
        assertEquals(0.5, l.value(), 1.0e-9, "no key: the fans keep lifting");
        for (int i = 0; i < 40; i++) l.step(true, 1.0);
        assertEquals(1.0, l.value(), 1.0e-9, "and it tops out at full");
    }

    @Test
    void comingDownStopsAtZeroUntilTheKeyIsPressedAgain() {
        Lever l = new Lever();
        for (int i = 0; i < 4; i++) l.step(true, 1.0);
        for (int i = 0; i < 40; i++) l.step(true, -1.0);
        assertEquals(0.0, l.value(), 1.0e-9, "held down, it lands on off");
        l.step(true, 0.0);
        l.step(true, -1.0);
        assertEquals(-1.0 / Lever.FULL_TICKS, l.value(), 1.0e-9, "a fresh press pushes down");
    }

    @Test
    void notHeldItIsJustTheKeys() {
        Lever l = new Lever();
        assertEquals(1.0, l.step(false, 1.0), 1.0e-9);
        assertEquals(0.0, l.step(false, 0.0), 1.0e-9);
        l.step(true, 1.0);
        l.reset();
        assertEquals(0.0, l.value(), 1.0e-9);
    }
}
