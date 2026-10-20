package com.createstats.vccmk4.air.core;

import com.createstats.vccmk4.core.Control;

// game time flashes so every beacon on every ship blinks together
public enum AirLight {
    OFF("off", null, 0),

    POSITION("position", null, 0),

    BEACON("beacon", null, 0),

    STROBE("strobe", null, 0),

    LANDING("landing", Control.BEAM, 1),

    FLOOD("flood", null, 0),

    HELM("helm", null, 0),

    MOORED("moored", null, 0),

    ASTERN("astern", null, 0),

    CLIMB("climb", null, 0),

    SINK("sink", null, 0),

    ALARM("alarm", null, 0),
    AUX_A_PLUS("aux_a_plus", Control.AUX_A, 1),
    AUX_A_MINUS("aux_a_minus", Control.AUX_A, -1),
    AUX_B_PLUS("aux_b_plus", Control.AUX_B, 1),
    AUX_B_MINUS("aux_b_minus", Control.AUX_B, -1),
    AUX_C_PLUS("aux_c_plus", Control.AUX_C, 1),
    AUX_C_MINUS("aux_c_minus", Control.AUX_C, -1),
    AUX_D_PLUS("aux_d_plus", Control.AUX_D, 1),
    AUX_D_MINUS("aux_d_minus", Control.AUX_D, -1);

    public static final int RECEIVER = -1;
    public static final int WHITE = 0;
    public static final int RED = 1;
    public static final int AMBER = 2;

    public static final int FULL = 15;
    public static final int LOW = 8;

    public static final double LOW_HEIGHT = 24.0;

    public static final double CLIMB_RATE = 1.0;

    public final String key;

    public final Control control;

    public final int sign;

    AirLight(String key, Control control, int sign) {
        this.key = key;
        this.control = control;
        this.sign = sign;
    }

    public boolean keyed() {
        return control != null && this != LANDING;
    }

    public static AirLight byOrdinal(int i) {
        AirLight[] v = values();
        return i >= 0 && i < v.length ? v[i] : OFF;
    }

    public static AirLight byKey(String key) {
        for (AirLight l : values()) if (l.key.equals(key)) return l;
        return OFF;
    }

    public record Signals(boolean flying, boolean piloted, boolean moored, boolean low, boolean night,
                          boolean astern, double climb, boolean alarm, boolean beam, double[] controls, long gameTime) {
    }

    public int level(Signals s, boolean held) {
        return switch (this) {
            case OFF -> 0;
            case POSITION -> s.flying() ? 15 : 0;
            case BEACON -> s.flying() && flash(s.gameTime(), 24, 0, 4) ? 15 : 0;
            case STROBE -> s.flying() && (flash(s.gameTime(), 26, 0, 2) || flash(s.gameTime(), 26, 4, 2)) ? 15 : 0;
            case LANDING -> s.flying() && (s.low() || s.beam()) ? 15 : 0;
            case FLOOD -> s.flying() && s.night() ? 15 : 0;
            case HELM -> s.piloted() ? 15 : 0;
            case MOORED -> s.moored() ? 15 : 0;
            case ASTERN -> s.flying() && s.astern() ? 15 : 0;
            case CLIMB -> s.flying() && s.climb() > CLIMB_RATE ? 15 : 0;
            case SINK -> s.flying() && s.climb() < -CLIMB_RATE ? 15 : 0;
            case ALARM -> s.alarm() ? 15 : 0;
            default -> held ? 15 : 0;
        };
    }

    public int strength(Signals s, boolean held, int lamp) {
        boolean on;
        if (lamp == AMBER && (this == BEACON || this == STROBE)) on = s.flying();
        else on = level(s, held) > 0;
        if (!on) return 0;

        if (lamp == WHITE && (this == POSITION || this == HELM || this == MOORED)) return LOW;
        return FULL;
    }

    public static AirLight propose(int lamp, double up, double forward) {
        return switch (lamp) {
            case WHITE -> up < -0.5 || forward > 0.5 ? LANDING : forward < -0.5 ? POSITION : FLOOD;

            case RED -> Math.abs(up) > 0.5 ? BEACON : POSITION;
            case AMBER -> BEACON;
            default -> OFF;
        };
    }

    public static final class Latch {
        private boolean on;
        private boolean was;

        public boolean step(boolean held, boolean toggle) {
            boolean rising = held && !was;
            was = held;
            if (!toggle) {
                on = false;
                return held;
            }
            if (rising) on = !on;
            return on;
        }

        public boolean on() {
            return on;
        }
    }

    public boolean keyHeld(Signals s) {
        if (control == null) return false;
        double v = s.controls()[control.ordinal()];
        return sign > 0 ? v > 0.5 : v < -0.5;
    }

    static boolean flash(long t, int period, int offset, int width) {
        long p = Math.floorMod(t - offset, (long) period);
        return p < width;
    }
}
