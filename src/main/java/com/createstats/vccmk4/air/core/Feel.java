package com.createstats.vccmk4.air.core;

public enum Feel {
    GENTLE(1.5, 0.25, 0.3, 4.0, 20),
    STANDARD(3.0, 0.4, 0.6, 8.0, 10),
    SPORTY(5.0, 0.6, 1.0, 14.0, 5);

    public static final int REDSTONE_STEPS = 15;

    public final double climb;

    public final double lift;

    public final double accel;

    public final double turn;

    public final int jetTicksPerStep;

    Feel(double climb, double lift, double accel, double turn, int jetTicksPerStep) {
        this.climb = climb;
        this.lift = lift;
        this.accel = accel;
        this.turn = turn;
        this.jetTicksPerStep = jetTicksPerStep;
    }

    public AltitudeLaw.Limits altitude() {
        return new AltitudeLaw.Limits(climb, lift);
    }

    public double jetStep() {
        return 1.0 / (REDSTONE_STEPS * (double) jetTicksPerStep);
    }

    public double jetSeconds() {
        return REDSTONE_STEPS * jetTicksPerStep / 20.0;
    }

    public Feel next() {
        Feel[] v = values();
        return v[(ordinal() + 1) % v.length];
    }

    public static Feel byOrdinal(int i) {
        Feel[] v = values();
        return i >= 0 && i < v.length ? v[i] : STANDARD;
    }
}
