package com.createstats.vccmk4.core;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeatRuleTest {
    private static final UUID CRAFT = new UUID(1L, 1L);
    private static final UUID OTHER = new UUID(2L, 2L);

    private static int[] at(int dx, int dy, int dz) {
        return new int[] {dx, dy, dz};
    }

    @Test
    void aSeatBesideTheConsoleOrOneBlockOutFlies() {
        assertTrue(SeatRule.score(CRAFT, CRAFT, at(1, 0, 0)) > 0.0, "right beside it");
        assertTrue(SeatRule.score(CRAFT, CRAFT, at(-1, -1, 1)) > 0.0, "beside it, diagonally, a step down");
        assertTrue(SeatRule.score(CRAFT, CRAFT, at(2, 0, 0)) > 0.0, "one block between");
        assertTrue(SeatRule.score(CRAFT, CRAFT, at(2, 2, -2)) > 0.0, "the far corner of the cube");
        assertTrue(SeatRule.score(CRAFT, CRAFT, at(0, -2, 0)) > 0.0, "the seat two below");
    }

    @Test
    void aSeatFurtherOnTheSameShipDoesNot() {
        assertEquals(0.0, SeatRule.score(CRAFT, CRAFT, at(3, 0, 0)), "two blocks between");
        assertEquals(0.0, SeatRule.score(CRAFT, CRAFT, at(2, 3, 0)));
        assertEquals(0.0, SeatRule.score(CRAFT, CRAFT, at(40, 0, 0)), "anywhere on board flew it until 1.15.27");
    }

    @Test
    void offTheCraftTheSameTwoBlocks() {
        assertTrue(SeatRule.score(null, CRAFT, at(1, 0, 0)) > 0.0, "a seat Sable cannot place, beside the console");
        assertTrue(SeatRule.score(null, CRAFT, at(-2, 1, 2)) > 0.0);
        assertEquals(0.0, SeatRule.score(null, CRAFT, at(3, 0, 0)));
        assertEquals(0.0, SeatRule.score(null, CRAFT, at(16, 0, 0)), "a rider passing by");
        assertTrue(SeatRule.score(null, null, at(2, 0, 0)) > 0.0, "Sable absent: the same two blocks");
        assertEquals(0.0, SeatRule.score(null, null, at(0, 0, 5)));
    }

    @Test
    void aSeatOnAnotherCraftNeverFlies() {
        assertEquals(0.0, SeatRule.score(OTHER, CRAFT, at(1, 0, 0)), "the vehicle parked alongside keeps its own driver");
    }

    @Test
    void nearerFirstAndOnTheCraftBeforeUnplaced() {
        assertTrue(SeatRule.score(CRAFT, CRAFT, at(1, 0, 0)) > SeatRule.score(CRAFT, CRAFT, at(2, 0, 0)));
        assertTrue(SeatRule.score(CRAFT, CRAFT, at(2, 2, 2)) > SeatRule.score(null, CRAFT, at(1, 0, 0)));
    }

    @Test
    void anUnreadableSeatIsNoSeat() {
        assertEquals(0.0, SeatRule.score(CRAFT, CRAFT, null));
        assertEquals(0.0, SeatRule.score(CRAFT, CRAFT, new int[0]));
    }

    @Test
    void twoPilotsAtMost() {
        assertEquals(2, SeatRule.MAX_PILOTS);
        assertEquals(2, SeatRule.RADIUS);
    }

    @Test
    void bothPilotsKeysCountAsOneHand() {
        int w = KeyIds.key(87);
        int s = KeyIds.key(83);
        int a = KeyIds.key(65);
        HeldKeys pilot = HeldKeys.of(w);
        HeldKeys co = HeldKeys.of(a);
        HeldKeys both = HeldKeys.union(pilot, co);
        assertTrue(both.has(w) && both.has(a), "one steers while the other works the throttle");
        assertEquals(HeldKeys.of(w, a), both);
        assertEquals(HeldKeys.of(w, s), HeldKeys.union(HeldKeys.of(w, s), HeldKeys.of(s)), "a key both hold is held once");
        assertEquals(pilot, HeldKeys.union(pilot, HeldKeys.NONE));
        assertEquals(co, HeldKeys.union(HeldKeys.NONE, co));
        assertEquals(HeldKeys.NONE, HeldKeys.union(null, null));
    }
}
