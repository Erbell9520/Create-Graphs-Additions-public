package com.createstats.power;

// width = capacity, readable across a room
public enum CableTier {
    SURFACE("Surface Cable", 0.5f, 2.1f, 1.0f, 0.35f),

    TRAY("Cable Tray", 1.0f, 3.0f, 2.0f, 0.5f),

    DUCT("Bus Duct", 2.0f, 3.0f, 3.0f, 0.75f),

    TRUNK("Busbar Trunk", 4.0f, 2.0f, 3.5f, 1.4f);

    private final String displayName;
    private final float multiplier;
    private final float thicknessPx;
    private final float halfWidthPx;
    private final float reliefPx;

    CableTier(String displayName, float multiplier, float thicknessPx, float halfWidthPx,
              float reliefPx) {
        this.displayName = displayName;
        this.multiplier = multiplier;
        this.thicknessPx = thicknessPx;
        this.halfWidthPx = halfWidthPx;
        this.reliefPx = reliefPx;
    }

    public String getDisplayName() {
        return displayName;
    }

    public float multiplier() {
        return multiplier;
    }

    public float thicknessPx() {
        return thicknessPx;
    }

    public float halfWidthPx() {
        return halfWidthPx;
    }

    public float reliefPx() {
        return reliefPx;
    }

    public int rate(int baseRate) {
        return Math.round(Math.max(0, baseRate) * multiplier);
    }

    public static CableTier byOrdinal(int ordinal) {
        final CableTier[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : SURFACE;
    }
}
