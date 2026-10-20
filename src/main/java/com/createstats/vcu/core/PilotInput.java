package com.createstats.vcu.core;

public final class PilotInput {
    public static final int FORWARD = 1;
    public static final int BACK = 1 << 1;
    public static final int LEFT = 1 << 2;
    public static final int RIGHT = 1 << 3;
    public static final int UP = 1 << 4;
    public static final int DOWN = 1 << 5;
    public static final int AUX_LEFT = 1 << 6;
    public static final int AUX_RIGHT = 1 << 7;
    public static final int BOOST = 1 << 8;

    public static final int ALL = (1 << 9) - 1;

    private PilotInput() {
    }

    public static double axis(int bits, int plus, int minus) {
        double v = 0.0;
        if ((bits & plus) != 0) v += 1.0;
        if ((bits & minus) != 0) v -= 1.0;
        return v;
    }

    public static boolean held(int bits, int key) {
        return (bits & key) != 0;
    }
}
