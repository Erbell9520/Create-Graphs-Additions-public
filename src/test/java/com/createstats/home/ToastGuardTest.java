package com.createstats.home;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ToastGuardTest {
    private static final long MINUTE = 1200;

    @Test
    @DisplayName("a person's bake toasts every time, however many loaves a day")
    void peopleAreHeard() {
        final ToastGuard g = new ToastGuard();
        for (int loaf = 0; loaf < 6; loaf++) {
            assertEquals(ToastGuard.Verdict.TOAST, g.ask(1L, loaf * 15 * MINUTE), "loaf " + loaf);
        }
    }

    @Test
    @DisplayName("a belt-fed washer toasts twice, then says it is going quiet, then is quiet while it keeps going")
    void beltsGoQuiet() {
        final ToastGuard g = new ToastGuard();
        assertEquals(ToastGuard.Verdict.TOAST, g.ask(7L, 0));
        assertEquals(ToastGuard.Verdict.TOAST, g.ask(7L, 2 * MINUTE));
        assertEquals(ToastGuard.Verdict.LAST, g.ask(7L, 4 * MINUTE));
        for (long t = 6 * MINUTE; t < 60 * MINUTE; t += 2 * MINUTE) {
            assertEquals(ToastGuard.Verdict.QUIET, g.ask(7L, t), "still cycling at " + t);
        }

        assertEquals(ToastGuard.Verdict.TOAST, g.ask(7L, 58 * MINUTE + ToastGuard.WINDOW_TICKS + MINUTE));
    }

    @Test
    @DisplayName("never two toasts inside a minute, and one appliance's quiet is not another's")
    void gapAndIndependence() {
        final ToastGuard g = new ToastGuard();
        assertEquals(ToastGuard.Verdict.TOAST, g.ask(1L, 0));
        assertEquals(ToastGuard.Verdict.QUIET, g.ask(1L, MINUTE / 2));
        assertEquals(ToastGuard.Verdict.TOAST, g.ask(2L, MINUTE / 2));
    }

    @Test
    @DisplayName("an appliance taken off the panel is forgotten")
    void retain() {
        final ToastGuard g = new ToastGuard();
        g.ask(1L, 0);
        g.ask(1L, 2 * MINUTE);
        g.ask(1L, 4 * MINUTE);
        g.retain(Set.of(2L));
        assertEquals(ToastGuard.Verdict.TOAST, g.ask(1L, 5 * MINUTE), "put back, it starts fresh");
    }
}
