package com.createstats.sound;

import java.util.Locale;

public enum DoorClip {
    SERVO_LOOP,

    DRUM_LOOP,

    LATCH,

    SEAT,

    HINGE_SWEEP,

    SEAL_HISS,

    KNUCKLE,

    FLOOR_BAR,

    REFUSE,

    STALL,

    CONFIG;

    private final String key = name().toLowerCase(Locale.ROOT);

    public String key() {
        return key;
    }

    public String soundId() {
        return "door." + key;
    }

    public String path() {
        return "door/" + key;
    }

    public String subtitleKey() {
        return "subtitles.create_stats.powered_door." + key;
    }
}
