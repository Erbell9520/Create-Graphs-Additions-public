package com.createstats.aerial;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.createstats.aerial.AirframeReadout.BalloonState;
import com.createstats.aerial.AirframeReadout.Corner;
import com.createstats.aerial.AirframeReadout.PropState;
import com.createstats.aerial.AirframeReadout.TrafficState;

public final class AerialAlerts {
    public enum Level {
        ADVISORY, CAUTION, CRITICAL
    }

    public enum Code {
        HELM, TERRAIN, SINK_RATE, LIFT_SHORT, LOW_SPEED, TRAFFIC, NO_FEED, LOW_FUEL, BALLOON, PROP_LAG, ASYMMETRY,
        CONTROL_LIMIT, CEILING, PROP_SATURATED, ROUTE, BALANCE, STAFF, GUARD, WEIGHING, NO_AIR_DATA
    }

    public record Alert(Code code, Level level, String text) {
    }

    public static final double STALE_SECONDS = 3.0;

    private AerialAlerts() {
    }

    public static List<Alert> evaluate(AirframeReadout r, Collection<Code> held, double feedAge) {
        final List<Alert> out = new ArrayList<>();
        final boolean flying = !r.landed;
        terrain(r, held, flying, out);
        sink(r, held, flying, out);
        lift(r, held, flying, out);
        lowSpeed(r, held, flying, out);
        traffic(r, held, out);
        feed(held, feedAge, out);
        fuel(r, held, out);
        balloons(r, held, out);
        props(r, out);
        asymmetry(r, held, out);
        control(r, held, out);
        ceiling(r, out);
        saturated(r, flying, out);
        route(r, out);
        balance(r, held, out);
        helm(r, out);
        if (!r.airMeasured) {
            out.add(new Alert(Code.NO_AIR_DATA, Level.ADVISORY, "NO AIR DATA  speeds are over the ground"));
        }
        out.sort(Comparator.comparing((Alert a) -> a.level()).reversed().thenComparing(a -> a.code()));
        return out;
    }

    public static List<Code> codes(List<Alert> alerts) {
        final List<Code> out = new ArrayList<>(alerts.size());
        for (final Alert a : alerts) {
            out.add(a.code());
        }
        return out;
    }

    public static Level worst(List<Alert> alerts) {
        return alerts.isEmpty() ? null : alerts.get(0).level();
    }

    static void terrain(AirframeReadout r, Collection<Code> held, boolean flying, List<Alert> out) {
        if (!flying) {
            return;
        }
        final boolean was = held.contains(Code.TERRAIN);
        final double impact = r.terrain.impactSeconds();
        if (Double.isFinite(impact)) {
            final Level level = impact <= (was ? 7.0 : 5.0) ? Level.CRITICAL : Level.CAUTION;
            out.add(new Alert(Code.TERRAIN, level, String.format(Locale.ROOT, "TERRAIN  ground in %.0f s", impact)));
            return;
        }
        final double clear = r.terrain.minClearance();

        final boolean nearestNow = r.terrain.atSeconds() <= AirframeMath.PREDICT_STEP + 1.0e-9;
        final boolean climbingAway = r.verticalSpeed > 0.3 && nearestNow;

        final AerialSnapshot.Helm h = r.snapshot.helm();
        final boolean holdingLow = nearestNow && h.known() && h.flying() && !h.liftShort()
                && Double.isFinite(h.flownAltitude()) && Math.abs(r.altitude - h.flownAltitude()) < 1.5;
        if (climbingAway || holdingLow || commandedDescent(r)) {
            return;
        }
        if (Double.isFinite(clear) && clear < (was ? 5.0 : 3.0)) {
            out.add(new Alert(Code.TERRAIN, Level.CAUTION,
                    String.format(Locale.ROOT, "TERRAIN  %.0f m clear in %.0f s", clear, r.terrain.atSeconds())));
        }
    }

    static double descentBrake(String feel) {
        return switch (feel == null ? "" : feel) {
            case "GENTLE" -> 0.25;
            case "SPORTY" -> 0.6;
            default -> 0.4;
        };
    }

    static boolean commandedDescent(AirframeReadout r) {
        final AerialSnapshot.Helm h = r.snapshot.helm();
        if (!h.known() || !h.flying() || h.liftShort() || !Double.isFinite(h.flownAltitude())) {
            return false;
        }
        final double room = r.altitude - h.flownAltitude();
        final double stop = r.verticalSpeed * r.verticalSpeed / (2.0 * descentBrake(h.feel()));
        return room > -0.5 && stop <= room + 1.0;
    }

    static void sink(AirframeReadout r, Collection<Code> held, boolean flying, List<Alert> out) {
        if (!flying || !(r.verticalSpeed < -0.5) || !Double.isFinite(r.agl) || commandedDescent(r)) {
            return;
        }
        final boolean was = held.contains(Code.SINK_RATE);
        final double t = Math.max(0.0, r.agl) / -r.verticalSpeed;
        final Level level;
        if (t < (was ? 7.0 : 5.0)) {
            level = Level.CRITICAL;
        } else if (t < (was ? 15.0 : 12.0)) {
            level = Level.CAUTION;
        } else {
            return;
        }
        out.add(new Alert(Code.SINK_RATE, level,
                String.format(Locale.ROOT, "SINK RATE  %.1f m/s, ground in %.0f s", r.verticalSpeed, t)));
    }

    static void lift(AirframeReadout r, Collection<Code> held, boolean flying, List<Alert> out) {
        if (!flying || !Double.isFinite(r.support)) {
            return;
        }
        final boolean was = held.contains(Code.LIFT_SHORT);
        final String text = String.format(Locale.ROOT, "LIFT SHORT  %.0f%% of weight carried", r.support * 100.0);
        if (r.support < (was ? 0.93 : 0.90)) {
            out.add(new Alert(Code.LIFT_SHORT, Level.CRITICAL, text));
        } else if (r.support < (was ? 1.0 : 0.98) && r.verticalSpeed < -0.2 && !commandedDescent(r)) {
            out.add(new Alert(Code.LIFT_SHORT, Level.CAUTION, text));
        }
    }

    static void lowSpeed(AirframeReadout r, Collection<Code> held, boolean flying, List<Alert> out) {
        final double min = r.minHoldSpeed;
        if (!flying || !Double.isFinite(min)) {
            return;
        }
        final double v = r.snapshot.air().velocity().horizontalLength();
        if (v >= min * (held.contains(Code.LOW_SPEED) ? 1.15 : 1.10)) {
            return;
        }
        out.add(new Alert(Code.LOW_SPEED, Level.CAUTION, Double.isFinite(r.topSpeed) && r.topSpeed < min
                ? String.format(Locale.ROOT, "LOW SPEED  sails need %.1f m/s, top speed %.1f", min, r.topSpeed)
                : String.format(Locale.ROOT, "LOW SPEED  %.1f m/s, sails hold height from %.1f", v, min)));
    }

    static void traffic(AirframeReadout r, Collection<Code> held, List<Alert> out) {
        TrafficState worst = null;
        for (final TrafficState t : r.traffic) {
            if (t.conflict() && (worst == null || t.cpaTime() < worst.cpaTime())) {
                worst = t;
            }
        }
        if (worst == null) {
            return;
        }
        final boolean was = held.contains(Code.TRAFFIC);
        final Level level = worst.cpaTime() <= (was ? 13.0 : 10.0) ? Level.CRITICAL : Level.CAUTION;
        out.add(new Alert(Code.TRAFFIC, level, String.format(Locale.ROOT, "TRAFFIC  %s, %.0f m in %.0f s",
                name(worst.traffic().name()), worst.cpaDistance(), worst.cpaTime())));
    }

    static void feed(Collection<Code> held, double age, List<Alert> out) {
        if (age > (held.contains(Code.NO_FEED) ? STALE_SECONDS - 1.0 : STALE_SECONDS)) {
            out.add(new Alert(Code.NO_FEED, Level.CAUTION,
                    String.format(Locale.ROOT, "NO FEED  %.0f s since the Aerial reported", age)));
        }
    }

    static void fuel(AirframeReadout r, Collection<Code> held, List<Alert> out) {
        final double s = r.enduranceSeconds;
        if (!Double.isFinite(s)) {
            return;
        }
        final boolean was = held.contains(Code.LOW_FUEL);
        final String text = String.format(Locale.ROOT, "LOW FUEL  %s left", duration(s));
        if (s < (was ? 36.0 : 30.0)) {
            out.add(new Alert(Code.LOW_FUEL, Level.CRITICAL, text));
        } else if (s < (was ? 144.0 : 120.0)) {
            out.add(new Alert(Code.LOW_FUEL, Level.CAUTION, text));
        }
    }

    static void balloons(AirframeReadout r, Collection<Code> held, List<Alert> out) {
        final boolean was = held.contains(Code.BALLOON);
        BalloonState worst = null;
        for (final BalloonState b : r.balloons) {
            if (!Double.isFinite(b.fill())) {
                continue;
            }
            if (b.starved() || b.fill() < (was ? 0.85 : 0.80)) {
                if (worst == null || (b.starved() && !worst.starved()) || b.fill() < worst.fill()) {
                    worst = b;
                }
            }
        }
        if (worst == null) {
            return;
        }
        final String name = name(worst.balloon().name());
        if (worst.starved()) {
            out.add(new Alert(Code.BALLOON, Level.CAUTION, String.format(Locale.ROOT, worst.burnersOut()
                    ? "BALLOON  %s at %.0f%%, burners out" : "BALLOON  %s at %.0f%% and not filling", name,
                    worst.fill() * 100.0)));
        } else {
            out.add(new Alert(Code.BALLOON, Level.ADVISORY, String.format(Locale.ROOT,
                    "BALLOON  %s at %.0f%%, full in %s", name, worst.fill() * 100.0, duration(worst.timeToTarget()))));
        }
    }

    static void props(AirframeReadout r, List<Alert> out) {
        PropState worst = null;
        for (final PropState p : r.props) {
            if (p.lagging() && (worst == null || p.prop().rpm() < worst.prop().rpm())) {
                worst = p;
            }
        }
        if (worst == null) {
            return;
        }
        final String name = name(worst.prop().name());
        if (worst.overloaded()) {
            out.add(new Alert(Code.PROP_LAG, Level.CAUTION, String.format(Locale.ROOT,
                    "PROP LAG  %s, its shaft at %.0f%% stress", name, worst.prop().shaftStress() * 100.0)));
        } else {
            out.add(new Alert(Code.PROP_LAG, Level.ADVISORY, String.format(Locale.ROOT,
                    "PROP LAG  %s at %.0f RPM", name, worst.prop().rpm())));
        }
    }

    static void asymmetry(AirframeReadout r, Collection<Code> held, List<Alert> out) {
        if (r.asymmetry > (held.contains(Code.ASYMMETRY) ? 0.10 : 0.15)) {
            out.add(new Alert(Code.ASYMMETRY, Level.CAUTION, "ASYMMETRY  thrust yaws the ship "
                    + (r.yawTorque > 0.0 ? "right" : "left")));
        }
    }

    static void control(AirframeReadout r, Collection<Code> held, List<Alert> out) {
        if (!Double.isFinite(r.maxAuthority) || !r.snapshot.autopilot().engaged()) {
            return;
        }
        if (r.maxAuthority > (held.contains(Code.CONTROL_LIMIT) ? 0.90 : 0.95)) {
            out.add(new Alert(Code.CONTROL_LIMIT, Level.CAUTION, String.format(Locale.ROOT,
                    "CONTROL LIMIT  %s at %.0f%%", axisAtLimit(r.snapshot.autopilot()), r.maxAuthority * 100.0)));
        }
    }

    static void ceiling(AirframeReadout r, List<Alert> out) {
        final AerialSnapshot.Helm h = r.snapshot.helm();
        if (h.known() && h.flying()) {
            if (Double.isFinite(h.heldAltitude()) && Double.isFinite(h.ceiling()) && h.heldAltitude() > h.ceiling() + 1.0) {
                out.add(new Alert(Code.CEILING, Level.CAUTION, String.format(Locale.ROOT,
                        "CEILING  Y %.0f (%s), height held Y %.0f is above it", h.ceiling(), HelmWords.ceilingReason(h),
                        h.heldAltitude())));
            }
            return;
        }
        final double target = r.snapshot.autopilot().targetAltitude();
        if (Double.isFinite(target) && Double.isFinite(r.ceilingY) && r.reserve >= 0.0 && target > r.ceilingY + 1.0) {
            out.add(new Alert(Code.CEILING, Level.CAUTION, String.format(Locale.ROOT,
                    "CEILING  Y %.0f, target Y %.0f is above it", r.ceilingY, target)));
        }
    }

    static void saturated(AirframeReadout r, boolean flying, List<Alert> out) {
        if (!flying) {
            return;
        }
        int n = 0;
        for (final PropState p : r.props) {
            if (p.saturated()) {
                n++;
            }
        }
        if (n > 0) {
            out.add(new Alert(Code.PROP_SATURATED, Level.ADVISORY, String.format(Locale.ROOT,
                    "PROPS  %d out of airflow at %.1f m/s", n, r.airspeed)));
        }
    }

    static void route(AirframeReadout r, List<Alert> out) {
        if (r.route == null) {
            return;
        }
        for (final Corner c : r.route.corners()) {
            if (c.feasible()) {
                continue;
            }
            out.add(new Alert(Code.ROUTE, Level.ADVISORY, Double.isFinite(c.suggestedSpeed())
                    ? String.format(Locale.ROOT, "ROUTE  WP%d needs R %.0f m, slow to %.1f m/s", c.index(),
                    c.allowedRadius(), c.suggestedSpeed())
                    : String.format(Locale.ROOT, "ROUTE  WP%d needs R %.0f m, tighter than any turn flown",
                    c.index(), c.allowedRadius())));
            return;
        }
    }

    static void balance(AirframeReadout r, Collection<Code> held, List<Alert> out) {
        final double length = r.snapshot.silhouette().isEmpty() ? 16.0 : r.snapshot.silhouette().length();
        if (!Double.isFinite(r.balanceArm)) {
            return;
        }
        if (Math.abs(r.balanceArm) > length * (held.contains(Code.BALANCE) ? 0.06 : 0.08)) {
            out.add(new Alert(Code.BALANCE, Level.ADVISORY, String.format(Locale.ROOT,
                    "BALANCE  centre of mass %.1f m %s of the lift", Math.abs(r.balanceArm),
                    r.balanceArm > 0.0 ? "aft" : "ahead")));
        }
    }

    static void helm(AirframeReadout r, List<Alert> out) {
        final AerialSnapshot.Helm h = r.snapshot.helm();
        if (!h.known()) {
            return;
        }

        final boolean guardFlies = h.guard().acting() && h.unattended();
        String warning = null;
        if (h.intentAlert() && h.staff() == 0 && !guardFlies && !h.intent().isBlank()) {
            warning = h.intent();
        } else {
            for (final String n : h.notes()) {
                if (guardFlies && (n.startsWith("Terrain ahead") || n.startsWith("Unloaded ground"))) {
                    continue;
                }
                warning = n;
                break;
            }
        }
        if (warning != null) {
            final String text = noteText(warning);

            boolean said = false;
            for (final Alert a : out) {
                said |= head(a.text()).equals(head(text));
            }
            if (!said) {
                out.add(new Alert(Code.HELM, Level.CAUTION, text));
            }
        }
        if (h.staff() > 0) {
            out.add(new Alert(Code.STAFF, Level.ADVISORY, h.staff() == 2 ? "STAFF  pinned in place by a physics staff"
                    : "STAFF  held by a physics staff: standing by"));
        }
        if (guardFlies) {
            out.add(new Alert(Code.GUARD, Level.ADVISORY, "GUARD  " + HelmWords.guard(h.guard())
                    .replace("Terrain guard: ", "")));
        }
        if (h.weighing()) {
            out.add(new Alert(Code.WEIGHING, Level.ADVISORY, "WEIGHING OFF  held on the ground while the gas rises"));
        }
    }

    static String noteText(String note) {
        final String n = note.strip();
        final int colon = n.indexOf(": ");
        if (colon <= 0) {
            return n.toUpperCase(Locale.ROOT);
        }
        final String head = n.substring(0, colon).toUpperCase(Locale.ROOT);
        String rest = n.substring(colon + 2);
        if (rest.equals(rest.toUpperCase(Locale.ROOT)) && !rest.isEmpty()) {
            rest = sentenceCase(rest);
        }
        return head + "  " + rest;
    }

    static String sentenceCase(String s) {
        final StringBuilder b = new StringBuilder(s.toLowerCase(Locale.ROOT));
        boolean start = true;
        for (int i = 0; i < b.length(); i++) {
            final char c = b.charAt(i);
            if (start && Character.isLetter(c)) {
                b.setCharAt(i, Character.toUpperCase(c));
                start = false;
            } else if (c == '.' || c == '!' || c == '?') {
                start = true;
            }
        }
        return b.toString();
    }

    static String head(String text) {
        final int cut = text.indexOf("  ");
        return cut > 0 ? text.substring(0, cut) : text;
    }

    static String axisAtLimit(AerialSnapshot.Autopilot a) {
        final String[] names = {"throttle", "yaw", "pitch", "lift"};
        final double[] values = {a.authThrottle(), a.authYaw(), a.authPitch(), a.authLift()};
        int best = -1;
        for (int i = 0; i < values.length; i++) {
            if (Double.isFinite(values[i]) && (best < 0 || values[i] > values[best])) {
                best = i;
            }
        }
        return best < 0 ? "control" : names[best];
    }

    static String name(String s) {
        return s == null || s.isBlank() ? "unnamed" : s;
    }

    public static String duration(double seconds) {
        if (!Double.isFinite(seconds)) {
            return "--";
        }
        final long s = Math.max(0L, Math.round(seconds));
        if (s >= 600) {
            return (s / 60) + " min";
        }
        if (s >= 60) {
            return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
        }
        return s + " s";
    }
}
