package com.createstats.display;

public enum InstrumentDensityPreset
{
    MINIMAL("Minimal"),
    MEDIUM("Medium"),
    DENSE("Dense");
    
    private final String displayName;
    
    private InstrumentDensityPreset(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return this.displayName;
    }
}
