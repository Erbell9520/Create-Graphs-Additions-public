package com.createstats.battle.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MasterElectionTest {
    @Test
    void smallestPositionWins() {
        MasterElection e = new MasterElection();
        e.checkIn(500L, 1);
        e.checkIn(-20L, 1);
        e.checkIn(90L, 1);
        e.update(1);
        assertEquals(-20L, e.master());
        assertTrue(e.isMaster(-20L));
        assertFalse(e.isMaster(90L));
    }

    @Test
    void anExplicitLeaveHandsOverTheSameTick() {
        MasterElection e = new MasterElection();
        e.checkIn(1L, 100);
        e.checkIn(2L, 100);
        e.update(100);
        e.leave(1L);
        assertTrue(e.update(100), "handover reported");
        assertEquals(2L, e.master());
    }

    @Test
    void aMissedCheckInHandsOverAfterTwentyTicks() {
        MasterElection e = new MasterElection();
        e.checkIn(1L, 0);
        e.checkIn(2L, 0);
        e.update(0);
        for (long t = 1; t <= 20; t++) {
            e.checkIn(2L, t);
            assertFalse(e.update(t), "Bridge 1 is silent but inside the window at " + t);
            assertEquals(1L, e.master());
        }
        e.checkIn(2L, 21);
        assertTrue(e.update(21));
        assertEquals(2L, e.master());
    }

    @Test
    void eachHandoverEmitsExactlyOneSafeEvent() {
        MasterElection e = new MasterElection();
        int safe = 0;
        e.checkIn(10L, 0);
        if (e.update(0)) safe++;
        if (e.update(0)) safe++;
        assertEquals(1, safe, "the first master counts, once, however many Bridges ask");
        e.checkIn(5L, 1);
        e.checkIn(10L, 1);
        if (e.update(1)) safe++;
        if (e.update(1)) safe++;
        if (e.update(1)) safe++;
        assertEquals(2, safe, "a smaller Bridge arriving is one more handover");
        e.leave(5L);
        e.leave(10L);
        if (e.update(2)) safe++;
        assertEquals(3, safe, "the last master leaving is a change too: its fire signals must clear");
        assertFalse(e.hasMaster());
    }
}
