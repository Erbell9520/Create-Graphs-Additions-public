package com.createstats.power;

/** [1] */
public enum CableTier {

    /** The starting cable. One circuit, base rate, and slim enough to disappear into a wall. */
    SURFACE("Surface Cable", 1, 2.0f, 1.0f),
    /** A heavier industrial channel at three times the rate, and visibly wider for it. */
    TRAY("Cable Tray", 3, 3.0f, 1.5f),
    /** The trunk grade: six times the base rate, and the fattest thing that will still hug a wall. */
    DUCT("Bus Duct", 6, 4.0f, 2.0f);

    private final String displayName;
    private final int multiplier;
    private final float thicknessPx;
    private final float halfWidthPx;

    CableTier(String displayName, int multiplier, float thicknessPx, float halfWidthPx) {
        this.displayName = displayName;
        this.multiplier = multiplier;
        this.thicknessPx = thicknessPx;
        this.halfWidthPx = halfWidthPx;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Throughput multiplier over the base connector rate. */
    public int multiplier() {
        return multiplier;
    }

    /** How far the cable stands off the face it is stuck to, in pixels. */
    public float thicknessPx() {
        return thicknessPx;
    }

    /** Half the drawn width across the run, in pixels. Wider grade, wider cord. */
    public float halfWidthPx() {
        return halfWidthPx;
    }

    /** FE per tick one block of this grade moves, given the base connector rate. */
    public int rate(int baseRate) {
        return Math.max(0, baseRate) * multiplier;
    }

    /** Ordinal-safe lookup for NBT and packet decode. Defaults to the grade every world already has. */
    public static CableTier byOrdinal(int ordinal) {
        final CableTier[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : SURFACE;
    }
}
