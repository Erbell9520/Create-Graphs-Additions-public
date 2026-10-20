package com.createstats.inventory.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetPointRelayTest {
    private static final SetPointRelay.Mode S = SetPointRelay.Mode.STEADY;
    private static final SetPointRelay.Mode P = SetPointRelay.Mode.PULSE;
    private static final SetPointRelay.Mode O = SetPointRelay.Mode.OFF;

    @Test
    void theComparatorReadsBetweenTheMarks() {
        assertEquals(0, SetPointRelay.comparator(0, 10, 90));
        assertEquals(0, SetPointRelay.comparator(10, 10, 90), "at LOW is 0");
        assertEquals(1, SetPointRelay.comparator(10.01, 10, 90));
        assertEquals(8, SetPointRelay.comparator(50, 10, 90));
        assertEquals(14, SetPointRelay.comparator(89.99, 10, 90));
        assertEquals(15, SetPointRelay.comparator(90, 10, 90), "at HIGH is 15");
        assertEquals(15, SetPointRelay.comparator(1e9, 10, 90));

        assertEquals(1 + (14 * (2417 - 512)) / (4096 - 512), SetPointRelay.comparator(2417, 512, 4096));
    }

    @Test
    void degenerateMarksStillGiveZeroOrFifteen() {
        assertEquals(0, SetPointRelay.comparator(5, 5, 5));
        assertEquals(15, SetPointRelay.comparator(6, 5, 5));
        assertEquals(15, SetPointRelay.comparator(10, 9, 2), "HIGH below LOW is LOW + 1");
        assertEquals(8, SetPointRelay.comparator(9.5, 9, 2));
        assertEquals(0, SetPointRelay.comparator(7, 9, 2));
    }

    @Test
    void steadyRelaysHoldAndReleasePastTheBand() {
        final SetPointRelay r = new SetPointRelay();
        assertEquals(1.6, SetPointRelay.band(10, 90), 1e-9);
        assertEquals(1.0, SetPointRelay.band(10, 20), 1e-9, "never below one");
        r.update(50, 10, 90, S, S);
        assertFalse(r.lowOutput());
        assertTrue(r.update(10, 10, 90, S, S), "at LOW the left relay comes on");
        assertTrue(r.lowOutput());
        assertFalse(r.update(11, 10, 90, S, S), "inside the band it holds");
        assertTrue(r.lowOutput());
        assertTrue(r.update(11.7, 10, 90, S, S), "past the band it releases");
        assertFalse(r.lowOutput());
        r.update(90, 10, 90, S, S);
        assertTrue(r.highOutput());
        r.update(88.5, 10, 90, S, S);
        assertTrue(r.highOutput(), "hysteresis on the way down too");
        r.update(88.3, 10, 90, S, S);
        assertFalse(r.highOutput());
    }

    @Test
    void aPulseFiresOnTheCrossingForFourTicks() {
        final SetPointRelay r = new SetPointRelay();
        r.update(50, 10, 90, P, P);
        r.update(9, 10, 90, P, P);
        assertTrue(r.lowOutput());
        for (int t = 1; t < SetPointRelay.PULSE_TICKS; t++) {
            r.tick();
            assertTrue(r.lowOutput(), "tick " + t);
        }
        r.tick();
        assertFalse(r.lowOutput(), "four ticks and off");
        r.update(5, 10, 90, P, P);
        r.tick();
        assertFalse(r.lowOutput(), "still below: no second pulse without a new crossing");
    }

    @Test
    void noPulseForAStoreAlreadyPastItsMarkWhenFirstRead() {
        final SetPointRelay r = new SetPointRelay();
        r.update(2, 10, 90, P, P);
        assertFalse(r.lowOutput());
        assertEquals(SetPointRelay.Lamp.FLASHING, r.lowLamp(), "the lamp still says so");
    }

    @Test
    void offIsOff() {
        final SetPointRelay r = new SetPointRelay();
        r.update(0, 10, 90, O, O);
        assertFalse(r.lowOutput());
        r.update(100, 10, 90, O, O);
        assertFalse(r.highOutput());
    }

    @Test
    void theLampsFollowSequenceA() {
        final SetPointRelay r = new SetPointRelay();
        r.update(50, 10, 90, S, S);
        assertEquals(SetPointRelay.Lamp.NORMAL, r.lowLamp());
        r.update(5, 10, 90, S, S);
        assertEquals(SetPointRelay.Lamp.FLASHING, r.lowLamp(), "a new alarm flashes");
        assertTrue(r.acknowledge());
        assertEquals(SetPointRelay.Lamp.STEADY, r.lowLamp(), "acknowledged, it burns steady");
        assertFalse(r.acknowledge(), "nothing left to acknowledge");
        r.update(6, 10, 90, S, S);
        assertEquals(SetPointRelay.Lamp.STEADY, r.lowLamp());
        r.update(30, 10, 90, S, S);
        assertEquals(SetPointRelay.Lamp.NORMAL, r.lowLamp(), "back inside: normal");
        r.update(95, 10, 90, S, S);
        assertEquals(SetPointRelay.Lamp.FLASHING, r.fullLamp());
    }

    @Test
    void resetReleasesEverything() {
        final SetPointRelay r = new SetPointRelay();
        r.update(0, 10, 90, S, S);
        r.reset();
        assertFalse(r.lowOutput());
        assertEquals(SetPointRelay.Lamp.NORMAL, r.lowLamp());
    }

    @Test
    void theModesCycleInTheScreensOrder() {
        assertEquals(P, S.next());
        assertEquals(O, P.next());
        assertEquals(S, O.next());
    }
}
