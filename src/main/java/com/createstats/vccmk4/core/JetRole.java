package com.createstats.vccmk4.core;

public enum JetRole {
    AUTO("auto", null),

    PUSH("push", JetMixer.Job.PUSH),

    TURN("turn", JetMixer.Job.TURN),

    LIFT("lift", JetMixer.Job.LIFT),

    SLIDE("slide", JetMixer.Job.SLIDE),

    THROTTLE("throttle", null),

    AUX_A("aux_a", null),
    AUX_B("aux_b", null),
    AUX_C("aux_c", null),
    AUX_D("aux_d", null),

    OFF("off", null);

    private final String key;
    private final JetMixer.Job job;

    JetRole(String key, JetMixer.Job job) {
        this.key = key;
        this.job = job;
    }

    public String key() {
        return key;
    }

    public JetMixer.Job job() {
        return job;
    }

    public Control auxControl() {
        return switch (this) {
            case AUX_A -> Control.AUX_A;
            case AUX_B -> Control.AUX_B;
            case AUX_C -> Control.AUX_C;
            case AUX_D -> Control.AUX_D;
            default -> null;
        };
    }

    public static JetRole byOrdinal(int ordinal) {
        JetRole[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : AUTO;
    }

    public static JetRole byKey(String key) {
        for (JetRole r : values()) {
            if (r.key.equals(key)) return r;
        }
        return AUTO;
    }

    public static JetRole fromLegacyOrdinal(int ordinal) {
        return switch (ordinal) {
            case 1 -> THROTTLE;
            case 2 -> OFF;
            default -> AUTO;
        };
    }
}
