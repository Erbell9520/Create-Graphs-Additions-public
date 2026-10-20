package com.createstats.vccmk4.core;

public enum JetKind {
    PROPELLER("propeller"),

    THRUSTER("thruster"),

    BOOSTER("booster"),

    VECTOR("vector");

    private final String key;

    JetKind(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public boolean direct() {
        return this != PROPELLER;
    }

    public boolean oneWay() {
        return this != PROPELLER;
    }

    public static JetKind byOrdinal(int ordinal) {
        JetKind[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : PROPELLER;
    }
}
