package com.createstats.powertrack.core;

import com.createstats.power.CableTier;
import com.createstats.power.IndoorCable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RailPowerTest {
    @Test
    @DisplayName("at the default base a section carries 8,192 FE/t and a shoe 1,024: one Cable Tray, one Indoor Cable")
    void defaults() {
        assertEquals(8_192, RailPower.sectionCeiling(8_192));
        assertEquals(1_024, RailPower.collectorRate(8_192));
        assertEquals(2_048, RailPower.carRate(8_192, 2));
        assertEquals(2_048, RailPower.carRate(8_192, 3), "two shoes per car count");
        assertEquals(0, RailPower.carRate(8_192, 0));
    }

    @Test
    @DisplayName("a server that retunes the base retunes the rail with every other conductor")
    void followsTheBase() {
        for (int base : new int[]{1_000, 4_096, 16_384, 65_536}) {
            assertEquals(CableTier.TRAY.rate(base), RailPower.sectionCeiling(base));
            assertEquals(IndoorCable.rate(base), RailPower.collectorRate(base));
        }
    }
}
