package com.createstats.seismic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeismicRigTest {
    @Test
    @DisplayName("state, most specific first: falling, no ground, no target, ready, shed, hoisting, waiting")
    void states() {
        assertEquals(SeismicRig.State.DROPPING, SeismicRig.state(true, false, true, true, true, 0));
        assertEquals(SeismicRig.State.NO_GROUND, SeismicRig.state(true, false, false, false, false, 4096));
        assertEquals(SeismicRig.State.NO_TARGET, SeismicRig.state(false, true, false, false, false, 0));
        assertEquals(SeismicRig.State.READY, SeismicRig.state(true, true, false, true, true, 0),
                "a shed rig at its tick is still ready: the pawl holds a full weight");
        assertEquals(SeismicRig.State.SHED, SeismicRig.state(true, true, false, true, false, 0));
        assertEquals(SeismicRig.State.CHARGING, SeismicRig.state(true, true, false, false, false, 12));
        assertEquals(SeismicRig.State.WAITING, SeismicRig.state(true, true, false, false, false, 0));
    }

    @Test
    @DisplayName("the lamp: amber charging, green ready, red without ground, blinking when it waits")
    void lamp() {
        assertEquals(SeismicRig.Lamp.AMBER, SeismicRig.lamp(SeismicRig.State.CHARGING));
        assertEquals(SeismicRig.Lamp.GREEN, SeismicRig.lamp(SeismicRig.State.READY));
        assertEquals(SeismicRig.Lamp.RED, SeismicRig.lamp(SeismicRig.State.NO_GROUND));
        assertEquals(SeismicRig.Lamp.OFF, SeismicRig.lamp(SeismicRig.State.NO_TARGET));
        assertEquals(SeismicRig.Lamp.AMBER_BLINK, SeismicRig.lamp(SeismicRig.State.WAITING));
        assertEquals(SeismicRig.Lamp.AMBER_SLOW, SeismicRig.lamp(SeismicRig.State.SHED));
        int lit = 0;
        for (long t = 0; t < 80; t++) {
            if (SeismicRig.lampLit(SeismicRig.Lamp.AMBER_BLINK, t)) {
                lit++;
            }
        }
        assertEquals(40, lit, "a fast blink is lit half the time");
        assertTrue(SeismicRig.lampLit(SeismicRig.Lamp.GREEN, 7));
        assertFalse(SeismicRig.lampLit(SeismicRig.Lamp.OFF, 7));
    }

    @Test
    @DisplayName("the winch takes FE only tuned, coupled, standing, unshed and below the tick")
    void winch() {
        assertTrue(SeismicRig.winchTakes(true, true, false, false, false));
        assertFalse(SeismicRig.winchTakes(false, true, false, false, false));
        assertFalse(SeismicRig.winchTakes(true, false, false, false, false));
        assertFalse(SeismicRig.winchTakes(true, true, true, false, false));
        assertFalse(SeismicRig.winchTakes(true, true, false, true, false));
        assertFalse(SeismicRig.winchTakes(true, true, false, false, true));
    }

    @Test
    @DisplayName("a board budgets the winch's rate or the room to the tick; a shed never zeroes it, switched off does")
    void rated() {
        assertEquals(4096, SeismicRig.rated(true, true, false, false, 0, 64));
        assertEquals(6, SeismicRig.rated(true, true, false, false, 62_494, 32));
        assertEquals(0, SeismicRig.rated(true, true, false, false, 62_500, 32));
        assertEquals(0, SeismicRig.rated(true, true, false, true, 0, 64), "switched off at the board");
        assertEquals(0, SeismicRig.rated(false, true, false, false, 0, 64));
        assertEquals(0, SeismicRig.rated(true, false, false, false, 0, 64));
        assertEquals(0, SeismicRig.rated(true, true, true, false, 0, 64));
    }

    @Test
    @DisplayName("a held use button pulls the lever once: only a click after a pause is a new pull")
    void lever() {
        assertTrue(SeismicRig.newPull(Long.MIN_VALUE, 100), "never clicked: a pull, not a wrapped subtraction");
        assertFalse(SeismicRig.newPull(96, 100), "four ticks on: Minecraft's repeat of a held button");
        assertFalse(SeismicRig.newPull(95, 100));
        assertTrue(SeismicRig.newPull(94, 100));
        assertTrue(SeismicRig.newPull(200, 100), "time went backwards: a pull");
        long last = Long.MIN_VALUE;
        int pulls = 0;
        for (long t = 1000; t < 1200; t += 4) {
            if (SeismicRig.newPull(last, t)) {
                pulls++;
            }
            last = t;
        }
        assertEquals(1, pulls, "ten seconds of a held button is one pull");
    }

    @Test
    @DisplayName("red is a double flash, so no colour shares another's rhythm (review P2-3)")
    void redDoubleFlash() {
        final StringBuilder seen = new StringBuilder();
        for (long t = 0; t < SeismicRig.FAULT_PERIOD; t++) {
            seen.append(SeismicRig.lampLit(SeismicRig.Lamp.RED, t) ? '#' : '.');
        }
        assertEquals("####....####..................", seen.toString());
        assertTrue(SeismicRig.lampLit(SeismicRig.Lamp.RED, SeismicRig.FAULT_PERIOD), "and it repeats");
        assertTrue(SeismicRig.lampLit(SeismicRig.Lamp.AMBER, 17), "hoisting stays steady");
    }

    @Test
    @DisplayName("a load is never a pulse: a lever left on does not drop the weight when the chunk loads (P0-1)")
    void signalAfterLoad() {
        assertFalse(SeismicRig.signalFires(true, false, false), "first tick after a load, lever on: no drop");
        assertFalse(SeismicRig.signalFires(true, true, true), "a standing signal is no pulse");
        assertTrue(SeismicRig.signalFires(true, false, true), "a rising edge on a settled rig drops it");
        assertFalse(SeismicRig.signalFires(false, true, true));

        boolean was = false;
        boolean settled = false;
        int drops = 0;
        final boolean[] lever = {true, true, true, false, true, true};
        for (final boolean on : lever) {
            if (SeismicRig.signalFires(on, was, settled)) {
                drops++;
            }
            was = on;
            settled = true;
        }
        assertEquals(1, drops);
    }

    @Test
    @DisplayName("the comparator: 0 empty, at least 1 with any charge, 14 just short, 15 ready (P3-1)")
    void comparator() {
        assertEquals(0, SeismicRig.comparator(0, 62_500));
        assertEquals(1, SeismicRig.comparator(1, 62_500), "any charge reads at least 1");
        assertEquals(8, SeismicRig.comparator(31_250, 62_500));
        assertEquals(14, SeismicRig.comparator(62_499, 62_500), "the reading reaches 14 before ready");
        assertEquals(15, SeismicRig.comparator(62_500, 62_500));
        assertEquals(0, SeismicRig.comparator(10, 0));
    }
}
