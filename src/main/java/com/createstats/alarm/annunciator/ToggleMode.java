package com.createstats.alarm.annunciator;

public enum ToggleMode {
    ARMED("armed"),

    MUTED("muted"),

    TEST("test");

    private final String serializedName;

    ToggleMode(String serializedName) {
        this.serializedName = serializedName;
    }

    public String getSerializedName() {
        return this.serializedName;
    }

    public boolean isMuted() {
        return this == MUTED;
    }

    public boolean isTest() {
        return this == TEST;
    }

    public ToggleMode next() {
        final ToggleMode[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    public float leverDegrees() {
        return switch (this) {
            case ARMED -> -28.0f;
            case MUTED -> 0.0f;
            case TEST -> 28.0f;
        };
    }

    public static ToggleMode byName(String name) {
        for (ToggleMode mode : values()) {
            if (mode.serializedName.equalsIgnoreCase(name) || mode.name().equalsIgnoreCase(name)) {
                return mode;
            }
        }
        return ARMED;
    }
}
