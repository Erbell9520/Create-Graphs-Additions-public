package com.createstats.security;

import java.util.ArrayList;
import java.util.List;

// AWAY+ quietly changes seven things, so list them
public final class ModeEffects {
    private ModeEffects() {
    }

    public static final String HAZARD_NOTE = "Fire and water sensors are always armed.";

    private static final String NONE = "none in this house";

    public enum Axis {
        DOORS("Doors"),
        AUTO_OPEN("Auto-open"),
        SENSORS("Sensors"),
        IF_TRIPPED("If tripped"),
        SHUTTERS("Shutters"),
        LOCKERS("Lockers"),
        SWITCHING("Switching");

        private final String label;

        Axis(String label) {
            this.label = label;
        }

        public String label() {
            return this.label;
        }
    }

    public record Row(Axis axis, String phrase, boolean changed) {
    }

    public record House(boolean lockdown, String lockdownCause, int shutters,
                        int commandedShutters, int lockers, int sensors, int watching, int blind) {
    }

    public static String posture(SecurityMode mode, House house) {
        if (house.lockdown()) {
            return "A sensor tripped, and the house is sealed";
        }
        return switch (mode) {
            case HOME -> "You are home";
            case AWAY -> "The house is watched";
            case CUSTOM -> "Watched room by room";
            case AWAY_PLUS -> "The house is shut";
        };
    }

    public static List<Row> rows(SecurityMode mode, House house) {
        final List<Row> out = new ArrayList<>(Axis.values().length);
        for (Axis axis : Axis.values()) {
            final String phrase = phrase(mode, axis, house);

            out.add(new Row(axis, phrase, !phrase.equals(phrase(SecurityMode.HOME, axis, house))));
        }
        return List.copyOf(out);
    }

    public static String phrase(SecurityMode mode, Axis axis, House house) {
        return switch (axis) {
            case DOORS -> mode.sealsHouse() ? "owner only, on a click" : "open by clearance";
            case AUTO_OPEN -> mode.sealsHouse()
                    ? "front door only, for you"
                    : "on, as you near a door";
            case SENSORS -> sensors(mode, house);
            case IF_TRIPPED -> tripped(mode, house);
            case SHUTTERS -> shutters(mode, house);
            case LOCKERS -> house.lockers() == 0
                    ? NONE
                    : mode.sealsHouse() ? "code only, everyone" : "clearance, or a code";
            case SWITCHING -> mode.sealsHouse() ? "code to arm and to stand down" : "one tap";
        };
    }

    private static String sensors(SecurityMode mode, House house) {
        if (house.sensors() == 0) {
            return NONE;
        }
        final String armed = switch (mode) {
            case HOME -> "recording only";
            case AWAY, AWAY_PLUS -> "all armed";
            case CUSTOM -> "armed room by room";
        };
        if (house.blind() <= 0) {
            return armed;
        }

        return armed + " · " + house.blind() + " blind, no power";
    }

    private static String tripped(SecurityMode mode, House house) {
        if (house.lockdown()) {
            return house.lockdownCause() == null || house.lockdownCause().isBlank()
                    ? "locked down"
                    : "locked down · " + house.lockdownCause();
        }
        return switch (mode) {
            case HOME -> "written to the log";
            case AWAY, CUSTOM -> "the house locks down";
            case AWAY_PLUS -> "alarm only, already shut";
        };
    }

    private static String shutters(SecurityMode mode, House house) {
        if (house.shutters() == 0) {
            return NONE;
        }
        if (!mode.sealsHouse()) {
            return "as you set them";
        }

        final int reopen = Math.max(0, house.shutters() - house.commandedShutters());
        if (reopen == 0) {
            return "all shut, and all were shut already";
        }
        return "all shut · " + reopen + " reopen on exit";
    }
}
