package com.createstats.display;

public enum InstrumentStyleFamily
{
    LEGACY_ANALOG("Legacy Analog"),
    MECHANICAL_BRASS("Mechanical Brass"),
    LOCOMOTIVE_CAB("Locomotive Cab"),
    SCIENTIFIC_INSTRUMENT("Scientific Instrument"),
    CONTROL_PANEL_WALL("Control-Panel Wall");

    private final String displayName;

    private InstrumentStyleFamily(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public boolean isLegacy() {
        return this == LEGACY_ANALOG;
    }
}
