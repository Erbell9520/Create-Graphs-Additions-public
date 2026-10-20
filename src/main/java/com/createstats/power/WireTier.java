package com.createstats.power;

public enum WireTier {
    COPPER("Copper Wire", 1, 0.050f,
            new int[] {198, 112, 58}, new int[] {150, 84, 44}),

    INSULATED("Insulated Wire", 2, 0.062f,
            new int[] {74, 122, 178}, new int[] {44, 80, 126}),

    SHIELDED("Shielded Wire", 4, 0.076f,
            new int[] {216, 188, 116}, new int[] {158, 132, 74}),

    HV("HV Line", 8, 0.094f,
            new int[] {66, 70, 78}, new int[] {184, 190, 198});

    private final String displayName;
    private final int multiplier;
    private final float halfThickness;
    private final int[] strandA;
    private final int[] strandB;

    WireTier(String displayName, int multiplier, float halfThickness, int[] strandA, int[] strandB) {
        this.displayName = displayName;
        this.multiplier = multiplier;
        this.halfThickness = halfThickness;
        this.strandA = strandA;
        this.strandB = strandB;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int multiplier() {
        return multiplier;
    }

    public float halfThickness() {
        return halfThickness;
    }

    public int[] strandA() {
        return strandA;
    }

    public int[] strandB() {
        return strandB;
    }

    public static WireTier byOrdinal(int ordinal) {
        final WireTier[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : COPPER;
    }
}
