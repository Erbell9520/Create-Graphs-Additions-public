package com.createstats.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityLinkTest {
    private static final String PANEL = "panel";

    private static class Sweep implements java.util.function.Supplier<String> {
        final AtomicInteger calls = new AtomicInteger();
        final String result;

        Sweep(String result) {
            this.result = result;
        }

        @Override
        public String get() {
            this.calls.incrementAndGet();
            return this.result;
        }
    }

    @Test
    @DisplayName("A panel that answers is the whole of the question")
    void knownPanelWins() {
        final Sweep sweep = new Sweep(PANEL);
        final SecurityLink.Contact<String> contact =
                SecurityLink.resolve(PANEL, true, true, sweep);
        assertEquals(SecurityLink.LINKED, contact.state());
        assertSame(PANEL, contact.panel());

        assertEquals(0, sweep.calls.get());
    }

    @Test
    @DisplayName("A panel in an unloaded chunk is unknown, and does not pay for a sweep")
    void unloadedIsUnknown() {
        final Sweep sweep = new Sweep(PANEL);
        final SecurityLink.Contact<String> contact =
                SecurityLink.resolve(null, true, false, sweep);
        assertEquals(SecurityLink.UNKNOWN, contact.state());
        assertNull(contact.panel());
        assertFalse(contact.state().isLost());
        assertEquals(0, sweep.calls.get());
    }

    @Test
    @DisplayName("A panel gone from a loaded position is a loss when the circuit carries no other")
    void demolishedIsLost() {
        final Sweep sweep = new Sweep(null);
        final SecurityLink.Contact<String> contact =
                SecurityLink.resolve(null, true, true, sweep);
        assertEquals(SecurityLink.LOST, contact.state());
        assertTrue(contact.state().isLost());
        assertNull(contact.panel());
        assertEquals(1, sweep.calls.get());
    }

    @Test
    @DisplayName("A second panel on the circuit is a recovery, not a loss")
    void sweepRecovers() {
        final Sweep sweep = new Sweep("other panel");
        final SecurityLink.Contact<String> contact =
                SecurityLink.resolve(null, true, true, sweep);
        assertEquals(SecurityLink.LINKED, contact.state());
        assertEquals("other panel", contact.panel());
        assertEquals(1, sweep.calls.get());
    }

    @Test
    @DisplayName("A device that never had a panel sweeps, and an empty circuit is a loss")
    void neverHadOne() {
        final Sweep found = new Sweep(PANEL);
        assertEquals(SecurityLink.LINKED, SecurityLink.resolve(null, false, false, found).state());
        assertEquals(1, found.calls.get());

        final Sweep empty = new Sweep(null);
        assertEquals(SecurityLink.LOST, SecurityLink.resolve(null, false, false, empty).state());
        assertEquals(1, empty.calls.get());
    }

    @Test
    @DisplayName("A device with no circuit is UNKNOWN, never LOST, and is not asked to sweep")
    void offCircuitIsUnknown() {
        final Sweep sweep = new Sweep(null);
        final SecurityLink.Contact<String> fresh =
                SecurityLink.resolve(null, false, false, false, sweep);
        assertEquals(SecurityLink.UNKNOWN, fresh.state());
        assertFalse(fresh.state().isLost());
        assertNull(fresh.panel());
        assertEquals(0, sweep.calls.get());

        assertEquals(SecurityLink.UNKNOWN,
                SecurityLink.resolve(null, true, true, false, sweep).state());
        assertEquals(0, sweep.calls.get());
    }

    @Test
    @DisplayName("A panel we can still see beats having no circuit to sweep")
    void knownPanelBeatsNoCircuit() {
        final Sweep sweep = new Sweep(null);
        final SecurityLink.Contact<String> contact =
                SecurityLink.resolve(PANEL, true, true, false, sweep);
        assertEquals(SecurityLink.LINKED, contact.state());
        assertSame(PANEL, contact.panel());
        assertEquals(0, sweep.calls.get());
    }
}
