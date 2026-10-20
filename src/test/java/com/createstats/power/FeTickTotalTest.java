package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeTickTotalTest {
    @Test
    @DisplayName("a fresh total is zero, and does not read as current at tick zero")
    void freshIsZero() {
        final FeTickTotal total = new FeTickTotal();
        assertEquals(0, total.at(0L));
        assertEquals(0, total.at(Long.MIN_VALUE));

        assertEquals(0, total.at(100L));
    }

    @Test
    @DisplayName("what was booked for a tick is readable on that tick")
    void readableOnItsOwnTick() {
        final FeTickTotal total = new FeTickTotal();
        total.add(1_800, 40L);
        assertEquals(1_800, total.at(40L));
    }

    @Test
    @DisplayName("several reports in one tick add up, because one grid can be driven from several ports")
    void sameTickAccumulates() {
        final FeTickTotal total = new FeTickTotal();
        total.add(1_000, 40L);
        total.add(800, 40L);
        assertEquals(1_800, total.at(40L));
    }

    @Test
    @DisplayName("a new tick starts a new total rather than adding to the old one")
    void newTickResets() {
        final FeTickTotal total = new FeTickTotal();
        total.add(1_800, 40L);
        total.add(200, 41L);
        assertEquals(200, total.at(41L));
    }

    @Test
    @DisplayName("still readable one tick later, so a reader that ticks before its reporter sees no flicker")
    void survivesOneTick() {
        final FeTickTotal total = new FeTickTotal();
        total.add(1_800, 40L);
        assertEquals(1_800, total.at(41L));
    }

    @Test
    @DisplayName("gone by the tick after that, so stopped traffic falls to zero on its own")
    void agesOut() {
        final FeTickTotal total = new FeTickTotal();
        total.add(1_800, 40L);
        assertEquals(0, total.at(42L));
        assertEquals(0, total.at(4_000L));
    }

    @Test
    @DisplayName("a booking in the past is not resurrected by the clock going backwards")
    void ignoresBackwardsTime() {
        final FeTickTotal total = new FeTickTotal();
        total.add(1_800, 40L);
        assertEquals(0, total.at(39L));
    }

    @Test
    @DisplayName("nothing is booked for a zero or negative report")
    void ignoresEmptyReports() {
        final FeTickTotal total = new FeTickTotal();
        total.add(0, 40L);
        total.add(-500, 40L);
        assertEquals(0, total.at(40L));
    }
}
