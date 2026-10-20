package com.createstats.power;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WireTensionTest {
    private static final double MAX_RUN = 32.0;

    @Test
    void theOldHangIsKeptExactlyAsSlack() {
        assertEquals(1.02, WireTension.SLACK.slack(), 1e-12);
        assertEquals(2.77, WireTension.SLACK.sagOver(MAX_RUN), 0.01);
    }

    @Test
    void standardHangsInsideTheRangeRealLineIsStrungTo() {
        final double ratio = WireTension.STANDARD.sagPerBlock();
        assertTrue(ratio >= 0.02 && ratio <= 0.04, "standard sag ratio " + ratio);

        assertEquals(1.24, WireTension.STANDARD.sagOver(MAX_RUN), 0.01);
    }

    @Test
    void everyStepTightensAndTheOrderIsLoosestFirst() {
        final WireTension[] all = WireTension.values();
        for (int i = 1; i < all.length; i++) {
            assertTrue(all[i].slack() < all[i - 1].slack(), all[i] + " is not tighter than " + all[i - 1]);
            assertTrue(all[i].slack() > 1.0, all[i] + " must still be longer than its span");
        }
        assertSame(WireTension.SLACK, WireTension.loosest());
    }

    @Test
    void sagFormulaIsTheShallowCatenaryDrop() {
        for (WireTension t : WireTension.values()) {
            final double d = t.sagOver(20.0);
            final double arc = 20.0 + 8.0 * d * d / (3.0 * 20.0);
            assertEquals(20.0 * t.slack(), arc, 1e-9, t.name());
        }
        assertEquals(0.0, WireTension.sagPerBlock(1.0), 0.0);
        assertEquals(0.0, WireTension.sagPerBlock(0.9), 0.0, "a wire shorter than its span does not hang upward");
    }

    @Test
    void idsRoundTripAreUniqueAndUnknownReadsStandard() {
        final Set<Integer> ids = new HashSet<>();
        for (WireTension t : WireTension.values()) {
            assertTrue(ids.add(t.id()), "duplicate id " + t.id());
            assertTrue(t.id() >= 0 && t.id() <= Byte.MAX_VALUE, "id must survive a signed byte");
            assertSame(t, WireTension.byId(t.id()));
        }

        assertSame(WireTension.STANDARD, WireTension.byId(-1));
        assertSame(WireTension.STANDARD, WireTension.byId(99));
    }

    @Test
    void stepClampsAndCycleWraps() {
        assertSame(WireTension.SLACK, WireTension.SLACK.step(-1));
        assertSame(WireTension.STANDARD, WireTension.SLACK.step(1));
        assertSame(WireTension.DRUM, WireTension.DRUM.step(1));
        assertSame(WireTension.TAUT, WireTension.DRUM.step(-5), "step moves one level whatever the magnitude");
        assertSame(WireTension.SLACK, WireTension.DRUM.cycle());
        assertSame(WireTension.TAUT, WireTension.STANDARD.cycle());
    }
}
