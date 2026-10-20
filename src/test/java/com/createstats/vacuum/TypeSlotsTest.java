package com.createstats.vacuum;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypeSlotsTest {
    @Test
    void emptyItTakesAnything() {
        final TypeSlots<String> s = new TypeSlots<>();
        assertTrue(s.accepts("bone", 1, 64));
        assertTrue(s.accepts("wool", 1, 64));
    }

    @Test
    void threeKindsFillItAndAFourthIsRefused() {
        final TypeSlots<String> s = new TypeSlots<>();
        assertTrue(s.reserve("bone", 3, 64));
        assertTrue(s.commit("bone", 3));
        assertTrue(s.reserve("arrow", 1, 64));
        assertTrue(s.commit("arrow", 1));
        assertTrue(s.reserve("string", 2, 64));
        assertTrue(s.commit("string", 2));
        assertEquals(3, s.kindsHeld());
        assertFalse(s.accepts("rotten_flesh", 1, 64), "a fourth kind must be refused");
        assertTrue(s.accepts("bone", 1, 64), "more of a held kind is still welcome");
    }

    @Test
    void aReservedKindCountsAgainstTheThree() {
        final TypeSlots<String> s = new TypeSlots<>();
        assertTrue(s.reserve("a", 1, 64));
        assertTrue(s.reserve("b", 1, 64));
        assertTrue(s.reserve("c", 1, 64));
        assertFalse(s.reserve("d", 1, 64), "three kinds still in flight fill all three slots");
    }

    @Test
    void aFullSlotRefusesMoreOfItsKind() {
        final TypeSlots<String> s = new TypeSlots<>();
        assertTrue(s.reserve("pearl", 16, 16));
        assertTrue(s.commit("pearl", 16));
        assertFalse(s.accepts("pearl", 1, 16));
        assertFalse(s.reserve("pearl", 1, 16));
    }

    @Test
    void reservationsCountAgainstRoomSoTwoItemsCannotBeAimedAtOneGap() {
        final TypeSlots<String> s = new TypeSlots<>();
        assertTrue(s.reserve("egg", 15, 16));
        assertTrue(s.commit("egg", 15));
        assertTrue(s.reserve("egg", 1, 16));
        assertFalse(s.reserve("egg", 1, 16), "the last gap is already spoken for");
    }

    @Test
    void anItemThatNeverArrivesGivesItsRoomBack() {
        final TypeSlots<String> s = new TypeSlots<>();
        assertTrue(s.reserve("a", 1, 64));
        assertTrue(s.reserve("b", 1, 64));
        assertTrue(s.reserve("c", 1, 64));
        final int slot = s.slotOf("c");
        s.release("c", 1);
        assertEquals(-1, s.slotOf("c"), "released and never landed: forgotten");
        assertNull(s.kind(slot));
        assertTrue(s.reserve("d", 1, 64), "the freed slot takes a new kind");
    }

    @Test
    void anEmptiedSlotForgetsItsKind() {
        final TypeSlots<String> s = new TypeSlots<>();
        s.reserve("a", 4, 64);
        s.commit("a", 4);
        s.reserve("b", 1, 64);
        s.commit("b", 1);
        s.reserve("c", 1, 64);
        s.commit("c", 1);
        final int slot = s.slotOf("a");
        assertEquals(4, s.extract(slot, 64));
        assertEquals(-1, s.slotOf("a"));
        assertTrue(s.accepts("z", 1, 64), "a funnel emptied a slot, so a new kind fits");
    }

    @Test
    void landingWithoutAReservationIsRefused() {
        final TypeSlots<String> s = new TypeSlots<>();
        assertFalse(s.commit("ghost", 1));
        s.reserve("a", 1, 64);
        assertFalse(s.commit("a", 2), "cannot land more than was reserved");
    }

    @Test
    void loadRestoresStockAndDropsReservations() {
        final TypeSlots<String> s = new TypeSlots<>();
        s.load(0, "bone", 10);
        s.load(1, null, 0);
        s.load(2, "wool", 3);
        assertEquals(2, s.kindsHeld());
        assertEquals(13, s.totalStored());
        assertEquals(0, s.reserved(0));
    }
}
