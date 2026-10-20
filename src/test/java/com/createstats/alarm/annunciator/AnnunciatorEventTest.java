package com.createstats.alarm.annunciator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnunciatorEventTest {
    @Test
    @DisplayName("every state pair has a defined answer, and none of them throw")
    void theTableIsTotal() {
        for (AnnunciatorState was : AnnunciatorState.values()) {
            for (AnnunciatorState now : AnnunciatorState.values()) {
                final AnnunciatorEvent.Kind kind = AnnunciatorEvent.kindFor(was, now);
                if (was == now) {
                    assertNull(kind, "a rule that did not move is not an event: " + was);
                }
            }
        }
    }

    @Test
    @DisplayName("the four transitions a player cares about each get their own entry")
    void theInterestingTransitions() {
        assertEquals(AnnunciatorEvent.Kind.TRIP,
                AnnunciatorEvent.kindFor(AnnunciatorState.NORMAL, AnnunciatorState.ALARM));
        assertEquals(AnnunciatorEvent.Kind.ACK,
                AnnunciatorEvent.kindFor(AnnunciatorState.ALARM, AnnunciatorState.ACKNOWLEDGED));
        assertEquals(AnnunciatorEvent.Kind.RINGBACK,
                AnnunciatorEvent.kindFor(AnnunciatorState.ALARM, AnnunciatorState.RINGBACK));
        assertEquals(AnnunciatorEvent.Kind.CLEAR,
                AnnunciatorEvent.kindFor(AnnunciatorState.ACKNOWLEDGED, AnnunciatorState.NORMAL));
    }

    @Test
    @DisplayName("a ringback tripping again is logged as a fresh trip, because a fault recurring is news")
    void aRecurrenceIsNotAContinuation() {
        assertEquals(AnnunciatorEvent.Kind.TRIP,
                AnnunciatorEvent.kindFor(AnnunciatorState.RINGBACK, AnnunciatorState.ALARM),
                "the same fault coming back is a new event, not a footnote on the old one");
    }

    @Test
    @DisplayName("settling into NORMAL from NORMAL is never logged")
    void quietScansProduceNothing() {
        assertNull(AnnunciatorEvent.kindFor(AnnunciatorState.NORMAL, AnnunciatorState.NORMAL));
    }

    @Test
    @DisplayName("clearing a ringback with RESET is logged, so the record of dismissing it survives")
    void dismissingARingbackIsItselfAnEvent() {
        assertEquals(AnnunciatorEvent.Kind.CLEAR,
                AnnunciatorEvent.kindFor(AnnunciatorState.RINGBACK, AnnunciatorState.NORMAL));
    }

    @Test
    @DisplayName("acquisition kinds are marked as such, because they are not part of the sequence")
    void lostAndRestoredAreNotSequenceStates() {
        assertTrue(AnnunciatorEvent.Kind.LOST.isAcquisition());
        assertTrue(AnnunciatorEvent.Kind.RESTORED.isAcquisition());
        for (AnnunciatorEvent.Kind kind : AnnunciatorEvent.Kind.values()) {
            if (kind != AnnunciatorEvent.Kind.LOST && kind != AnnunciatorEvent.Kind.RESTORED) {
                assertTrue(!kind.isAcquisition(), kind + " describes the alarm sequence, not the source");
            }
        }
    }

    @Test
    @DisplayName("an entry survives a save and load unchanged")
    void roundTrips() {
        final AnnunciatorEvent event = new AnnunciatorEvent("Bank Charge",
                AnnunciatorEvent.Kind.TRIP, AlarmSeverity.CRITICAL, "18.4%", 123456L);
        final AnnunciatorEvent back = AnnunciatorEvent.load(event.save());
        assertEquals(event, back, "the log is persisted, so a lossy round trip loses history");
    }

    @Test
    @DisplayName("a blank label becomes a placeholder rather than an empty row")
    void labelsAreNeverEmpty() {
        assertEquals("?", new AnnunciatorEvent("", AnnunciatorEvent.Kind.TRIP,
                AlarmSeverity.WARNING, "", 0L).label());
        assertEquals("?", new AnnunciatorEvent(null, AnnunciatorEvent.Kind.TRIP,
                AlarmSeverity.WARNING, "", 0L).label());
    }

    @Test
    @DisplayName("long labels are clipped on the way in, because the log rides the block's sync packet")
    void labelsAreClipped() {
        final String huge = "x".repeat(400);
        final AnnunciatorEvent event = new AnnunciatorEvent(huge, AnnunciatorEvent.Kind.TRIP,
                AlarmSeverity.WARNING, "", 0L);
        assertTrue(event.label().length() <= 24,
                "a 400 character rule name must not become 400 bytes on the wire every scan");
    }

    @Test
    @DisplayName("an unknown kind or severity loads as something drawable rather than null")
    void loadIsForgiving() {
        final net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("L", "Boiler");
        tag.putString("K", "not_a_kind");
        tag.putString("S", "not_a_severity");
        tag.putString("V", "");
        tag.putLong("T", 5L);
        final AnnunciatorEvent event = AnnunciatorEvent.load(tag);
        assertNotNull(event.kind(), "the log view switches on this and cannot take a null");
        assertNotNull(event.severity());
    }
}
