package com.createstats.vccmk4.core;

// read from the runtime jars, not assumed
public enum LinkKind {
    HITCH("hitch", false, true, true, 2, Reading.ANGLE, Style.NONE, "hitch", "on a hitch"),
    JOINT("joint", true, true, true, 3, Reading.NONE, Style.NONE, "locked joint", "on a locked joint"),
    ROPE("rope", false, false, false, 0, Reading.GAP, Style.LINE, "rope", "on a rope"),
    SPRING("spring", false, true, false, 1, Reading.STRETCH, Style.ZIGZAG, "spring", "on springs"),
    DOCK("dock", true, true, true, 4, Reading.NONE, Style.BAR, "docking connector", "docked");

    public enum Reading {
        GAP,

        STRETCH,

        ANGLE,

        NONE
    }

    public enum Style { NONE, LINE, ZIGZAG, BAR }

    private final String key;
    private final boolean rigid;
    private final boolean pushes;
    private final boolean holds;
    private final int stiffness;
    private final Reading reading;
    private final Style style;
    private final String noun;
    private final String towedAs;

    LinkKind(String key, boolean rigid, boolean pushes, boolean holds, int stiffness, Reading reading, Style style,
             String noun, String towedAs) {
        this.key = key;
        this.rigid = rigid;
        this.pushes = pushes;
        this.holds = holds;
        this.stiffness = stiffness;
        this.reading = reading;
        this.style = style;
        this.noun = noun;
        this.towedAs = towedAs;
    }

    public Reading reading() {
        return reading;
    }

    public Style style() {
        return style;
    }

    public String key() {
        return key;
    }

    public boolean rigid() {
        return rigid;
    }

    public boolean pushes() {
        return pushes;
    }

    public boolean articulates() {
        return !rigid;
    }

    public boolean holds() {
        return holds;
    }

    public String noun() {
        return noun;
    }

    public String towedAs() {
        return towedAs;
    }

    public boolean stifferThan(LinkKind other) {
        return other == null || stiffness > other.stiffness;
    }

    public static LinkKind byOrdinal(int ordinal) {
        LinkKind[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : HITCH;
    }
}
