package com.createstats.blockentity;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoTransmissionClutchSettingsTest {
    @Test
    void aNewClutchHoldsAPercentBand() {
        AutoTransmissionClutchSettings s = new AutoTransmissionClutchSettings();
        assertEquals(AutoTransmissionClutchSettings.LimitMode.PERCENTAGE, s.getLimitMode());
        assertEquals(50f, s.getLowerBound());
        assertEquals(80f, s.getUpperBound());
    }

    @Test
    void aSaveWithoutAModeStaysInSu() {
        CompoundTag legacy = new CompoundTag();
        legacy.putFloat("LimitValue", 256f);
        AutoTransmissionClutchSettings s = AutoTransmissionClutchSettings.load(legacy);
        assertEquals(AutoTransmissionClutchSettings.LimitMode.SU, s.getLimitMode());
        assertEquals(256f, s.getUpperBound());
    }

    @Test
    void aSavedSuBandLoadsAsSu() {
        AutoTransmissionClutchSettings su = new AutoTransmissionClutchSettings();
        su.setLimitMode(AutoTransmissionClutchSettings.LimitMode.SU);
        su.setUpperBound(4096f);
        su.setLowerBound(2048f);
        AutoTransmissionClutchSettings back = AutoTransmissionClutchSettings.load(su.save());
        assertEquals(AutoTransmissionClutchSettings.LimitMode.SU, back.getLimitMode());
        assertEquals(2048f, back.getLowerBound());
        assertEquals(4096f, back.getUpperBound());
    }

    @Test
    void thePercentBandSwitchesModeAndKeepsTheSuBand() {
        AutoTransmissionClutchSettings s = new AutoTransmissionClutchSettings();
        s.setLimitMode(AutoTransmissionClutchSettings.LimitMode.SU);
        s.setUpperBound(4096f);
        s.setPercentageBand(40f, 60f);
        assertEquals(AutoTransmissionClutchSettings.LimitMode.PERCENTAGE, s.getLimitMode());
        assertEquals(40f, s.getLowerBound());
        assertEquals(60f, s.getUpperBound());
        s.setLimitMode(AutoTransmissionClutchSettings.LimitMode.SU);
        assertEquals(4096f, s.getUpperBound());
    }

    @Test
    void aBandPastTheEdgesIsClampedInsideThem() {
        AutoTransmissionClutchSettings s = new AutoTransmissionClutchSettings();
        s.setPercentageBand(-5f, 110f);
        assertEquals(1f, s.getLowerBound());
        assertEquals(100f, s.getUpperBound());
        assertTrue(s.getLowerBound() <= s.getUpperBound());
    }
}
