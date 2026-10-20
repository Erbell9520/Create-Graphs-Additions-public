package com.createstats.surveyor;

import com.createstats.surveyor.core.RouteRunner;
import com.createstats.surveyor.core.Stop;
import com.createstats.surveyor.core.StopCondition;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RouteWords {
    private RouteWords() {
    }

    public static String rules(Stop stop) {
        if (stop == null) return "";
        List<String> alts = new ArrayList<>();
        for (List<StopCondition> alt : stop.rules()) {
            List<String> parts = new ArrayList<>();
            for (StopCondition c : alt) parts.add(condition(c));
            alts.add(String.join(" and ", parts));
        }
        String s = alts.isEmpty() ? "touch and go" : String.join(" or ", alts);
        if (stop.leaveAfter() > 0) s += ", leave after " + seconds(stop.leaveAfter());
        return s;
    }

    public static String condition(StopCondition c) {
        return switch (c) {
            case StopCondition.Wait w -> "wait " + seconds(w.seconds());
            case StopCondition.Idle i -> "cargo idle " + seconds(i.seconds());
            case StopCondition.Items i -> "cargo " + i.op().symbol + " " + i.amount() + " " + (i.stacks() ? "stacks" : "items")
                    + (i.item().isEmpty() ? "" : " of " + shortId(i.item()));
            case StopCondition.Fluid f -> "fluid " + f.op().symbol + " " + f.buckets() + " buckets"
                    + (f.fluid().isEmpty() ? "" : " of " + shortId(f.fluid()));
            case StopCondition.Link l -> "link " + (l.powered() ? "powered" : "unpowered");
            case StopCondition.TimeOfDay t -> String.format(java.util.Locale.ROOT, "at %d:%02d, %s", t.hour(), t.minute(),
                    rotation(t.rotation()));
            case StopCondition.Players pl -> pl.count() + (pl.orMore() ? " or more" : "") + " players seated";
            case StopCondition.Powered pw -> "the stop powered";
            case StopCondition.Docked d -> "docked";
        };
    }

    public static String rotation(int index) {
        return switch (index) {
            case 0 -> "every day";
            case 1 -> "every 12 h";
            case 2 -> "every 6 h";
            case 3 -> "every 4 h";
            case 4 -> "every 3 h";
            case 5 -> "every 2 h";
            case 6 -> "every hour";
            case 7 -> "every 45 min";
            case 8 -> "every 30 min";
            default -> "every 15 min";
        };
    }

    public static String seconds(int s) {
        if (s < 60) return s + " s";
        int m = s / 60;
        int r = s % 60;
        return r == 0 ? m + " min" : m + " min " + r + " s";
    }

    public static String shortId(String id) {
        int colon = id.indexOf(':');
        return (colon >= 0 ? id.substring(colon + 1) : id).replace('_', ' ');
    }

    public static String state(RouteRunner.State s) {
        return switch (s) {
            case CALIBRATING -> "Calibrating";
            case JOINING -> "Joining";
            case RUNNING -> "Running";
            case APPROACHING -> "Approaching stop";
            case WAITING -> "Waiting";
            case DEPARTING -> "Departing";
            case BLOCKED -> "Blocked";
            case STUCK -> "Stuck";
            case LOST -> "Lost";
            case PAUSED -> "Paused";
            case MANUAL -> "Manual";
            case RECORDING -> "Recording";
            case FINISHED -> "Finished";
            case NO_BUDGET -> "Waiting for a chunk budget";
            case HAZARD -> "Stopped short";
        };
    }

    // a clause you can act on
    public static String doing(RouteRunner.Mode mode, String subject) {
        String about = subject == null ? "" : subject;
        return switch (mode) {
            case EASE -> about.isEmpty() ? "easing off" : "easing off for " + about;
            case BUILD -> about.isEmpty() ? "picking up speed" : "picking up after " + about;
            case FOLLOW -> "keeping its distance";
            case APPROACH -> "slowing for " + (about.isEmpty() ? "the stop" : about);
            case STOPPING -> "stopping" + (about.isEmpty() ? "" : " at " + about);
            case REJOIN -> "steering back onto the line";
            case HAZARD -> about.isEmpty() ? "stopped short of something on the line" : "stopped short of " + about;
            case BLOCKED -> about.isEmpty() ? "waiting for the way to clear" : "waiting for " + about + " to move";
            case UNSTICK -> "trying to get free" + (about.isEmpty() ? "" : ": " + about);
            case REVERSE -> "backing off to try again";
            case LEAVE -> "pulling away";
            case CRUISE, HOLD, NONE -> "";
        };
    }

    public static String distance(double blocks) {
        long b = Math.max(0, Math.round(blocks));
        return b == 1 ? "a block" : b + " blocks";
    }

    public static String speed(double v) {
        return String.format(Locale.ROOT, "%.1f b/s", v);
    }
}
