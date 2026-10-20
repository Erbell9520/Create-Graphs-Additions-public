package com.createstats.alarm.annunciator;

// same green/amber/red as the tower light so a base with both reads the same
public enum AlarmSeverity {
    NORMAL("normal", 0x2FB36D, 0),

    NOTICE("notice", 0x4FA3E3, 4),

    WARNING("warning", 0xE0A32E, 8),

    CRITICAL("critical", 0xD84A3A, 12);

    private final String serializedName;
    private final int colour;
    private final int lightLevel;

    AlarmSeverity(String serializedName, int colour, int lightLevel) {
        this.serializedName = serializedName;
        this.colour = colour;
        this.lightLevel = lightLevel;
    }

    public String getSerializedName() {
        return this.serializedName;
    }

    public int colour() {
        return this.colour;
    }

    public int lightLevel() {
        return this.lightLevel;
    }

    public boolean isAlarming() {
        return this.ordinal() >= WARNING.ordinal();
    }

    public boolean isTripped() {
        return this != NORMAL;
    }

    public int comparatorLevel() {
        return switch (this) {
            case NORMAL -> 0;
            case NOTICE -> 5;
            case WARNING -> 10;
            case CRITICAL -> 15;
        };
    }

    public AlarmSeverity next() {
        return switch (this) {
            case NORMAL, NOTICE -> WARNING;
            case WARNING -> CRITICAL;
            case CRITICAL -> NOTICE;
        };
    }

    public static AlarmSeverity worst(AlarmSeverity a, AlarmSeverity b) {
        if (a == null) {
            return b == null ? NORMAL : b;
        }
        if (b == null) {
            return a;
        }
        return a.ordinal() >= b.ordinal() ? a : b;
    }

    public static AlarmSeverity byName(String name) {
        for (AlarmSeverity severity : values()) {
            if (severity.serializedName.equalsIgnoreCase(name) || severity.name().equalsIgnoreCase(name)) {
                return severity;
            }
        }
        return WARNING;
    }
}
