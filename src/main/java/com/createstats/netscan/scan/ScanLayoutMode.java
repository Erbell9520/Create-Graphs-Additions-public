package com.createstats.netscan.scan;

public enum ScanLayoutMode {
    DRIVE("Drive", "Layered by distance from the source"),

    RING("Ring", "One ring per step out from the source"),

    POWER("Power", "Branches sized by the stress they carry"),

    PLAN("Plan", "Where the elements actually are, seen from above");

    private final String label;
    private final String reading;

    ScanLayoutMode(String label, String reading) {
        this.label = label;
        this.reading = reading;
    }

    public String label() {
        return this.label;
    }

    public String reading() {
        return this.reading;
    }

    public boolean square() {
        return this == RING || this == PLAN;
    }

    public static ScanLayoutMode byOrdinal(int ordinal) {
        final ScanLayoutMode[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : DRIVE;
    }
}
