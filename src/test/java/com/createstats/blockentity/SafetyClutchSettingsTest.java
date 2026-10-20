package com.createstats.blockentity;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SafetyClutchSettingsTest {
    private static SafetyClutchSettings loaded(float threshold, float hysteresis) {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("Threshold", threshold);
        tag.putFloat("Hysteresis", hysteresis);
        tag.putString("Mode", "AUTO_RESET");
        tag.putBoolean("Enabled", true);
        return SafetyClutchSettings.load(tag);
    }

    @Test
    void aZeroThresholdIsClampedRatherThanTrippingForever() {
        assertEquals(5f, loaded(0f, 5f).getThreshold());
    }

    @Test
    void outOfRangeValuesAreClamped() {
        SafetyClutchSettings s = loaded(900f, -3f);
        assertEquals(100f, s.getThreshold());
        assertEquals(0f, s.getHysteresis());
    }

    @Test
    void nonFiniteValuesKeepTheDefaults() {
        SafetyClutchSettings s = loaded(Float.NaN, Float.POSITIVE_INFINITY);
        assertEquals(80f, s.getThreshold());
        assertEquals(5f, s.getHysteresis());
    }

    @Test
    void rearmPointIsTheThresholdLessTheGap() {
        assertEquals(70f, loaded(80f, 10f).getRearmBelow());
    }
}
