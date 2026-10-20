package com.createstats.gui.meter.plate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MeterTextTest {
    @Test
    void groupedFigures() {
        assertEquals("9,040", MeterText.group(9040));
        assertEquals("131,072", MeterText.group(131072));
        assertEquals("0", MeterText.group(0));
    }

    @Test
    void compactStaysWholeBelowTenThousand() {
        assertEquals("9,999", MeterText.compact(9999));
        assertEquals("640", MeterText.compact(640));
    }

    @Test
    void compactKeepsThreeSignificantFigures() {
        assertEquals("16.4k", MeterText.compact(16384));
        assertEquals("20.0k", MeterText.compact(20000));
        assertEquals("325k", MeterText.compact(325040));
        assertEquals("1.30M", MeterText.compact(1302448));
        assertEquals("812M", MeterText.compact(812_400_000));
        assertEquals("4.20G", MeterText.compact(4_200_000_000L));
    }

    @Test
    void ages() {
        assertEquals("0s", MeterText.duration(0));
        assertEquals("14s", MeterText.duration(280));
        assertEquals("1m", MeterText.duration(1200));
        assertEquals("2m 14s", MeterText.duration(2680));
    }
}
