package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BurnerFuelOutlookTest {
    @Test
    @DisplayName("a hopper that replaces every item the fire takes keeps the burner fed")
    void aHopperKeepsItFed() {
        final BurnerFuelOutlook f = new BurnerFuelOutlook();
        f.onFeed();
        for (int i = 0; i < 5; i++) {
            f.onTake();
            assertTrue(f.fed(), "one take is not a verdict");
            f.onFeed();
            assertTrue(f.fed());
        }
    }

    @Test
    @DisplayName("a burner loaded by hand is never fed")
    void byHandIsNotFed() {
        final BurnerFuelOutlook f = new BurnerFuelOutlook();
        f.onHand();
        f.onTake();
        f.onTake();
        assertFalse(f.fed());
    }

    @Test
    @DisplayName("a hopper that runs dry stops counting after the next take, not before")
    void aDryHopper() {
        final BurnerFuelOutlook f = new BurnerFuelOutlook();
        f.onFeed();
        f.onTake();
        f.onFeed();
        f.onTake();
        assertTrue(f.fed(), "this gap may still be filled");
        f.onTake();
        assertFalse(f.fed(), "the last gap was never filled");
    }

    @Test
    @DisplayName("a hand filling a gap the feed left says the feed is not keeping up; a hand after a refill says nothing")
    void aHandInTheGap() {
        final BurnerFuelOutlook f = new BurnerFuelOutlook();
        f.onFeed();
        f.onTake();
        f.onHand();
        assertFalse(f.fed());

        final BurnerFuelOutlook g = new BurnerFuelOutlook();
        g.onFeed();
        g.onTake();
        g.onFeed();
        g.onHand();
        assertTrue(g.fed(), "the hopper had already refilled it");
    }

    @Test
    @DisplayName("output ahead: the rate while fuel lasts or while fed, nothing after, nothing without fuel")
    void outputIn() {
        final long onHand = BurnerFuelOutlook.ticksOnHand(120, 4, 300);
        assertEquals(1_320, onHand);
        assertEquals(180, BurnerFuelOutlook.outputIn(180, onHand, false, 0));
        assertEquals(180, BurnerFuelOutlook.outputIn(180, onHand, false, 1_319));
        assertEquals(0, BurnerFuelOutlook.outputIn(180, onHand, false, 1_320));
        assertEquals(180, BurnerFuelOutlook.outputIn(180, onHand, true, 36_000), "fed: flat");
        assertEquals(0, BurnerFuelOutlook.outputIn(0, onHand, true, 0), "no fuel, no fire");

        assertTrue(BurnerFuelOutlook.outputIn(180, onHand, false, 20_000) < 180);
    }
}
