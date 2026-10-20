package com.createstats.vccmk4.core;

public enum JetSupply {
    NONE("none"),
    OK("ok"),

    LOW("low"),
    EMPTY("empty"),
    POWERED("powered"),
    UNPOWERED("unpowered");

    private final String key;

    JetSupply(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public boolean out() {
        return this == EMPTY || this == UNPOWERED;
    }

    public static JetSupply byOrdinal(int ordinal) {
        JetSupply[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : NONE;
    }
}
