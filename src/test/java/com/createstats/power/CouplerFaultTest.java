package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CouplerFaultTest {
    private static final int CAP = 16384;

    private static CouplerFault.Inputs ok(boolean pulling, CouplerEndpoint endpoint, int crossing, int stored) {
        return new CouplerFault.Inputs(pulling, false, false, endpoint, false, false, true, false, false, false,
                -1, false, crossing, stored, CAP);
    }

    @Test
    @DisplayName("carrying with nothing wrong is no fault")
    void carryingIsNone() {
        assertEquals(CouplerFault.NONE, CouplerFault.judge(ok(true, CouplerEndpoint.BOLTED_GENERIC, 500, 100)));
        assertEquals(CouplerFault.NONE, CouplerFault.judge(ok(false, CouplerEndpoint.WIRED_GENERIC, 500, 100)));
    }

    @Test
    @DisplayName("an idle seam is split by the store: the old goggles told an exporter to make their device feed")
    void idleIsSplitByTheStore() {
        assertEquals(CouplerFault.THEIRS_NOT_GIVING,
                CouplerFault.judge(ok(true, CouplerEndpoint.BOLTED_GENERIC, 0, 0)));
        assertEquals(CouplerFault.OURS_NOT_TAKING,
                CouplerFault.judge(ok(true, CouplerEndpoint.BOLTED_GENERIC, 0, CAP)));
        assertEquals(CouplerFault.OURS_NOT_GIVING,
                CouplerFault.judge(ok(false, CouplerEndpoint.BOLTED_GENERIC, 0, 0)));
        assertEquals(CouplerFault.THEIRS_NOT_TAKING,
                CouplerFault.judge(ok(false, CouplerEndpoint.BOLTED_GENERIC, 0, 5000)));

        for (int stored : new int[] {0, 1, CAP / 2, CAP}) {
            assertFalse(CouplerFault.judge(ok(false, CouplerEndpoint.WIRED_GENERIC, 0, stored))
                    == CouplerFault.THEIRS_NOT_GIVING);
        }
    }

    @Test
    @DisplayName("a wrong face is a bolted fault only, and outranks everything")
    void wrongFace() {
        final CouplerFault.Inputs bolted = new CouplerFault.Inputs(true, true, true, CouplerEndpoint.NONE, false,
                false, false, true, false, false, -1, false, 0, 0, CAP);
        assertEquals(CouplerFault.WRONG_FACE, CouplerFault.judge(bolted));
        final CouplerFault.Inputs wired = new CouplerFault.Inputs(true, true, false, CouplerEndpoint.WIRED_GENERIC,
                false, false, true, false, false, false, -1, false, 10, 0, CAP);
        assertEquals(CouplerFault.NONE, CouplerFault.judge(wired), "a wired seam does not use a face");
    }

    @Test
    @DisplayName("loop, ours, their connector bolted elsewhere, nothing there, nothing ours: in that order")
    void causesFirst() {
        assertEquals(CouplerFault.LOOPED, CouplerFault.judge(new CouplerFault.Inputs(true, false, true,
                CouplerEndpoint.NONE, true, true, false, true, true, false, -1, false, 0, 0, CAP)));
        assertEquals(CouplerFault.FOREIGN_REFUSED, CouplerFault.judge(new CouplerFault.Inputs(true, false, false,
                CouplerEndpoint.NONE, true, false, false, false, false, false, -1, false, 0, 0, CAP)));
        assertEquals(CouplerFault.CONNECTOR_FACES_AWAY, CouplerFault.judge(new CouplerFault.Inputs(true, false,
                false, CouplerEndpoint.NONE, false, true, true, false, false, false, -1, false, 0, 0, CAP)));
        assertEquals(CouplerFault.NO_FOREIGN, CouplerFault.judge(new CouplerFault.Inputs(true, false, false,
                CouplerEndpoint.NONE, false, false, false, false, false, false, -1, false, 0, 0, CAP)));
        assertEquals(CouplerFault.NO_GRID, CouplerFault.judge(new CouplerFault.Inputs(true, false, false,
                CouplerEndpoint.BOLTED_GENERIC, false, false, false, true, false, false, -1, false, 0, 0, CAP)));
    }

    @Test
    @DisplayName("force off reads in both directions; shed only in the load direction (KI-220)")
    void lockAndShed() {
        for (boolean pulling : new boolean[] {true, false}) {
            assertEquals(CouplerFault.FORCED_OFF, CouplerFault.judge(new CouplerFault.Inputs(pulling, false, false,
                    CouplerEndpoint.BOLTED_GENERIC, false, false, true, true, true, false, -1, false, 0, 0, CAP)));
        }
        assertEquals(CouplerFault.SHED, CouplerFault.judge(new CouplerFault.Inputs(false, false, false,
                CouplerEndpoint.BOLTED_GENERIC, false, false, true, false, true, false, -1, false, 0, 100, CAP)));
        assertEquals(CouplerFault.NONE, CouplerFault.judge(new CouplerFault.Inputs(true, false, false,
                CouplerEndpoint.BOLTED_GENERIC, false, false, true, false, true, false, -1, false, 300, 100, CAP)),
                "a shed importer is not a load and keeps carrying");
    }

    @Test
    @DisplayName("the lever's OFF is said as the player's own switch, before a board's lock, after a missing end")
    void switchedOff() {
        final CouplerFault.Inputs off = new CouplerFault.Inputs(false, false, false, CouplerEndpoint.BOLTED_GENERIC,
                false, false, true, true, true, false, -1, false, 0, 0, CAP, true);
        assertEquals(CouplerFault.SWITCHED_OFF, CouplerFault.judge(off), "OFF outranks a board's lock and its shed");
        final CouplerFault.Inputs offUnwired = new CouplerFault.Inputs(false, false, false, CouplerEndpoint.NONE,
                false, false, true, false, false, false, -1, false, 0, 0, CAP, true);
        assertEquals(CouplerFault.NO_FOREIGN, CouplerFault.judge(offUnwired), "a missing end is still the cause first");
        assertTrue(CouplerFault.SWITCHED_OFF.blocksFlow());
        assertFalse(CouplerFault.SWITCHED_OFF.isTheirSide() || CouplerFault.SWITCHED_OFF.isOurSide());
        assertFalse(CouplerFault.SWITCHED_OFF == CouplerFault.FORCED_OFF);
    }

    @Test
    @DisplayName("the New Age and Crafts and Additions quirks apply only to the shape that has them")
    void quirksAreScoped() {
        assertEquals(CouplerFault.NEEDS_THEIR_PULL, CouplerFault.judge(new CouplerFault.Inputs(false, false, false,
                CouplerEndpoint.BOLTED_NEW_AGE, false, false, true, false, false, true, 3, false, 0, 100, CAP)));
        assertEquals(CouplerFault.THEIRS_NOT_TAKING, CouplerFault.judge(new CouplerFault.Inputs(false, false,
                false, CouplerEndpoint.WIRED_NEW_AGE, false, false, true, false, false, true, 3, false, 0, 100, CAP)),
                "a wire inserts through their network whatever the connector's mode (KI-203)");

        assertEquals(CouplerFault.NO_PULL_SOURCES, CouplerFault.judge(new CouplerFault.Inputs(true, false, false,
                CouplerEndpoint.WIRED_NEW_AGE, false, false, true, false, false, false, 0, false, 0, 0, CAP)));
        assertEquals(CouplerFault.THEIRS_NOT_GIVING, CouplerFault.judge(new CouplerFault.Inputs(true, false, false,
                CouplerEndpoint.BOLTED_NEW_AGE, false, false, true, false, false, false, 0, false, 0, 0, CAP)));
        assertEquals(CouplerFault.CONNECTOR_OPPOSES, CouplerFault.judge(new CouplerFault.Inputs(true, false, false,
                CouplerEndpoint.BOLTED_CREATE_ADDITION, false, false, true, false, false, false, -1, true, 0, 0,
                CAP)));
    }

    @Test
    @DisplayName("every fault has a lang key, sides are never both, and an ordinal from another build is forgiven")
    void keysAndSides() {
        for (CouplerFault f : CouplerFault.values()) {
            assertTrue(f.langKey().startsWith("create_stats.universal_power_coupler.fault."));
            assertFalse(f.isTheirSide() && f.isOurSide(), f.name());
            assertEquals(f, CouplerFault.byOrdinal(f.ordinal()));
        }
        assertEquals(CouplerFault.NONE, CouplerFault.byOrdinal(-1));
        assertEquals(CouplerFault.NONE, CouplerFault.byOrdinal(999));
        assertFalse(CouplerFault.NONE.blocksFlow());
        assertTrue(CouplerFault.LOOPED.blocksFlow());
        assertFalse(CouplerFault.THEIRS_NOT_TAKING.blocksFlow(), "idle clears the moment either side moves");
    }
}
