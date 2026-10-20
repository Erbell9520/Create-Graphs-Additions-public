package com.createstats.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricTypeElectricTest {
    private static String format(MetricType type, float value) {
        return type.formatValue(value).replace(',', '.');
    }

    @Test
    void voltsTakeAPrefixAtEachThousand() {
        assertEquals("230.0 V", format(MetricType.VOLTAGE, 230.0f));
        assertEquals("11.00 kV", format(MetricType.VOLTAGE, 11_000.0f));
        assertEquals("1.20 MV", format(MetricType.VOLTAGE, 1_200_000.0f));
    }

    @Test
    void smallCurrentsReadInMilliamps() {
        assertEquals("250 mA", format(MetricType.CURRENT, 0.25f));
        assertEquals("12.3 A", format(MetricType.CURRENT, 12.3f));
    }

    @Test
    void anIdleMeterReadsAPlainZero() {
        assertEquals("0 V", format(MetricType.VOLTAGE, 0.0f));
        assertEquals("0 A", format(MetricType.CURRENT, 0.0002f));
    }

    @Test
    void theUnitsAreTheSiSymbols() {
        assertEquals("V", MetricType.VOLTAGE.getUnit());
        assertEquals("A", MetricType.CURRENT.getUnit());
    }

    @Test
    void theNewTypesComeAfterEveryOldOne() {
        assertEquals(22, MetricType.GENERIC.ordinal(), "an old constant moved");
        assertTrue(MetricType.VOLTAGE.ordinal() > MetricType.GENERIC.ordinal());
        assertTrue(MetricType.CURRENT.ordinal() > MetricType.GENERIC.ordinal());
    }
}
