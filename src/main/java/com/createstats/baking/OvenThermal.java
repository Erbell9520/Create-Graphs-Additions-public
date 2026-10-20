package com.createstats.baking;

public final class OvenThermal {
    public static final int AMBIENT = 20_000;

    public static final int[] SETPOINTS = {160_000, 180_000, 200_000};

    public static final int[] TIME_PERCENT = {125, 100, 90};

    public static final int DRAW_ELEMENT = 128;

    public static final int GAIN_PER_FE = 4;

    public static final int LOSS_DIVISOR = 1_000;

    public static final int BAKE_BAND = 5_000;

    public static final int OPEN_DOOR_LOSS = 6;

    private int heat = AMBIENT;

    public OvenThermal() {
    }

    public int heat() {
        return heat;
    }

    public void setHeat(int value) {
        this.heat = Math.max(AMBIENT, Math.min(SETPOINTS[SETPOINTS.length - 1] + 50_000, value));
    }

    public int celsius() {
        return heat / 1_000;
    }

    public int excess() {
        return Math.max(0, heat - AMBIENT);
    }

    public static int lossPerTick(int excess) {
        if (excess <= 0) {
            return 0;
        }
        return (excess + LOSS_DIVISOR - 1) / LOSS_DIVISOR;
    }

    public static int feForHeat(int milliDegrees) {
        if (milliDegrees <= 0) {
            return 0;
        }
        return (milliDegrees + GAIN_PER_FE - 1) / GAIN_PER_FE;
    }

    public static int heatFromFe(int fe) {
        return Math.max(0, fe) * GAIN_PER_FE;
    }

    public int elementDraw(int setpoint) {
        if (heat > setpoint) {
            return 0;
        }
        final int want = lossPerTick(excess()) + (setpoint - heat);
        return Math.min(DRAW_ELEMENT, feForHeat(want));
    }

    public boolean atTemperature(int setpoint) {
        return heat >= setpoint - BAKE_BAND;
    }

    public int step(int fe) {
        return step(fe, 1);
    }

    public int step(int fe, int lossFactor) {
        heat -= lossPerTick(excess()) * Math.max(1, lossFactor);
        heat += heatFromFe(fe);
        if (heat < AMBIENT) {
            heat = AMBIENT;
        }
        return heat;
    }

    // simulated not solved. the formula gets the curve right and the integer tick count wrong, and the player sees this number
    public static int preheatTicks(int from, int setpoint) {
        final OvenThermal probe = new OvenThermal();
        probe.setHeat(from);
        final int cap = 20 * 60 * 10;
        for (int t = 0; t < cap; t++) {
            if (probe.atTemperature(setpoint)) {
                return t;
            }
            probe.step(DRAW_ELEMENT);
        }
        return -1;
    }

    public static int bakeTicks(int recipeTicks, int setting) {
        final int percent = TIME_PERCENT[Math.floorMod(setting, TIME_PERCENT.length)];
        return Math.max(1, Math.round(recipeTicks * percent / 100.0f));
    }

    public static int setpoint(int setting) {
        return SETPOINTS[Math.floorMod(setting, SETPOINTS.length)];
    }

    public static int setpointCelsius(int setting) {
        return setpoint(setting) / 1_000;
    }

    public static int settings() {
        return SETPOINTS.length;
    }
}
