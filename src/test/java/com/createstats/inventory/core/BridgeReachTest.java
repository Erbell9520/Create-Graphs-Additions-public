package com.createstats.inventory.core;

import com.createstats.antenna.AntennaTier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BridgeReachTest {
    private static final double BASE = 64.0;

    @Test
    void withoutAnAntennaTheLinkRangeIsTheReach() {
        final BridgeReach.Verdict near = BridgeReach.check(true, false, false, 34 * 34, BASE, AntennaTier.NONE);
        assertTrue(near.reaches());
        assertEquals(34.0, near.distance(), 1e-9);
        assertEquals(64.0, near.reach(), 1e-9);
        assertEquals(AntennaTier.NONE, near.needed());
        final BridgeReach.Verdict far = BridgeReach.check(true, false, false, 212 * 212, BASE, AntennaTier.NONE);
        assertFalse(far.reaches());
        assertEquals(AntennaTier.SMALL, far.needed(), "the face names the antenna that would reach");
    }

    @Test
    void theAntennaOnTheScreenExtendsIt() {
        assertTrue(BridgeReach.check(true, false, false, 212 * 212, BASE, AntennaTier.SMALL).reaches());
        assertEquals(256.0, BridgeReach.reachOf(BASE, AntennaTier.SMALL, false), 1e-9);
        assertEquals(768.0, BridgeReach.reachOf(BASE, AntennaTier.ADVANCED, false), 1e-9);
        assertEquals(2048.0, BridgeReach.reachOf(BASE, AntennaTier.TRT, false), 1e-9);
        final BridgeReach.Verdict v = BridgeReach.check(true, false, false, 900 * 900, BASE, AntennaTier.SMALL);
        assertFalse(v.reaches());
        assertEquals(AntennaTier.TRT, v.needed());
        assertNull(BridgeReach.check(true, false, false, 9000.0 * 9000.0, BASE, AntennaTier.TRT).needed(),
                "nothing reaches nine thousand blocks");
    }

    @Test
    void theNetherCompressesOnlyTheTrtReach() {
        assertEquals(256.0, BridgeReach.reachOf(BASE, AntennaTier.TRT, true), 1e-9, "2,048 over eight");
        assertEquals(256.0, BridgeReach.reachOf(BASE, AntennaTier.SMALL, true), 1e-9);
    }

    @Test
    void acrossDimensionsOnlyATrtBetweenTheOverworldAndTheNetherReaches() {
        assertTrue(BridgeReach.check(false, true, false, 0, BASE, AntennaTier.TRT).reaches());
        final BridgeReach.Verdict small = BridgeReach.check(false, true, false, 0, BASE, AntennaTier.SMALL);
        assertFalse(small.reaches());
        assertTrue(small.crossDimension());
        assertEquals(AntennaTier.TRT, small.needed());
        final BridgeReach.Verdict end = BridgeReach.check(false, false, false, 0, BASE, AntennaTier.TRT);
        assertFalse(end.reaches(), "the End is not on the antennas' map");
        assertNull(end.needed());
    }
}
