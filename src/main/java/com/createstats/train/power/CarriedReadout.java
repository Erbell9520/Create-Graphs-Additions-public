package com.createstats.train.power;

public final class CarriedReadout {
    private static boolean active;

    private CarriedReadout() {
    }

    public static boolean active() {
        return active;
    }

    public static void set(boolean on) {
        active = on;
    }
}
