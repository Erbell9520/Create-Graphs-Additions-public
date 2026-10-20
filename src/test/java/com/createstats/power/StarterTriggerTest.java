package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StarterTriggerTest {
    private static StarterTrigger.Input auto(boolean shortNow) {
        return new StarterTrigger.Input(StarterMode.AUTO, shortNow, true, true, false, false);
    }

    private static int ticksToFire(StarterTrigger t, StarterTrigger.Input in, int limit) {
        for (int i = 1; i <= limit; i++) {
            if (t.tick(in)) {
                return i;
            }
        }
        return -1;
    }

    @Test
    @DisplayName("a shortfall held five seconds fires it, on the hundredth tick and not before")
    void firesAfterFiveSeconds() {
        final StarterTrigger t = new StarterTrigger();
        assertEquals(StarterTrigger.SUSTAIN_TICKS, ticksToFire(t, auto(true), 500));
        assertEquals(StarterTrigger.Phase.CLOSING, t.phase());
    }

    @Test
    @DisplayName("a one-tick recovery resets the count, so a flickering grid never spends a burst")
    void flickerDoesNotFire() {
        final StarterTrigger t = new StarterTrigger();
        for (int round = 0; round < 10; round++) {
            for (int i = 0; i < StarterTrigger.SUSTAIN_TICKS - 1; i++) {
                assertFalse(t.tick(auto(true)));
            }
            assertFalse(t.tick(auto(false)));
        }
        assertEquals(StarterTrigger.Phase.IDLE, t.phase());
    }

    @Test
    @DisplayName("Auto re-arms only when full: short but not full never fires, and fires the tick it fills")
    void onlyWhenFull() {
        final StarterTrigger t = new StarterTrigger();
        final StarterTrigger.Input notFull = new StarterTrigger.Input(StarterMode.AUTO, true, false, true, false, false);
        for (int i = 0; i < 300; i++) {
            assertFalse(t.tick(notFull));
        }
        assertTrue(t.tick(auto(true)), "the shortfall was held all along; filling up is the last condition");
    }

    @Test
    @DisplayName("the arm closes before any FE leaves, then the burst runs its full ten seconds even if the grid recovers")
    void committedBurst() {
        final StarterTrigger t = new StarterTrigger();
        ticksToFire(t, auto(true), 500);
        assertFalse(t.accepting(), "the charge port shuts the moment the arm is thrown");
        for (int i = 0; i < StarterTrigger.CLOSE_TICKS; i++) {
            assertEquals(StarterTrigger.Phase.CLOSING, t.phase());
            t.tick(auto(true));
        }
        assertEquals(StarterTrigger.Phase.FIRING, t.phase());
        for (int i = 0; i < CapacitorDischarge.BURST_TICKS; i++) {
            assertEquals(StarterTrigger.Phase.FIRING, t.phase(), "stopped early at burst tick " + i);
            assertEquals(i, t.phaseTick());
            t.tick(auto(false));
        }
        assertEquals(StarterTrigger.Phase.RELEASING, t.phase());
        for (int i = 0; i < StarterTrigger.RELEASE_TICKS; i++) {
            t.tick(auto(false));
        }
        assertEquals(StarterTrigger.Phase.IDLE, t.phase());
        assertTrue(t.accepting());
    }

    @Test
    @DisplayName("Hold never fires, and switching to Hold mid-burst stops it")
    void holdIsSafe() {
        final StarterTrigger t = new StarterTrigger();
        final StarterTrigger.Input hold = new StarterTrigger.Input(StarterMode.HOLD, true, true, true, true, false);
        assertEquals(-1, ticksToFire(t, hold, 1000));
        final StarterTrigger u = new StarterTrigger();
        ticksToFire(u, auto(true), 500);
        for (int i = 0; i < StarterTrigger.CLOSE_TICKS + 20; i++) {
            u.tick(auto(true));
        }
        assertEquals(StarterTrigger.Phase.FIRING, u.phase());
        u.tick(hold);
        assertEquals(StarterTrigger.Phase.RELEASING, u.phase());
    }

    @Test
    @DisplayName("an empty store ends the burst, which is how a partly charged Redstone shot finishes early")
    void emptyEnds() {
        final StarterTrigger t = new StarterTrigger();
        t.tick(new StarterTrigger.Input(StarterMode.REDSTONE, false, false, true, true, false));
        for (int i = 0; i < StarterTrigger.CLOSE_TICKS; i++) {
            t.tick(new StarterTrigger.Input(StarterMode.REDSTONE, false, false, true, true, false));
        }
        assertEquals(StarterTrigger.Phase.FIRING, t.phase());
        t.tick(new StarterTrigger.Input(StarterMode.REDSTONE, false, false, false, true, false));
        assertEquals(StarterTrigger.Phase.RELEASING, t.phase());
    }

    @Test
    @DisplayName("Redstone fires on a rising edge with any charge, once per edge, and never empty")
    void redstoneEdge() {
        final StarterTrigger t = new StarterTrigger();
        final StarterTrigger.Input low = new StarterTrigger.Input(StarterMode.REDSTONE, false, false, true, false, false);
        final StarterTrigger.Input high = new StarterTrigger.Input(StarterMode.REDSTONE, false, false, true, true, false);
        final StarterTrigger.Input highEmpty = new StarterTrigger.Input(StarterMode.REDSTONE, false, false, false, true, false);
        assertFalse(t.tick(low));
        assertFalse(t.tick(highEmpty), "nothing to fire with");
        assertFalse(t.tick(low));
        assertTrue(t.tick(high));
        final int cycle = StarterTrigger.CLOSE_TICKS + CapacitorDischarge.BURST_TICKS + StarterTrigger.RELEASE_TICKS;
        for (int i = 0; i < cycle + 50; i++) {
            assertFalse(t.tick(high), "a held signal is one edge, not a stream of shots");
        }
        assertFalse(t.tick(low));
        assertTrue(t.tick(high));
    }

    @Test
    @DisplayName("a sibling's claim freezes the count: no double fire, and an instant hand-off when it ends short")
    void siblingHandOff() {
        final StarterTrigger t = new StarterTrigger();
        for (int i = 0; i < StarterTrigger.SUSTAIN_TICKS - 1; i++) {
            t.tick(auto(true));
        }
        final StarterTrigger.Input sibling = new StarterTrigger.Input(StarterMode.AUTO, true, true, true, false, true);
        assertFalse(t.tick(sibling), "the sibling won the tie");
        final StarterTrigger.Input served = new StarterTrigger.Input(StarterMode.AUTO, false, true, true, false, true);
        for (int i = 0; i < 200; i++) {
            assertFalse(t.tick(served), "the sibling's burst covers the grid; nothing to do");
        }
        assertEquals(StarterTrigger.SUSTAIN_TICKS - 1, t.shortTicks(), "frozen, neither reset nor counted");
        assertTrue(t.tick(auto(true)), "the sibling is done and the grid is short again: fire now, not in five seconds");
    }

    @Test
    @DisplayName("a claim belongs to one capacitor per network and lapses by game time")
    void claims() {
        final StarterClaims c = new StarterClaims();
        final Object net = new Object();
        final Object a = new Object();
        final Object b = new Object();
        assertTrue(c.claim(net, a, 100, 110));
        assertFalse(c.claim(net, b, 105, 115));
        assertTrue(c.heldByOther(net, b, 105));
        assertFalse(c.heldByOther(net, a, 105));
        assertTrue(c.claim(net, a, 111, 120), "the holder renews");
        assertFalse(c.heldByOther(net, b, 121), "lapsed");
        assertTrue(c.claim(net, b, 121, 130));
        assertTrue(c.claim(null, a, 0, 1), "no network, nothing to share");
    }

    @Test
    @DisplayName("a world loaded with the lever already on is not a rising edge")
    void primedRedstoneIsNotAnEdge() {
        final StarterTrigger t = new StarterTrigger();
        t.primeRedstone(true);
        assertFalse(t.tick(new StarterTrigger.Input(StarterMode.REDSTONE, false, true, true, true, false)));
    }
}
