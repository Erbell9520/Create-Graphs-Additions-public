package com.createstats.vccmk4.core;

public enum LinkSource {
    OFF("off", null, 0),

    DRIVING("driving", null, 0),

    BRAKE("brake", null, 0),

    REVERSE("reverse", null, 0),

    BOOST("boost", null, 0),

    LEFT("left", null, 0),

    RIGHT("right", null, 0),
    AUX_A_PLUS("aux_a_plus", Control.AUX_A, 1),
    AUX_A_MINUS("aux_a_minus", Control.AUX_A, -1),
    AUX_B_PLUS("aux_b_plus", Control.AUX_B, 1),
    AUX_B_MINUS("aux_b_minus", Control.AUX_B, -1),
    AUX_C_PLUS("aux_c_plus", Control.AUX_C, 1),
    AUX_C_MINUS("aux_c_minus", Control.AUX_C, -1),
    AUX_D_PLUS("aux_d_plus", Control.AUX_D, 1),
    AUX_D_MINUS("aux_d_minus", Control.AUX_D, -1),

    TAIL_BRAKE("tail_brake", null, 0),

    HEADLIGHT("headlight", Control.BEAM, 1);

    private final String key;
    private final Control control;
    private final int sign;

    LinkSource(String key, Control control, int sign) {
        this.key = key;
        this.control = control;
        this.sign = sign;
    }

    public String key() {
        return key;
    }

    public Control control() {
        return control;
    }

    public int sign() {
        return sign;
    }

    public boolean keyed() {
        return control != null;
    }

    public Control shownUnder() {
        return switch (this) {
            case BRAKE, TAIL_BRAKE -> Control.BRAKE;
            case REVERSE -> Control.THROTTLE;
            case BOOST -> Control.BOOST;
            case LEFT, RIGHT -> Control.STEER;
            default -> control;
        };
    }

    public static LinkSource firstKeyOf(Control auxiliary) {
        for (LinkSource s : values()) {
            if (s.control != null && s.control == auxiliary && s.sign > 0) return s;
        }
        return OFF;
    }

    public static LinkSource byOrdinal(int ordinal) {
        LinkSource[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : OFF;
    }

    public static LinkSource byKey(String key) {
        for (LinkSource s : values()) {
            if (s.key.equals(key)) return s;
        }
        return OFF;
    }
}
