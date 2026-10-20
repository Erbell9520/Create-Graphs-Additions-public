package com.createstats.gui.pdu;

import com.createstats.power.SolarCurve;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PduDayOutlookTest {
    private static PduDayOutlook day(int[] rate, int critical, int shortfall) {
        return new PduDayOutlook(500, rate, rate, 0L, 6_000L, critical, shortfall, false,
                0, false, 0, 24, SolarCurve.Sky.CLEAR, -1, SolarCurve.Sky.CLEAR);
    }

    @Test
    @DisplayName("day time reads as a Minecraft clock: zero is six in the morning")
    void theClock() {
        assertEquals("06:00", PduDayOutlook.clock(0));
        assertEquals("12:00", PduDayOutlook.clock(6_000));
        assertEquals("18:00", PduDayOutlook.clock(12_000));
        assertEquals("00:00", PduDayOutlook.clock(18_000));
        assertEquals("18:30", PduDayOutlook.clock(12_500));
        assertEquals("06:00", PduDayOutlook.clock(24_000));
        assertEquals("05:00", PduDayOutlook.clock(-1_000));
    }

    @Test
    @DisplayName("the first axis mark is the next whole multiple of the interval on the clock")
    void theFirstMark() {
        assertEquals(0, day(new int[] {1}, 0, 0).ticksToNextMark(6));
        final PduDayOutlook late = new PduDayOutlook(500, new int[] {1}, null, 0L, 6_500L, 0, 0, false,
                0, false, 0, 24, SolarCurve.Sky.CLEAR, -1, SolarCurve.Sky.CLEAR);
        assertEquals(5_500, late.ticksToNextMark(6), "12:30 to 18:00");
        assertEquals(2_500, late.ticksToNextMark(3), "12:30 to 15:00");
    }

    @Test
    @DisplayName("the lowest point is where the running balance bottoms, not the deepest single dip")
    void deepestIsTheRunningBalance() {
        final PduDayOutlook o = day(new int[] {500, 100, 100, 50, 400}, 200, 1);

        assertEquals(3, o.deepestStep());
        assertEquals(150, o.deficitAt(3));
        assertEquals(0, o.deficitAt(0));
        assertEquals(-1, day(new int[] {500, 300}, 200, 0).deepestStep(), "never short, no marker");
    }

    @Test
    @DisplayName("the scale fits the tallest of supply, clear sky and the critical line, with headroom")
    void theScale() {
        assertEquals(1_100, day(new int[] {1_000, 200}, 300, 0).scaleMax());
        assertEquals(550, day(new int[] {100}, 500, 0).scaleMax());
        assertEquals(1, day(new int[] {0}, 0, 0).scaleMax(), "never a zero scale to divide by");
    }

    @Test
    @DisplayName("rain shows as the gap between clear sky and the forecast, and the wet strip follows the countdown")
    void rainAndTheCountdown() {
        final PduDayOutlook o = new PduDayOutlook(500, new int[] {100, 35, 35}, new int[] {100, 100, 100}, 0L,
                0L, 0, 0, true, 0, false, 0, 24, SolarCurve.Sky.CLEAR, 600, SolarCurve.Sky.RAIN);
        assertEquals(0, o.rainCostAt(0));
        assertEquals(65, o.rainCostAt(1));
        assertTrue(o.anyRainCost());
        assertFalse(o.wetAt(0));
        assertFalse(o.wetAt(1), "step 1 starts at 500, the rain comes at 600");
        assertTrue(o.wetAt(2));
        assertEquals(1, o.weatherChangeStep());
    }

    @Test
    @DisplayName("the summary says learning, covered or short, and names who holds the shortfall")
    void theSummary() {
        final PduDayOutlook cold = new PduDayOutlook(0, new int[0], null, 0L, 0L, 0, 0, false, 0, false, 0,
                9, SolarCurve.Sky.CLEAR, -1, SolarCurve.Sky.CLEAR);
        assertFalse(cold.hasCurve());
        assertEquals("Learning the day: 9 of 24 hours watched", cold.summary(false));
        assertEquals("Covered: the supply carries the critical load all day",
                day(new int[] {500}, 200, 0).summary(false));
        assertEquals("No critical load declared, so nothing to bank for", day(new int[] {500}, 0, 0).summary(false));
        final PduDayOutlook shortDay = new PduDayOutlook(500, new int[] {0}, null, 0L, 0L, 200, 1_250_000, true,
                800_000, true, 450_000, 24, SolarCurve.Sky.CLEAR, -1, SolarCurve.Sky.CLEAR);
        assertEquals("Short 1.3M FE before the supply is back, weather allowed for", shortDay.summary(false));
        assertEquals("own banks hold 800k; boards on its legs asked for 450k", shortDay.holdLine(false));
        final PduDayOutlook unheld = new PduDayOutlook(500, new int[] {0}, null, 0L, 0L, 200, 90_000, false,
                90_000, false, 0, 24, SolarCurve.Sky.CLEAR, -1, SolarCurve.Sky.CLEAR);
        assertEquals("no bank of its own holds 90k", unheld.holdLine(false));
        assertNull(day(new int[] {500}, 200, 0).holdLine(false), "nothing short, nothing to say about holding");
    }

    @Test
    @DisplayName("the hover names the time, the supply, what rain takes and the gap to critical")
    void theHover() {
        final PduDayOutlook o = new PduDayOutlook(500, new int[] {100, 35}, new int[] {100, 100}, 0b10L, 0L,
                50, 0, false, 0, false, 0, 24, SolarCurve.Sky.CLEAR, -1, SolarCurve.Sky.CLEAR);
        assertEquals("06:00 (now)", o.hoverLines(0).get(0));
        final java.util.List<String> second = o.hoverLines(1);
        assertEquals("06:30", second.get(0));
        assertTrue(second.contains("Not learned yet: this hour has not been watched enough"));
        assertTrue(second.contains("Clear sky 100 FE/t, rain takes 65"));
        assertTrue(second.contains("Critical 50 FE/t, short 15"));
        assertTrue(o.hoverLines(5).isEmpty());
    }
}
