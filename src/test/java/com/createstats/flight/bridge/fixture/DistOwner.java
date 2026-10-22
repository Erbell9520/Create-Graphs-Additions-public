package com.createstats.flight.bridge.fixture;

public class DistOwner {
    public DistOwner() {
    }

    public static String get(DistA a) {
        return "A";
    }

    public static String get(DistC c) {
        return "C";
    }

    public String name(DistA a) {
        return "named";
    }
}
