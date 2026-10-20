package com.createstats.aerial;

import java.util.Locale;

public final class HelmWords {
    public static final double[] NOTCHES = {-1.0, -0.5, -0.25, 0.0, 0.25, 0.5, 1.0};
    public static final String[] NOTCH_WORDS = {"FULL ASTERN", "HALF ASTERN", "SLOW ASTERN", "STOP", "SLOW AHEAD",
            "HALF AHEAD", "FULL AHEAD"};

    public static final String[] NOTCH_SHORT = {"FULL", "HALF", "SLOW", "STOP", "SLOW", "HALF", "FULL"};

    private HelmWords() {
    }

    public static int notch(double order) {
        if (!Double.isFinite(order)) {
            return -1;
        }
        for (int i = 0; i < NOTCHES.length; i++) {
            if (Math.abs(NOTCHES[i] - order) < 1.0e-3) {
                return i;
            }
        }
        return -1;
    }

    public static String order(double order) {
        if (!Double.isFinite(order)) {
            return "--";
        }
        final int n = notch(order);
        if (n >= 0) {
            return NOTCH_WORDS[n];
        }
        return (order > 0.0 ? "AHEAD " : "ASTERN ") + Math.round(Math.abs(order) * 100.0) + "%";
    }

    public static String mode(String mode) {
        return switch (mode == null ? "" : mode) {
            case "HELM" -> "AT THE HELM";
            case "ANCHOR" -> "ANCHORED";
            case "KEEP_COURSE" -> "KEEPING COURSE";
            case "GO_TO" -> "GOING TO TARGET";
            case "MOORED" -> "MOORED";
            case "OFF" -> "STANDING BY";
            default -> "";
        };
    }

    public static String leave(String leave) {
        return switch (leave == null ? "" : leave) {
            case "KEEP_COURSE" -> "keeps its course";
            case "GO_TO" -> "flies to the target";
            case "ANCHOR" -> "anchors where it is";
            default -> "";
        };
    }

    public static String modeChange(String from, String to, String fromPilot, String toPilot) {
        final String who = toPilot == null || toPilot.isBlank() ? "A pilot" : toPilot;
        final String left = fromPilot == null || fromPilot.isBlank() ? "The pilot" : fromPilot;
        if ("HELM".equals(to)) {
            return who + " took the helm";
        }
        if ("HELM".equals(from)) {
            return switch (to == null ? "" : to) {
                case "ANCHOR" -> left + " left the helm: anchoring";
                case "KEEP_COURSE" -> left + " left the helm: keeping course";
                case "GO_TO" -> left + " left the helm: going to the target";
                case "MOORED" -> left + " left the helm: moored";
                default -> left + " left the helm";
            };
        }
        return switch (to == null ? "" : to) {
            case "ANCHOR" -> "Anchored";
            case "KEEP_COURSE" -> "Keeping course";
            case "GO_TO" -> "Going to the target";
            case "MOORED" -> "Moored";
            default -> "";
        };
    }

    public static String guard(AerialSnapshot.Guard g) {
        if (g == null) {
            return "";
        }
        return switch (g.verdict()) {
            case "CLIMB" -> String.format(Locale.ROOT, "Terrain guard: climbing to %s over ground at %s",
                    whole(g.floor()), whole(g.groundY()));
            case "SLOW" -> String.format(Locale.ROOT, "Terrain guard: slowed to %s b/s to clear %s in time",
                    tenth(g.speedCap()), whole(g.groundY()));
            case "WALL" -> String.format(Locale.ROOT, "Terrain guard: stopping short of ground at %s", whole(g.groundY()));
            case "BLIND" -> String.format(Locale.ROOT, "Terrain guard: slowed, ground unseen past %s m", whole(g.seen()));
            default -> "";
        };
    }

    public static String ceilingReason(AerialSnapshot.Helm h) {
        if (h == null || !Double.isFinite(h.ceiling())) {
            return h != null && h.ceiling() == Double.NEGATIVE_INFINITY ? "too heavy for its balloons" : "";
        }
        final double c = h.ceiling();
        if (Double.isFinite(h.cap()) && Math.abs(h.cap() - c) < 0.5) {
            return "your altitude cap";
        }
        if (Double.isFinite(h.worldTop()) && c >= h.worldTop() - 48.5) {
            return "the top of the world";
        }
        return "the balloons' lift";
    }

    private static String whole(double v) {
        return Double.isFinite(v) ? Long.toString(Math.round(v)) : "--";
    }

    private static String tenth(double v) {
        return Double.isFinite(v) ? String.format(Locale.ROOT, "%.1f", v) : "--";
    }
}
