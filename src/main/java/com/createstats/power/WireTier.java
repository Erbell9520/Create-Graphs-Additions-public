package com.createstats.power;

/** [1] */
public enum WireTier {

    /** Bare copper. The starting wire, and what every existing link in a loaded world becomes. */
    COPPER("Copper Wire", 1, 0.050f,
            new int[] {198, 112, 58}, new int[] {150, 84, 44}),
    /** Copper under a polymer jacket. Twice the throughput. */
    INSULATED("Insulated Wire", 2, 0.062f,
            new int[] {74, 122, 178}, new int[] {44, 80, 126}),
    /** A braided shield over a gold core. Three times the throughput. */
    SHIELDED("Shielded Wire", 3, 0.076f,
            new int[] {216, 188, 116}, new int[] {158, 132, 74});

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

    /** Throughput multiplier over the base connector rate. */
    public int multiplier() {
        return multiplier;
    }

    /** Half the drawn cable thickness. A heavier wire reads heavier without needing a label. */
    public float halfThickness() {
        return halfThickness;
    }

    /** The two alternating strand colours, which is how a tier is identified at a glance in world. */
    public int[] strandA() {
        return strandA;
    }

    public int[] strandB() {
        return strandB;
    }

    /** Ordinal-safe lookup for NBT decode. Defaults to {@link #COPPER}, which is the pre-tier wire. */
    public static WireTier byOrdinal(int ordinal) {
        final WireTier[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : COPPER;
    }
}
