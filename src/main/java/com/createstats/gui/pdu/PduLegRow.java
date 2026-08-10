package com.createstats.gui.pdu;

import com.createstats.power.PduStuds;

import java.util.Locale;

/** [1] */
public final class PduLegRow {

    private PduLegRow() {
    }

    /** What a leg is doing, in the order the panel decides between them. */
    public enum State {
        /** No wire on this stud. Not a fault. */
        UNWIRED("Not wired", false),
        /** The breaker threw itself: this leg was asked for more than its wire carries. */
        TRIPPED("TRIPPED", true),
        /** The player opened this breaker. A switch, not a fault. */
        OPEN("Open", false),
        /** Live, and turning something away for want of wire. */
        AT_CAPACITY("At capacity", true),
        /** Live and within its rating. */
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

        /** True when this state should be drawn as something wrong rather than as something chosen. */
        public boolean isAlarm() {
            return alarm;
        }
    }

    /** [2] */
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

    /** How full this leg's wire is, 0 to 1, for the bar. */
    public static float loadFraction(int demand, int rating) {
        if (rating <= 0) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, demand / (float) rating));
    }

    /** [3] */
    public static String detail(State state, int demand, int rating, int devices, boolean back) {
        return switch (state) {
            case UNWIRED -> back ? "No cable on the back boss" : "No wire on this stud";
            case TRIPPED -> "Overloaded. Close the breaker to reset";
            case OPEN -> devices == 1 ? "1 device isolated" : devices + " devices isolated";
            case AT_CAPACITY -> "Wants " + fe(demand) + " of " + fe(rating) + ", upgrade the wire";
            case LIVE -> fe(demand) + " of " + fe(rating);
        };
    }

    /** [4] */
    public static String name(int leg) {
        return leg == PduStuds.COUNT ? "Back output" : "Leg " + (leg + 1);
    }

    /** True when this leg is the cable boss on the back rather than one of the wire studs. */
    public static boolean isBack(int leg) {
        return leg == PduStuds.COUNT;
    }

    /** The old four argument form, for a stud. */
    public static String detail(State state, int demand, int rating, int devices) {
        return detail(state, demand, rating, devices, false);
    }

    /** This leg's colour, shared with the Flow diagram so one leg reads as one colour everywhere. */
    public static int colour(int leg) {
        if (isBack(leg)) {
            return 0xFFB9C4CE;   // steel, so the one cable output does not read as a ninth colour code
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

    /** What clicking this leg's breaker button should ask for. */
    public static boolean nextClosedState(State state) {
        // A tripped breaker and an open one both want closing; anything live wants opening. So the
        // button is always "the other thing", which is what a physical breaker does.
        return state == State.TRIPPED || state == State.OPEN;
    }

    /** The word on the breaker button. */
    public static String buttonLabel(State state) {
        return switch (state) {
            case TRIPPED -> "RESET";
            case OPEN -> "CLOSE";
            default -> "OPEN";
        };
    }
}
