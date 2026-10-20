package com.createstats.display;

public enum InstrumentEffectPreset
{
    LOW("Low"),
    BALANCED("Balanced"),
    RICH("Rich");

    private final String displayName;

    private InstrumentEffectPreset(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return this.displayName;
    }
}
