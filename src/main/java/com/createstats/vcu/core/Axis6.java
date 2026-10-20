package com.createstats.vcu.core;

public enum Axis6 {
    FORWARD("Forward", false),
    UP("Lift", false),
    RIGHT("Strafe", false),
    ROLL("Roll", true),
    PITCH("Pitch", true),
    YAW("Yaw", true);

    public static final int COUNT = 6;

    private final String label;
    private final boolean moment;

    Axis6(String label, boolean moment) {
        this.label = label;
        this.moment = moment;
    }

    public String label() {
        return label;
    }

    public boolean isMoment() {
        return moment;
    }

    public int index() {
        return ordinal();
    }
}
