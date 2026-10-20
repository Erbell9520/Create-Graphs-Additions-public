package com.createstats.vccmk3.avionics;

public enum SensorSource {
    SENSOR("SENS", "Read from an installed sensor", 0xFF6EE7A8),

    DERIVED("DERV", "Computed from physics, no sensor installed", 0xFFFFB454),

    NONE("----", "No reading available", 0xFF53677A);

    private final String tag;
    private final String description;
    private final int color;

    SensorSource(String tag, String description, int color) {
        this.tag = tag;
        this.description = description;
        this.color = color;
    }

    public String tag() {
        return tag;
    }

    public String description() {
        return description;
    }

    public int color() {
        return color;
    }

    public boolean hasReading() {
        return this != NONE;
    }
}
