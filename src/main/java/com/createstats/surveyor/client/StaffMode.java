package com.createstats.surveyor.client;

public enum StaffMode {
    SURVEY("survey", 0xFF5CE1E6),

    EDIT("edit", 0xFFE4B763),

    WATCH("watch", 0xFF6FE36F),

    LINK("link", 0xFFB78CFF);

    public final String key;
    public final int colour;

    StaffMode(String key, int colour) {
        this.key = key;
        this.colour = colour;
    }

    public StaffMode next(int direction) {
        StaffMode[] v = values();
        return v[Math.floorMod(ordinal() + (direction < 0 ? -1 : 1), v.length)];
    }

    public static StaffMode byOrdinal(int i) {
        StaffMode[] v = values();
        return i >= 0 && i < v.length ? v[i] : SURVEY;
    }
}
