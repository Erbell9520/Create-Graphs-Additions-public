package com.createstats.client.ponder.coupler;

public final class CouplerPonderScript {
    private CouplerPonderScript() {
    }

    public static final int PLATE = 5;
    public static final int[] COUPLER = {2, 1, 2};

    public static final int[] THEIRS = {2, 1, 1};

    public static final int[] OURS = {4, 1, 3};

    public static final float SCALE = 1.75f;

    public static final int TAG_BELOW = 7;

    public static final int RATE = 2048;

    public static final int STORE = 8192;

    public static final int COUPLER_IN = 8;
    public static final int THEIRS_IN = 100;

    public static final int THEIRS_ON = THEIRS_IN + 15;
    public static final int SPAN_HINT = 207;
    public static final int OURS_IN = 300;
    public static final int WIRE = 314;
    public static final int WIRE_TICKS = 18;

    public static final int WIRED = WIRE + WIRE_TICKS - 1;
    public static final int PULL_RUN = 430;
    public static final int PUSH_CLICK = 530;
    public static final int PUSH_RUN = 535;
    public static final int OFF_CLICK = 635;
    public static final int OFF = 640;
    public static final int BACK_CLICK = 733;
    public static final int BACK = 737;

    public static final int TURN = 835;
    public static final int TURN_TICKS = 30;
    public static final float TURN_DEGREES = 70.0f;
    public static final int LOOP = 965;
    public static final int SHED = 1065;
    public static final int THEIRS_OUT = 1165;

    public static final int THEIRS_GONE = THEIRS_OUT + 15;
    public static final int THEIRS_BACK = 1278;
    public static final int THEIRS_BACK_ON = THEIRS_BACK + 15;
    public static final int LENGTH = 1401;

    public static final int[] CAPTIONS = {20, 125, 215, 340, 440, 545, 650, 745, 875, 975, 1075, 1188, 1301};

    public static final int HOLD = 80;

    public enum Mode { PULL, PUSH, OFF }

    public record Frame(Mode mode, int importFe, int exportFe, String fault, String endpoint, boolean wired,
                        int stored, String remembered) {
    }

    public static Frame at(int t) {
        final boolean theirs = (t >= THEIRS_ON && t < THEIRS_GONE) || t >= THEIRS_BACK_ON;
        final boolean wired = t >= WIRED;
        final String endpoint = theirs ? "BOLTED_GENERIC" : "NONE";
        if (t < PULL_RUN) {
            final String fault = !theirs ? "NO_FOREIGN" : !wired ? "NO_GRID" : "THEIRS_NOT_GIVING";
            return new Frame(Mode.PULL, 0, 0, fault, endpoint, wired, 0, "pull");
        }
        if (t < PUSH_RUN) {
            return new Frame(Mode.PULL, RATE, 0, "NONE", endpoint, wired, STORE, "pull");
        }
        if (t < OFF) {
            return new Frame(Mode.PUSH, 0, RATE, "NONE", endpoint, wired, STORE, "push");
        }
        if (t < BACK) {
            return new Frame(Mode.OFF, 0, 0, "SWITCHED_OFF", endpoint, wired, STORE, "push");
        }
        if (t < LOOP) {
            return new Frame(Mode.PUSH, 0, RATE, "NONE", endpoint, wired, STORE, "push");
        }
        if (t < SHED) {
            return new Frame(Mode.PUSH, 0, 0, "LOOPED", endpoint, wired, STORE, "push");
        }
        if (t < THEIRS_OUT) {
            return new Frame(Mode.PUSH, 0, 0, "SHED", endpoint, wired, STORE, "push");
        }
        if (!theirs) {
            return new Frame(Mode.PUSH, 0, 0, t < THEIRS_GONE ? "NONE" : "NO_FOREIGN", endpoint, wired, STORE,
                    "push");
        }
        return new Frame(Mode.PUSH, 0, t < THEIRS_BACK_ON ? 0 : RATE, "NONE", endpoint, wired, STORE, "push");
    }
}
