package com.createstats.vccmk4.air.core;

public enum LeaveMode {
    ANCHOR("Anchor", "holds its altitude, heading and spot"),

    KEEP_COURSE("Keep course", "keeps its altitude, heading and engine order"),

    GO_TO("Go to target", "flies to the Navigation Table target, then anchors");

    public final String label;
    public final String does;

    LeaveMode(String label, String does) {
        this.label = label;
        this.does = does;
    }

    public LeaveMode next() {
        LeaveMode[] v = values();
        return v[(ordinal() + 1) % v.length];
    }

    public static LeaveMode byOrdinal(int i) {
        LeaveMode[] v = values();
        return i >= 0 && i < v.length ? v[i] : ANCHOR;
    }
}
