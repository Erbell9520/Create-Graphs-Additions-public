package com.createstats.vccmk4.core;

public enum Channel {
    NONE(0, "none"),
    FORWARD(1, "forward"),
    BACK(2, "back"),
    LEFT(3, "left"),
    RIGHT(4, "right"),
    JUMP(5, "jump"),
    SPRINT(6, "sprint"),
    AUX1(7, "aux1"),
    AUX2(8, "aux2"),
    AUX3(9, "aux3");

    public static final int LAST_ID = 9;

    private final int id;
    private final String key;

    Channel(int id, String key) {
        this.id = id;
        this.key = key;
    }

    public int id() {
        return id;
    }

    public String key() {
        return key;
    }

    public static Channel byId(int id) {
        for (Channel c : values()) {
            if (c.id == id) return c;
        }
        return NONE;
    }
}
