package com.createstats.vcu.core;

public enum Scheme {
    GROUND("Ground"),

    AIRSHIP("Airship"),

    PLANE("Plane"),

    HOVER("Hover");

    private final String label;

    Scheme(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    // order matters: balloons, then surfaces, then lift thrusters, then wheels. a car with jets is a car
    public static Scheme detect(Traits t) {
        if (t.liftEmitters() > 0) return AIRSHIP;
        if (t.surfacesRollAndPitch()) return PLANE;
        if (t.thrustUp() > 1.0e-9 && t.thrustUp() >= 0.5 * t.thrustTotal()) return HOVER;
        if (t.wheels() > 0) return GROUND;
        if (t.thrustForward() > 1.0e-9) return AIRSHIP;
        return GROUND;
    }

    public record Traits(int wheels, int drivenWheels, int surfaces, boolean surfacesRollAndPitch,
                         int liftEmitters, double thrustUp, double thrustForward, double thrustTotal) {
    }
}
