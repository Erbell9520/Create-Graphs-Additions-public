package com.createstats.vccmk4.air.core;

import java.util.Locale;

public final class FlightIntent {
    static final double HOLDING = 1.5;

    static final double SETTLING = 8.0;

    static final double TURNING = 10.0;

    static final double LONGEST_ETA = 3600.0;

    private FlightIntent() {
    }

    public record Now(AerialPilot.Mode mode, LeaveMode leave, boolean staff, double y, double held, double flown,
                      double vy, double heading, double heldHeading, double speed, double targetDist,
                      double targetBearing, String targetName, double groundClear) {
    }

    public record Line(String text, boolean alert) {
    }

    public static Line banner(Now n, String note, TerrainGuard.Result guard) {
        if (n.staff()) return new Line("HELD BY A STAFF: STANDING BY, ENGINES IDLE", false);
        TerrainGuard.Result g = guard == null ? TerrainGuard.Result.NONE : guard;
        if (g.acting() && n.mode().flying()) {
            String t = terrain(g, n.mode() == AerialPilot.Mode.HELM);
            if (t != null) return new Line(t, true);
        }
        if (note != null && !note.isBlank()) return new Line(note.trim().toUpperCase(Locale.ROOT), true);
        return new Line(intent(n), false);
    }

    static String terrain(TerrainGuard.Result g, boolean helm) {
        return switch (g.verdict()) {
            case CLEAR -> null;
            case CLIMB -> helm
                    ? String.format(Locale.ROOT, "TERRAIN AHEAD: RIDGE AT %.0f IN %.0f b, ABOVE THE HEIGHT HELD. CLIMB OR TURN.",
                    g.groundY(), g.distance())
                    : String.format(Locale.ROOT, "TERRAIN AHEAD: RIDGE AT %.0f IN %.0f b. CLIMBING TO CLEAR IT BY %.0f.",
                    g.groundY(), g.distance(), TerrainGuard.CLEARANCE);
            case SLOW -> helm
                    ? String.format(Locale.ROOT, "TERRAIN AHEAD: RIDGE AT %.0f IN %.0f b, TOO CLOSE TO CLIMB AT THIS SPEED.",
                    g.groundY(), g.distance())
                    : String.format(Locale.ROOT, "TERRAIN AHEAD: RIDGE AT %.0f IN %.0f b. SLOWED TO %.1f b/s TO CLIMB OVER IT.",
                    g.groundY(), g.distance(), g.speedCap());
            case WALL -> helm
                    ? String.format(Locale.ROOT, "TERRAIN AHEAD ABOVE THE CEILING, %.0f b ON. TURN OR STOP.", g.distance())
                    : String.format(Locale.ROOT, "TERRAIN AHEAD ABOVE THE CEILING: STOPPING SHORT, %.0f b ON.", g.distance());
            case BLIND -> helm ? null
                    : String.format(Locale.ROOT, "UNLOADED GROUND AHEAD: SLOWED TO STOP INSIDE %.0f b.", g.seen());
        };
    }

    static String intent(Now n) {
        switch (n.mode()) {
            case OFF:
                return "STANDING BY: SIT TO FLY";
            case MOORED:
                return "MOORED: SIT TO FLY";
            default:
                break;
        }
        double aim = Double.isFinite(n.flown()) ? n.flown() : n.held();

        if (Double.isFinite(n.groundClear()) && n.groundClear() < SETTLING && n.vy() < -0.05
                && Double.isFinite(aim) && aim < n.y() - HOLDING) {
            return String.format(Locale.ROOT, "SETTLING ONTO THE GROUND  %.1f b TO GO", Math.max(0.0, n.groundClear()));
        }
        if (n.mode() == AerialPilot.Mode.GO_TO && Double.isFinite(n.targetDist())) {
            String name = n.targetName() == null || n.targetName().isBlank() ? "TARGET" : n.targetName();
            double off = HeadingLaw.wrap(n.targetBearing() - n.heading());
            if (Math.abs(off) > TURNING) {
                return String.format(Locale.ROOT, "TURNING TO %03.0f, THEN %.0f b TO THE %s",
                        HeadingLaw.normalise(n.targetBearing()), n.targetDist(), name);
            }
            String eta = eta(n.targetDist(), n.speed());
            return String.format(Locale.ROOT, "%.0f b TO THE %s", n.targetDist(), name) + (eta.isEmpty() ? "" : "  " + eta);
        }
        String height = heightWords(n, aim);
        return switch (n.mode()) {
            case ANCHOR -> n.speed() > AerialPilot.ANCHOR_SPEED * 2.0 ? "COMING TO REST, THEN ANCHORING" : "ANCHORED, " + height;
            case KEEP_COURSE -> String.format(Locale.ROOT, "KEEPING COURSE %03.0f AT %.1f b/s, ",
                    Double.isFinite(n.heldHeading()) ? HeadingLaw.normalise(n.heldHeading()) : n.heading(), n.speed()) + height;
            case GO_TO -> "NO TARGET: HOLDING THIS SPOT, " + height;

            default -> !height.startsWith("HOLDING") ? height
                    : Double.isFinite(n.heldHeading())
                    ? height + String.format(Locale.ROOT, ", HEADING %03.0f", HeadingLaw.normalise(n.heldHeading()))
                    : height + ", TURNING";
        };
    }

    static String heightWords(Now n, double aim) {
        if (!Double.isFinite(aim) || Math.abs(aim - n.y()) < HOLDING) {
            return String.format(Locale.ROOT, "HOLDING %.0f", Double.isFinite(aim) ? aim : n.y());
        }
        boolean up = aim > n.y();
        double rate = up ? n.vy() : -n.vy();
        String eta = eta(Math.abs(aim - n.y()), rate);
        return String.format(Locale.ROOT, "%s TO %.0f", up ? "CLIMBING" : "DESCENDING", aim) + (eta.isEmpty() ? "" : "  " + eta);
    }

    public static String eta(double distance, double rate) {
        if (!(rate > 0.05) || !Double.isFinite(distance)) return "";
        double s = distance / rate;
        if (!(s < LONGEST_ETA)) return "";
        long whole = Math.round(s);
        if (whole < 60) return whole + " s";
        return String.format(Locale.ROOT, "%d:%02d", whole / 60, whole % 60);
    }

    public static String[] modeLines(AerialPilot.Mode mode, LeaveMode leave, boolean staff) {
        if (staff) return new String[] {"HELD", "BY A PHYSICS STAFF"};
        String label = mode.label.toUpperCase(Locale.ROOT);
        return switch (mode) {
            case HELM -> new String[] {label, "LEAVE: " + (leave == null ? LeaveMode.ANCHOR : leave).label.toUpperCase(Locale.ROOT)};
            case ANCHOR, KEEP_COURSE, GO_TO -> new String[] {label, "PILOT LEFT THE HELM"};
            case MOORED -> new String[] {label, "SIT TO FLY"};
            case OFF -> new String[] {"STANDING BY", "SIT TO FLY"};
        };
    }

    public static java.util.List<String> wrap(String line, int width) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (line == null) return out;
        int w = Math.max(8, width);
        String rest = line;
        String indent = "";
        while (indent.length() + rest.length() > w) {
            int room = w - indent.length();
            int cut = rest.lastIndexOf(' ', room);
            if (cut <= 0) cut = room;
            out.add(indent + rest.substring(0, cut).stripTrailing());
            rest = rest.substring(cut).stripLeading();
            indent = "  ";
        }
        if (!rest.isEmpty() || out.isEmpty()) out.add(indent + rest);
        return out;
    }

    public static String targetLabel(String name, double distance, double speed) {
        if (!Double.isFinite(distance)) return "";
        String eta = eta(distance, speed);
        return String.format(Locale.ROOT, "%s  %.0f b", name == null || name.isBlank() ? "TARGET" : name, distance)
                + (eta.isEmpty() ? "" : "  " + eta);
    }
}
