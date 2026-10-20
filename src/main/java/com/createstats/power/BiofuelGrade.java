package com.createstats.power;

public enum BiofuelGrade {
    GREEN(20),

    FIBRE(40),

    CROP(80),

    PLANK(100),

    PACKED(180),

    PRESSED(200),

    DENSE(360),

    LOG(600),

    BALE(720);

    public static final int PRESS_BATCH = 9;

    private final int burnTicks;

    BiofuelGrade(int burnTicks) {
        this.burnTicks = burnTicks;
    }

    public int burnTicks() {
        return burnTicks;
    }

    public int pressedBricks() {
        return burnTicks / GREEN.burnTicks;
    }

    public boolean compacts() {
        return this != PRESSED && pressedBricks() < PRESS_BATCH;
    }
}
