package com.createstats.inventory.core;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChartHistoryTest {
    private static long dayTime(int day, int hh, int mm) {
        return day * 24_000L + Math.floorMod(hh - 6, 24) * 1000L + mm * 1000L / 60L;
    }

    @Test
    void samplesAlignToAbsoluteGameTime() {
        assertTrue(ChartHistory.isSampleTick(0));
        assertTrue(ChartHistory.isSampleTick(4000));
        assertFalse(ChartHistory.isSampleTick(4001));
        assertEquals(20L, ChartHistory.periodOf(4000));
        assertEquals(19L, ChartHistory.periodOf(3999));
        assertEquals(120, ChartHistory.STEPS_A_DAY, "one Minecraft day of game time is 120 samples");
    }

    @Test
    void theClockComesFromTheStamp() {
        final int stamp = ChartHistory.stampOf(dayTime(41, 15, 36));
        assertEquals(41, ChartHistory.day(stamp));
        assertEquals(15, ChartHistory.hour(stamp));
        assertEquals(36, ChartHistory.minute(stamp));
        final int midnight = ChartHistory.stampOf(dayTime(3, 0, 0));
        assertEquals(0, ChartHistory.hour(midnight));
        assertTrue(ChartHistory.night(midnight));
        assertFalse(ChartHistory.night(ChartHistory.stampOf(dayTime(3, 12, 0))));
    }

    @Test
    void theRingKeepsThreeDaysNewestLast() {
        final ChartHistory h = new ChartHistory();
        for (int p = 0; p < 400; p++) {
            h.record(p, p, p * 10);
        }
        assertEquals(ChartHistory.CAPACITY, h.size());
        assertEquals(399 * 10, h.value(h.size() - 1));
        assertEquals(40 * 10, h.value(0), "the oldest forty fell off");
        assertEquals(399L, h.lastPeriod());
    }

    @Test
    void missedPeriodsBecomeGapsWithEstimatedStamps() {
        final ChartHistory h = new ChartHistory();
        h.record(10, 500, 7);
        h.record(14, 504, 9);
        assertEquals(5, h.size());
        for (int k = 1; k <= 3; k++) {
            assertTrue(h.gap(k), "period " + (10 + k) + " was unpowered");
            assertEquals(500 + k, h.stamp(k), "stamp counted back from the next sample");
        }
        assertFalse(h.gap(4));
        assertEquals(9, h.value(4));
    }

    @Test
    void aVeryLongGapKeepsLessThanAPaper() {
        final ChartHistory h = new ChartHistory();
        h.record(0, 0, 1);
        h.record(10_000, 10_000, 2);
        assertEquals(ChartHistory.CAPACITY, h.size());
        assertEquals(2, h.value(h.size() - 1));
        assertTrue(h.gap(0));
    }

    @Test
    void aSleepIsAJumpAndSoIsAClockSetBackwards() {
        final int dusk = ChartHistory.stampOf(dayTime(39, 19, 36));
        final int dawn = ChartHistory.stampOf(dayTime(40, 6, 0));
        assertTrue(ChartHistory.jump(dusk, dawn), "the night slept through");
        assertTrue(ChartHistory.jump(dawn, dawn - 30), "a time command back");
        assertFalse(ChartHistory.jump(dawn, dawn), "the daylight cycle off: no movement is normal");
        assertFalse(ChartHistory.jump(dawn, dawn + 1));
        final ChartHistory h = new ChartHistory();
        h.record(100, dusk, 5);
        h.record(101, dawn, 6);
        assertTrue(h.reading(1).clockJumped());
        assertEquals(40, h.reading(1).day());
        assertEquals(6, h.reading(1).hour());
    }

    @Test
    void theReadingsNetIsTheMinuteBeforeAndAbsentAcrossAGap() {
        final ChartHistory h = new ChartHistory();
        for (int p = 0; p < 10; p++) {
            h.record(p, 600 + p, 1000 + p * 13);
        }
        final ChartHistory.Reading r = h.reading(9);
        assertTrue(r.hasNet());
        assertEquals(6 * 13, r.netPerMinute(), "six samples, 1,200 ticks, one real minute");
        assertFalse(h.reading(5).hasNet(), "not six samples behind it yet");
        final ChartHistory g = new ChartHistory();
        g.record(0, 0, 5);
        g.record(3, 3, 5);
        for (int p = 4; p < 12; p++) {
            g.record(p, p, 5 + p);
        }
        assertFalse(g.reading(7).hasNet(), "a lifted pen inside the minute");
        assertTrue(g.reading(1).gap());
        assertTrue(g.reading(10).hasNet());
    }

    @Test
    void aRepeatedPeriodIsIgnored() {
        final ChartHistory h = new ChartHistory();
        h.record(5, 5, 1);
        h.record(5, 5, 2);
        h.record(4, 4, 3);
        assertEquals(1, h.size());
        assertEquals(1, h.value(0));
    }

    @Test
    void anNbtRoundTripKeepsEverySample() {
        final ChartHistory h = new ChartHistory();
        h.record(0, 100, 3);
        h.record(4, 104, 8);
        h.record(5, 900, 9);
        final CompoundTag tag = new CompoundTag();
        tag.putLong("Last", h.lastPeriod());
        tag.putIntArray("V", h.exportValues());
        tag.putIntArray("S", h.exportStamps());
        tag.putByteArray("F", h.exportFlags());
        final ChartHistory back = new ChartHistory();
        back.restore(tag.getLong("Last"), tag.getIntArray("V"), tag.getIntArray("S"), tag.getByteArray("F"));
        assertEquals(h.size(), back.size());
        assertArrayEquals(h.exportValues(), back.exportValues());
        assertArrayEquals(h.exportStamps(), back.exportStamps());
        assertArrayEquals(h.exportFlags(), back.exportFlags());
        assertEquals(5L, back.lastPeriod());
        back.record(6, 901, 10);
        assertEquals(7, back.size(), "it carries on recording where it left off");
    }
}
