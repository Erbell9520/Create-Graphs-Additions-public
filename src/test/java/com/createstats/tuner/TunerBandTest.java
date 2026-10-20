package com.createstats.tuner;

import com.createstats.repeller.FrequencyTable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TunerBandTest {
    private static final TunerBand KHZ = new TunerBand(FrequencyTable.MIN_TENTHS, FrequencyTable.MAX_TENTHS,
            10, 100, 10, "kHz");

    @Test
    void aStationSitsAtItsFrequencyAcrossTheDial() {
        assertEquals(0.0, KHZ.fraction(200), 1e-12);
        assertEquals(1.0, KHZ.fraction(650), 1e-12);
        assertEquals(0.5, KHZ.fraction(425), 1e-12);
        assertEquals(0.0, KHZ.fraction(-5), 1e-12, "clamped at the left stop");
        assertEquals(1.0, KHZ.fraction(9_999), 1e-12, "clamped at the right stop");
    }

    @Test
    void theNumberedTicksAreTheWholeTensOfKilohertzInsideTheBand() {
        assertArrayEquals(new int[] {200, 300, 400, 500, 600}, KHZ.ticks(KHZ.major()));
        assertEquals(46, KHZ.ticks(KHZ.minor()).length, "one small tick per kHz, 20 to 65 inclusive");
        assertArrayEquals(new int[] {6, 9}, new TunerBand(5, 10, 3, 3, 1, "").ticks(3), "starts at the first multiple");
        assertEquals(0, new TunerBand(1, 2, 5, 5, 1, "").ticks(5).length);
    }

    @Test
    void labelsAreWholeWhenTheyCanBe() {
        assertEquals("30", KHZ.label(300));
        assertEquals("31.5", KHZ.label(315));
        assertThrows(IllegalArgumentException.class, () -> new TunerBand(5, 5, 1, 1, 1, ""));
        assertThrows(IllegalArgumentException.class, () -> new TunerBand(0, 5, 0, 1, 1, ""));
    }
}
