package com.createstats.vccmk3.alert;

public enum AlertLevel {
    OK("RDY", 0xFF6EE7A8),

    ADVISORY("ADV", 0xFF62D5FF),

    CAUTION("CAUT", 0xFFFFB454),

    WARNING("WARN", 0xFFFF6B6B);

    private final String tag;
    private final int color;

    AlertLevel(String tag, int color) {
        this.tag = tag;
        this.color = color;
    }

    public String tag() {
        return tag;
    }

    public int color() {
        return color;
    }

    public boolean shouldPulse() {
        return this == WARNING;
    }

    public static AlertLevel byOrdinal(int ordinal) {
        AlertLevel[] all = values();
        return (ordinal >= 0 && ordinal < all.length) ? all[ordinal] : OK;
    }
}
