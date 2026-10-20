package com.createstats.cinecam.core;

public enum CineMode {
    TRACK("track", 0xFF5CE1E6, 0),

    PHOTO("photo", 0xFF6FE36F, 2),

    FILM("film", 0xFFB78CFF, 3);

    public static final int SLOTS = 4;

    public static final int SPARE_COLOUR = 0xFFE4B763;

    private final String key;
    private final int colour;
    private final int slot;

    CineMode(String key, int colour, int slot) {
        this.key = key;
        this.colour = colour;
        this.slot = slot;
    }

    public String key() {
        return key;
    }

    public int colour() {
        return colour;
    }

    public int slot() {
        return slot;
    }

    public static CineMode byIndex(int i) {
        CineMode[] all = values();
        return all[Math.floorMod(i, all.length)];
    }

    public static int slotColour(int slot) {
        for (CineMode m : values()) {
            if (m.slot == Math.floorMod(slot, SLOTS)) return m.colour;
        }
        return SPARE_COLOUR;
    }

    public static CineMode fromSavedIndex(int i) {
        return switch (i) {
            case 2 -> PHOTO;
            case 3 -> FILM;
            default -> TRACK;
        };
    }

    public static int stepsBetween(CineMode from, CineMode to) {
        return Math.floorMod(to.slot - from.slot, SLOTS);
    }
}
