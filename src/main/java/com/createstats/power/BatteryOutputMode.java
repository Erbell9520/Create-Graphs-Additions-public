package com.createstats.power;

public enum BatteryOutputMode {
    DIRECT("Direct", "Serves the full demand of this circuit"),

    LIMIT("Limited", "Never exceeds the set rate"),

    SURPLUS("Surplus", "Only spare generation, never the reserve");

    private final String label;
    private final String blurb;

    BatteryOutputMode(String label, String blurb) {
        this.label = label;
        this.blurb = blurb;
    }

    public String label() {
        return label;
    }

    public String blurb() {
        return blurb;
    }

    public BatteryOutputMode next() {
        final BatteryOutputMode[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public static BatteryOutputMode byOrdinal(int ordinal) {
        final BatteryOutputMode[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : DIRECT;
    }
}
