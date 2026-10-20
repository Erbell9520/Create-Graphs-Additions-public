package com.createstats.vccmk4.core;

public enum PartInput {
    AUX_A(Control.AUX_A, true),
    AUX_B(Control.AUX_B, false),
    AUX_C(Control.AUX_C, true),
    AUX_D(Control.AUX_D, true),
    STEER(Control.STEER, false),
    THROTTLE(Control.THROTTLE, false),
    LIFT(Control.LIFT, false),
    SLIDE(Control.SLIDE, false),
    OFF(null, false);

    public static final PartInput[] AUTO_ORDER = {AUX_A, AUX_B, AUX_C, AUX_D};

    private final Control control;
    private final boolean latchedByDefault;

    PartInput(Control control, boolean latchedByDefault) {
        this.control = control;
        this.latchedByDefault = latchedByDefault;
    }

    public Control control() {
        return control;
    }

    public String key() {
        return control == null ? "off" : control.key();
    }

    public boolean latchedByDefault() {
        return latchedByDefault;
    }

    public static PartInput byOrdinal(int ordinal) {
        PartInput[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : OFF;
    }

    public static PartInput byKey(String key) {
        for (PartInput s : values()) {
            if (s.key().equals(key)) return s;
        }
        return OFF;
    }

    public static PartInput of(Control control) {
        for (PartInput s : values()) {
            if (s.control == control) return s;
        }
        return OFF;
    }

    public static PartInput fromLegacyOrdinal(int ordinal) {
        return switch (ordinal) {
            case 0 -> AUX_A;
            case 1 -> AUX_B;
            default -> OFF;
        };
    }
}
