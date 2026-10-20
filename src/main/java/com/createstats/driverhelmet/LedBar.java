package com.createstats.driverhelmet;

public final class LedBar {
    public static final int LEDS = 15;

    public static final int OFF = 0;
    public static final int LOW = 1;
    public static final int MID = 2;
    public static final int HIGH = 3;
    public static final int BAD = 4;
    public static final int COOL = 5;

    public static final double FLASH_HZ = 6.0;

    public static final double SELF_TEST = 0.30;

    public static final double SWEEP_PASS = 0.55;

    public static final double WARN_SLIP = 0.30;

    public enum Warning { NONE, SPIN, LOCK }

    public record Frame(int[] role, float[] level) {
    }

    private LedBar() {
    }

    public static int segment(int i) {
        return i < 5 ? LOW : i < 10 ? MID : HIGH;
    }

    public static Frame live(double throttle, Warning warning, double seconds) {
        if (warning != Warning.NONE && flashOn(seconds)) {
            final int role = warning == Warning.SPIN ? COOL : BAD;
            return uniform(role, 1.0f);
        }
        final int[] role = new int[LEDS];
        final float[] level = new float[LEDS];
        final int lit = (int) Math.round(Math.min(1.0, Math.abs(throttle)) * LEDS);
        for (int i = 0; i < LEDS; i++) {
            final int k = throttle < 0 ? LEDS - 1 - i : i;
            if (k < lit) {
                role[i] = throttle < 0 ? BAD : segment(i);
                level[i] = 1.0f;
            } else {
                role[i] = OFF;
            }
        }
        return new Frame(role, level);
    }

    public static boolean flashOn(double seconds) {
        return ((long) Math.floor(seconds * FLASH_HZ * 2.0)) % 2 == 0;
    }

    public static Frame selfTest(double seconds) {
        final int[] role = new int[LEDS];
        final float[] level = new float[LEDS];
        final double reach = Math.max(0.0, Math.min(1.0, seconds / SELF_TEST)) * (LEDS / 2.0 + 0.5);
        for (int i = 0; i < LEDS; i++) {
            if (Math.abs(i - LEDS / 2) < reach) {
                role[i] = segment(i);
                level[i] = 1.0f;
            }
        }
        return new Frame(role, level);
    }

    public static Frame sweep(double seconds) {
        final int[] role = new int[LEDS];
        final float[] level = new float[LEDS];
        final double phase = (seconds / SWEEP_PASS) % 2.0;
        final double pos = (phase < 1.0 ? phase : 2.0 - phase) * (LEDS - 1);
        final int dir = phase < 1.0 ? -1 : 1;
        for (int t = 0; t < 3; t++) {
            final int i = (int) Math.round(pos) + dir * t;
            if (i >= 0 && i < LEDS && level[i] == 0.0f) {
                role[i] = segment(i);
                level[i] = t == 0 ? 1.0f : t == 1 ? 0.45f : 0.18f;
            }
        }
        return new Frame(role, level);
    }

    public static Frame uniform(int role, float level) {
        final int[] r = new int[LEDS];
        final float[] l = new float[LEDS];
        for (int i = 0; i < LEDS; i++) {
            r[i] = role;
            l[i] = level;
        }
        return new Frame(r, l);
    }
}
