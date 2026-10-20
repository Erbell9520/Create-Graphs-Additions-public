package com.createstats.aerial;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AerialText {
    private AerialText() {
    }

    public static List<String> lines(AirframeReadout r, List<AerialAlerts.Alert> alerts, int max) {
        final List<String> out = new ArrayList<>();
        final String name = r.snapshot.name().isBlank() ? "Aerial" : r.snapshot.name();
        out.add(name + "  ALT " + whole(r.altitude) + (Double.isFinite(r.agl) ? "  AGL " + whole(r.agl) : ""));

        final AerialSnapshot.Helm h = r.snapshot.helm();
        final Headline head = headline(r, alerts);
        if (head != null) {
            out.add(head.text());
        }

        if (h.known() && h.flying()) {
            out.add(HelmWords.order(h.order()) + (Double.isFinite(h.heldAltitude())
                    ? "  HOLD " + whole(h.heldAltitude()) : "") + (Double.isFinite(h.heldHeading())
                    ? String.format(Locale.ROOT, "  HDG %03d", Math.round(AirframeMath.wrap360(h.heldHeading())) % 360)
                    : ""));
        }
        out.add(String.format(Locale.ROOT, "IAS %.1f  GS %.1f  V/S %+.1f", r.indicatedAirspeed, r.groundSpeed,
                r.verticalSpeed) + (Double.isFinite(r.minHoldSpeed)
                ? String.format(Locale.ROOT, "  MIN %.1f", r.minHoldSpeed) : ""));
        final StringBuilder dir = new StringBuilder(String.format(Locale.ROOT, "HDG %03d  TRK %03d",
                Math.round(r.heading) % 360, Math.round(r.track) % 360));
        if (r.airMeasured && Double.isFinite(r.windSpeed) && Double.isFinite(r.windFrom)) {
            dir.append(String.format(Locale.ROOT, "  WIND %.1f FROM %03d", r.windSpeed, Math.round(r.windFrom) % 360));
        }
        out.add(dir.toString());
        if (Double.isFinite(r.reserve)) {
            out.add(String.format(Locale.ROOT, "LIFT %+.0f%%", r.reserve * 100.0)
                    + (Double.isFinite(r.ceilingY) ? "  CEILING Y " + whole(r.ceilingY) : ""));
        }
        if (r.nav != null) {
            out.add(String.format(Locale.ROOT, "NAV %s %s m", r.nav.label().isBlank() ? "target" : r.nav.label(),
                    whole(r.nav.distance())) + (Double.isFinite(r.nav.eta())
                    ? "  ETA " + AerialAlerts.duration(r.nav.eta()) : ""));
        }
        if (r.route != null) {
            out.add(String.format(Locale.ROOT, "ROUTE WP%d/%d  XTK %.1f %s", r.route.active(), r.route.count() - 1,
                    Math.abs(r.route.crossTrack()), r.route.crossTrack() >= 0.0 ? "R" : "L"));
        }
        if (Double.isFinite(r.enduranceSeconds)) {
            out.add("FUEL " + AerialAlerts.duration(r.enduranceSeconds));
        }
        return out.size() <= max ? out : out.subList(0, Math.max(0, max));
    }

    public record Headline(String text, AerialAlerts.Level level) {
    }

    public static Headline headline(AirframeReadout r, List<AerialAlerts.Alert> alerts) {
        for (final AerialAlerts.Alert a : alerts) {
            if (a.level() == AerialAlerts.Level.CRITICAL) {
                return new Headline(a.text(), a.level());
            }
        }
        final AerialSnapshot.Helm h = r.snapshot.helm();
        if (h.known() && !h.intent().isBlank()) {
            return new Headline(h.intent(), h.intentAlert() ? AerialAlerts.Level.CAUTION : null);
        }
        for (final AerialAlerts.Alert a : alerts) {
            if (a.level() == AerialAlerts.Level.CAUTION) {
                return new Headline(a.text(), a.level());
            }
        }
        return null;
    }

    private static String whole(double v) {
        return Double.isFinite(v) ? Long.toString(Math.round(v)) : "--";
    }
}
