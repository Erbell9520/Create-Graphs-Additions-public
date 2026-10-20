package com.createstats.block;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LockerMotionTest {
    private static final float EPS = 1.0e-4f;

    @Test
    @DisplayName("Unlocking starts fully locked and ends fully open")
    void unlockIsTotal() {
        assertEquals(0.0f, LockerMotion.door(0, false), EPS);
        assertEquals(0.0f, LockerMotion.lead(0, false), EPS);
        for (int i = 0; i < LockerMotion.BOLTS; i++) {
            assertEquals(LockerMotion.BOLT_THROW, LockerMotion.bolt(i, 0, false), EPS,
                    "bolt " + i + " does not start thrown");
        }

        final int end = LockerMotion.UNLOCK_TICKS;
        assertEquals(LockerMotion.DOOR_OPEN_DEG, LockerMotion.door(end, false), EPS);
        assertEquals(LockerMotion.LEAF_LEAD, LockerMotion.lead(end, false), EPS);
        for (int i = 0; i < LockerMotion.BOLTS; i++) {
            assertEquals(0.0f, LockerMotion.bolt(i, end, false), EPS,
                    "bolt " + i + " does not fully withdraw");
        }
    }

    @Test
    @DisplayName("Locking is the same sequence backwards, and ends with every bolt home")
    void lockIsTotal() {
        assertEquals(LockerMotion.DOOR_OPEN_DEG, LockerMotion.door(0, true), EPS);
        for (int i = 0; i < LockerMotion.BOLTS; i++) {
            assertEquals(0.0f, LockerMotion.bolt(i, 0, true), EPS);
        }
        final int end = LockerMotion.UNLOCK_TICKS;
        assertEquals(0.0f, LockerMotion.door(end, true), EPS);
        assertEquals(0.0f, LockerMotion.lead(end, true), EPS);
        for (int i = 0; i < LockerMotion.BOLTS; i++) {
            assertEquals(LockerMotion.BOLT_THROW, LockerMotion.bolt(i, end, true), EPS,
                    "bolt " + i + " does not seat");
        }
    }

    @Test
    @DisplayName("Nothing ever leaves its own range, in either direction")
    void neverOvershoots() {
        for (boolean locking : new boolean[]{false, true}) {
            for (float tick = 0.0f; tick <= LockerMotion.UNLOCK_TICKS; tick += 0.25f) {
                final float door = LockerMotion.door(tick, locking);
                final float lead = LockerMotion.lead(tick, locking);
                assertTrue(door >= -EPS && door <= LockerMotion.DOOR_OPEN_DEG + EPS,
                        "leaf angle " + door + " at tick " + tick);
                assertTrue(lead >= -EPS && lead <= LockerMotion.LEAF_LEAD + EPS,
                        "lead " + lead + " at tick " + tick);
                for (int i = 0; i < LockerMotion.BOLTS; i++) {
                    final float bolt = LockerMotion.bolt(i, tick, locking);
                    assertTrue(bolt >= -EPS && bolt <= LockerMotion.BOLT_THROW + EPS,
                            "bolt " + i + " at " + bolt + " on tick " + tick);
                }
            }
        }
    }

    @Test
    @DisplayName("The leaf never moves while a bolt is still engaged")
    void boltsClearBeforeTheLeafMoves() {
        for (boolean locking : new boolean[]{false, true}) {
            for (float tick = 0.0f; tick <= LockerMotion.UNLOCK_TICKS; tick += 0.25f) {
                final boolean moving = LockerMotion.lead(tick, locking) > EPS
                        || LockerMotion.door(tick, locking) > EPS;
                if (!moving) {
                    continue;
                }
                for (int i = 0; i < LockerMotion.BOLTS; i++) {
                    assertTrue(LockerMotion.bolt(i, tick, locking) <= EPS,
                            "bolt " + i + " is engaged at " + LockerMotion.bolt(i, tick, locking)
                                    + " while the leaf moves, tick " + tick
                                    + (locking ? " (locking)" : " (unlocking)"));
                }
            }
        }
    }

    @Test
    @DisplayName("The leaf plugs out before it turns")
    void plugBeforeTurn() {
        for (float tick = 0.0f; tick <= LockerMotion.UNLOCK_TICKS; tick += 0.25f) {
            if (LockerMotion.door(tick, false) > EPS) {
                assertEquals(LockerMotion.LEAF_LEAD, LockerMotion.lead(tick, false), 1.0e-2f,
                        "the leaf turned before it was clear of its pocket, tick " + tick);
            }
        }
    }

    @Test
    @DisplayName("The dial finishes before the first bolt starts")
    void dialLeadsTheBolts() {
        for (float tick = 0.0f; tick < LockerMotion.DIAL_TICKS; tick += 0.25f) {
            for (int i = 0; i < LockerMotion.BOLTS; i++) {
                assertEquals(LockerMotion.BOLT_THROW, LockerMotion.bolt(i, tick, false), EPS,
                        "bolt " + i + " moved while the dial was still turning, tick " + tick);
            }
        }
        assertEquals(LockerMotion.DIAL_TURN_DEG,
                LockerMotion.dial(LockerMotion.DIAL_TICKS, false), EPS);
    }
}
