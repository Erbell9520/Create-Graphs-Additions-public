package com.createstats.power;

import java.util.Locale;

// one class so the goggles and the screen can't say different things
public final class ReserveWords {
    private ReserveWords() {
    }

    public static String fe(long value) {
        final long abs = Math.abs(value);
        if (abs >= 1_000_000L) {
            return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0);
        }
        if (abs >= 100_000L) {
            return String.format(Locale.ROOT, "%.0fk", value / 1_000.0);
        }
        if (abs >= 1_000L) {
            return String.format(Locale.ROOT, "%.1fk", value / 1_000.0);
        }
        return String.valueOf(value);
    }

    public static String span(int ticks) {
        final int secs = Math.max(0, ticks) / 20;
        if (secs < 60) {
            return secs + " s";
        }
        final int mins = Math.round(secs / 60.0f);
        if (mins < 60) {
            return mins + " min";
        }
        return String.format(Locale.ROOT, "%d h %02d min", mins / 60, mins % 60);
    }

    public static String boardOutlook(int shortIn, int settle, int rate) {
        final String rest = settle >= 0 ? "; banks settle near " + settle + "%" : "";
        if (shortIn == 0) {
            return "already metered by the controller" + rest;
        }
        if (shortIn > 0) {
            return "whole for about " + span(shortIn) + ", then metered" + rest;
        }
        return rate < 0 ? "whole for the next " + horizon() : "generation covers everything";
    }

    public static String horizon() {
        return span(ReserveProjection.HORIZON_TICKS);
    }

    public static String controllerOutlook(ReserveProjection.Result r, int rate) {
        if (r == null || r.trace().length == 0) {
            return "no banks to forecast";
        }
        final ReserveProjection.Milestone rest = r.first(ReserveProjection.Kind.SETTLES);
        final String settles = rest == null ? "" : "; banks settle near " + rest.value() + "%";
        if (r.shortNow()) {
            return "an output is already short" + settles;
        }
        final ReserveProjection.Milestone first = r.first(ReserveProjection.Kind.SHORT);
        if (first != null) {
            return "everything served for about " + span(first.tick()) + ", then "
                    + outputName(first.output()) + " goes short" + settles;
        }
        final ReserveProjection.Milestone full = r.first(ReserveProjection.Kind.FULL);
        if (full != null) {
            return "everything served; banks full in about " + span(full.tick());
        }
        return rate < 0 ? "everything served for the next " + horizon()
                : "everything served, banks holding";
    }

    public static String nextSupplyChange(ReserveProjection.Result r) {
        if (r == null) {
            return null;
        }
        for (ReserveProjection.Milestone m : r.milestones()) {
            switch (m.kind()) {
                case SUN_UP -> {
                    return "Solar starts in about " + span(m.tick());
                }
                case SUN_DOWN -> {
                    return "Solar stops in about " + span(m.tick());
                }
                case FUEL_OUT -> {
                    return "Burners run dry in about " + span(m.tick());
                }
                default -> {
                }
            }
        }
        return null;
    }

    public static String milestone(ReserveProjection.Milestone m) {
        return switch (m.kind()) {
            case SHORT -> outputName(m.output()) + " starts going short, bank at " + m.socPct() + "%";
            case FLOOR -> outputName(m.output()) + " reaches its own floor (" + m.socPct() + "%)";
            case STANCE -> "Band: " + stanceName(m.value()) + " at " + m.socPct() + "%";
            case FULL -> "Banks full";
            case STORES_FULL -> "Starter Capacitors full";
            case SETTLES -> "Bank comes to rest near " + m.value() + "%";
            case SUN_UP -> "Sunrise: solar starts, up to " + fe(m.value()) + " FE/t";
            case SUN_DOWN -> "Sunset: solar stops, down from " + fe(m.value()) + " FE/t";
            case FUEL_OUT -> "Burners run dry: " + fe(m.value()) + " FE/t goes";
        };
    }

    public static String outputName(int index) {
        return index < 0 ? "an output" : "Output " + (index + 1);
    }

    private static String stanceName(int ordinal) {
        final FeReserveReport.Stance[] all = FeReserveReport.Stance.values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal].label() : "?";
    }
}
