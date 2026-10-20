package com.createstats.vccmk4.core;

import java.util.List;

public enum Control {
    THROTTLE(true, "throttle", Channel.FORWARD, Channel.BACK),

    STEER(true, "steer", Channel.RIGHT, Channel.LEFT),

    BRAKE(false, "brake", Channel.JUMP, Channel.NONE),

    BOOST(false, "boost", Channel.SPRINT, Channel.NONE),

    AUX_A(true, "aux_a", Channel.AUX1, Channel.AUX2),

    AUX_B(true, "aux_b", Channel.AUX3, Channel.NONE),

    LIFT(true, "lift", Channel.NONE, Channel.NONE),

    SLIDE(true, "slide", Channel.NONE, Channel.NONE),

    AUX_C(true, "aux_c", Channel.NONE, Channel.NONE),
    AUX_D(true, "aux_d", Channel.NONE, Channel.NONE),

    BEAM(false, "beam", Channel.NONE, Channel.NONE),

    TRIM(true, "trim", Channel.NONE, Channel.NONE);

    public static final List<Control> DISPLAY = List.of(THROTTLE, STEER, BRAKE, BOOST, BEAM, LIFT, SLIDE, AUX_A, AUX_B, AUX_C, AUX_D);

    private final boolean axis;
    private final String key;
    private final Channel defaultPlus;
    private final Channel defaultMinus;

    Control(boolean axis, String key, Channel defaultPlus, Channel defaultMinus) {
        this.axis = axis;
        this.key = key;
        this.defaultPlus = defaultPlus;
        this.defaultMinus = defaultMinus;
    }

    public boolean isAxis() {
        return axis;
    }

    public boolean isAux() {
        return this == AUX_A || this == AUX_B || this == AUX_C || this == AUX_D;
    }

    public String key() {
        return key;
    }

    public int defaultPlus() {
        return defaultPlus.id();
    }

    public int defaultMinus() {
        return defaultMinus.id();
    }

    public static Control byOrdinal(int ordinal) {
        Control[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : null;
    }

    public static Control byKey(String key) {
        for (Control c : values()) {
            if (c.key.equals(key)) return c;
        }
        return null;
    }
}
