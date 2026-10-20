package com.createstats.gui.pdu;

import com.createstats.power.PduStuds;

import java.util.Locale;

public final class PduLegRow {
    private PduLegRow() {
    }

    public enum State {
        UNWIRED("Not wired", false),

        TRIPPED("TRIPPED", true),

        OPEN("Open", false),

        AT_CAPACITY("At capacity", true),

        LIVE("Live", false);

        private final String label;
        private final boolean alarm;

        State(String label, boolean alarm) {
            this.label = label;
            this.alarm = alarm;
        }

        public String label() {
            return label;
        }

        public boolean isAlarm() {
            return alarm;
        }
    }

    public static State stateOf(boolean wired, boolean closed, boolean tripped, boolean overCapacity) {
        if (!wired) {
            return State.UNWIRED;
        }
        if (tripped) {
            return State.TRIPPED;
        }
        if (!closed) {
            return State.OPEN;
        }
        return overCapacity ? State.AT_CAPACITY : State.LIVE;
    }

    public static float loadFraction(int demand, int rating) {
        if (rating <= 0) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, demand / (float) rating));
    }

    public static String detail(State state, int demand, int rating, int devices, boolean back) {
        return switch (state) {
            case UNWIRED -> back ? "No cable on the back boss" : "No wire on this stud";
            case TRIPPED -> "Overloaded. Close the breaker to reset";
            case OPEN -> devices == 1 ? "1 device isolated" : devices + " devices isolated";
            case AT_CAPACITY -> "Wants " + fe(demand) + " of " + fe(rating) + ", upgrade the wire";
            case LIVE -> fe(demand) + " of " + fe(rating);
        };
    }

    public static String name(int leg) {
        return leg == PduStuds.COUNT ? "Back output" : "Leg " + (leg + 1);
    }

    public static boolean isBack(int leg) {
        return leg == PduStuds.COUNT;
    }

    public static String detail(State state, int demand, int rating, int devices) {
        return detail(state, demand, rating, devices, false);
    }

    public static String detail(State state, int demand, int rating, int devices, boolean back,
                                boolean board, boolean exportHeld) {
        if (!board) {
            return detail(state, demand, rating, devices, back);
        }
        if (state == State.UNWIRED || state == State.TRIPPED || state == State.OPEN) {
            return detail(state, demand, rating, devices, back);
        }
        if (exportHeld) {
            return "Board on this leg. No spare to export: its critical still runs";
        }
        return "Board on this leg. " + detail(state, demand, rating, devices, back);
    }

    public static int colour(int leg) {
        if (isBack(leg)) {
            return 0xFFB9C4CE;
        }
        return switch (Math.floorMod(leg, PduStuds.COUNT)) {
            case 0 -> 0xFFE0534A;
            case 1 -> 0xFFE28B3C;
            case 2 -> 0xFFE8C74A;
            case 3 -> 0xFF67C266;
            case 4 -> 0xFF4FA8D8;
            case 5 -> 0xFF8B7FD4;
            case 6 -> 0xFFC977B8;
            default -> 0xFF9AA6B2;
        };
    }

    private static String fe(int value) {
        return String.format(Locale.ROOT, "%,d", value) + " FE/t";
    }

    public static boolean nextClosedState(State state) {
        return state == State.TRIPPED || state == State.OPEN;
    }

    public static String buttonLabel(State state) {
        return switch (state) {
            case TRIPPED -> "RESET";
            case OPEN -> "CLOSE";
            default -> "OPEN";
        };
    }
}
