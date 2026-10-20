package com.createstats.ground;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.createstats.ground.DriveReadout.UnitRead;
import com.createstats.ground.GroundSnapshot.Unit;

public final class GroundText {
    private GroundText() {
    }

    public static List<String> lines(DriveReadout r, List<GroundAlerts.Alert> alerts, int max) {
        final GroundSnapshot s = r.snapshot;
        final List<String> out = new ArrayList<>();
        final String name = s.identity().name().isBlank() ? "Vehicle" : s.identity().name();
        out.add(name + "  " + headline(r));
        final GroundAlerts.Alert warn = GroundAlerts.headline(alerts);
        if (warn != null) {
            out.add(warn.text());
        }
        if (r.state == DriveReadout.State.DRIVING) {
            final StringBuilder d = new StringBuilder(String.format(Locale.ROOT, "DRV %+.0f%%  STR %+.0f%%",
                    s.drive().throttle() * 100.0, s.drive().steer() * 100.0));
            if (s.drive().braking()) {
                d.append("  BRK");
            }
            if (s.drive().boosting()) {
                d.append("  BST");
            }
            if (!s.drive().driver().isBlank()) {
                d.append("  ").append(s.drive().driver());
            }
            out.add(d.toString());
        }
        // round(NaN) is 0, an unread heading would say due north

        if (r.state != DriveReadout.State.OFF_CRAFT) {
            out.add("HDG " + (Double.isFinite(r.heading) ? String.format(Locale.ROOT, "%03d", Math.round(r.heading) % 360) : "--")
                    + "  Y " + (Double.isFinite(r.altitude) ? Long.toString(Math.round(r.altitude)) : "--")
                    + "  GRADE " + (Double.isFinite(r.grade) ? String.format(Locale.ROOT, "%+.0f%%", r.grade) : "--"));
        }
        if (r.trailers > 0) {
            out.add(train(r));
        }
        out.add("ODO " + distance(s.odometer().odometer()) + "  TRIP " + distance(s.odometer().trip()));
        final StringBuilder speeds = new StringBuilder();
        if (Double.isFinite(s.odometer().topSpeed())) {
            speeds.append(String.format(Locale.ROOT, "TOP %.0f km/h", s.odometer().topSpeed() * DriveMath.KMH));
        }
        if (r.model.known()) {
            speeds.append(speeds.length() > 0 ? "  " : "").append(String.format(Locale.ROOT, "CRUISE %.0f km/h model",
                    r.model.cruise() * DriveMath.KMH));
        }
        if (speeds.length() > 0) {
            out.add(speeds.toString());
        }
        if (!s.problems().isEmpty()) {
            out.add(s.problems().size() == 1 ? "1 problem: " + s.problems().get(0)
                    : s.problems().size() + " problems: " + s.problems().get(0));
        }
        return out.size() <= max ? out : out.subList(0, Math.max(0, max));
    }

    static String headline(DriveReadout r) {
        final GroundSnapshot s = r.snapshot;
        return switch (r.state) {
            case DRIVING -> String.format(Locale.ROOT, "%.0f km/h  %c", r.speedKmh, s.drive().gear());
            case TOWED -> "towed" + (s.identity().towedBy().isBlank() ? "" : " by " + s.identity().towedBy())
                    + String.format(Locale.ROOT, "  %.0f km/h", r.speedKmh);
            case PARKED -> "parked";
            case RUNAWAY -> String.format(Locale.ROOT, "ROLLING %.0f km/h", r.groundSpeed * DriveMath.KMH);
            case OFF_CRAFT -> "not on a vehicle";
            default -> "standing";
        };
    }

    public static String train(DriveReadout r) {
        final StringBuilder b = new StringBuilder("TOWING ").append(r.trailers).append(':');
        boolean first = true;
        for (final UnitRead ur : r.units) {
            final Unit u = ur.unit();
            if (u.isTractor() || !u.role().takesMode()) {
                continue;
            }
            b.append(first ? " " : ", ").append(u.name()).append(' ').append(linkWords(ur));
            first = false;
        }
        return b.toString();
    }

    public static String linkWords(UnitRead ur) {
        final Unit u = ur.unit();
        return switch (ur.link()) {
            case GONE -> u.link().key() + " gone";
            case SLACK -> String.format(Locale.ROOT, "rope %.1f slack", -u.reading());
            case TAUT -> "rope taut";
            case STRETCHED -> u.link() == com.createstats.vccmk4.core.LinkKind.SPRING
                    ? (Float.isFinite(u.snapShare()) ? String.format(Locale.ROOT, "springs %.0f%%", u.snapShare() * 100.0)
                    : String.format(Locale.ROOT, "springs +%.1f", u.reading()))
                    : String.format(Locale.ROOT, "rope +%.1f", u.reading());
            case NEAR_SNAP -> String.format(Locale.ROOT, "springs %.0f%% NEAR SNAP",
                    Float.isFinite(u.snapShare()) ? u.snapShare() * 100.0 : 70.0);
            case HITCHED -> Float.isFinite(u.articulation())
                    ? String.format(Locale.ROOT, "hitch %.0f %s", Math.abs(u.articulation()), u.articulation() >= 0 ? "R" : "L")
                    : "hitch";
            case DOCKING -> "docking";
            case DOCKED -> "docked";
            case RIGID -> "locked joint";
        };
    }

    public static String distance(double blocks) {
        if (!Double.isFinite(blocks)) {
            return "--";
        }
        if (blocks < 1000.0) {
            return Math.round(blocks) + " b";
        }
        return String.format(Locale.ROOT, blocks < 100_000.0 ? "%.1f km" : "%.0f km", blocks / 1000.0);
    }

    public static String duration(double seconds) {
        if (!Double.isFinite(seconds)) {
            return "--";
        }
        final long t = Math.max(0L, Math.round(seconds));
        if (t >= 600) {
            return (t / 60) + " min";
        }
        if (t >= 60) {
            return String.format(Locale.ROOT, "%d:%02d", t / 60, t % 60);
        }
        return t + " s";
    }

    public static String prettyId(String id) {
        if (id == null || id.isBlank()) {
            return "";
        }
        final int dot = id.lastIndexOf('.');
        final String tail = dot >= 0 ? id.substring(dot + 1) : id;
        final StringBuilder b = new StringBuilder(tail.length());
        boolean up = true;
        for (final char c : tail.toCharArray()) {
            if (c == '_') {
                b.append(' ');
                up = true;
            } else {
                b.append(up ? Character.toUpperCase(c) : c);
                up = false;
            }
        }
        return b.toString();
    }
}
