package com.createstats.ground;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.createstats.driverhelmet.DriveLog;
import com.createstats.driverhelmet.LedBar;
import com.createstats.ground.GroundSnapshot.Governor;
import com.createstats.ground.GroundSnapshot.Jet;
import com.createstats.ground.GroundSnapshot.Supply;
import com.createstats.ground.GroundSnapshot.Unit;
import com.createstats.vccmk4.core.JetSupply;
import com.createstats.vccmk4.core.LinkTrim;

// most severe first
public final class GroundAlerts {
    public enum Level {
        ADVISORY, CAUTION, CRITICAL
    }

    public enum Code {
        NO_FEED, LINK, RUNAWAY, SNAP, SWAY, STRAIN, BREAKAWAY, IMPACT, SUPPLY, FOREIGN, BRAKES, ROLLOVER, FUEL,
        AIRBORNE, WHEELSPIN, LOCKUP, OVERRUN, PROBLEMS
    }

    public record Alert(Code code, Level level, String text) {
        public String name() {
            final int cut = this.text.indexOf("  ");
            return cut > 0 ? this.text.substring(0, cut) : this.text;
        }

        public String detail() {
            final int cut = this.text.indexOf("  ");
            return cut > 0 ? this.text.substring(cut + 2) : "";
        }
    }

    public static final double STALE_SECONDS = 3.0;

    public static final int IMPACT_TICKS = 80;

    public static final double SNAP_CRITICAL = 0.9;

    public static final double ROLLOVER_RAISE = 0.8;
    public static final double ROLLOVER_CLEAR = 0.7;

    private GroundAlerts() {
    }

    public static List<Alert> evaluate(DriveReadout r, Collection<Code> held, double feedAge) {
        final List<Alert> out = new ArrayList<>();
        feed(held, feedAge, out);
        link(r, out);
        runaway(r, held, out);
        snap(r, held, out);
        sway(r, out);
        breakaway(r, out);
        impact(r, out);
        supply(r, out);
        foreign(r, out);
        brakes(r, out);
        rollover(r, held, out);
        fuel(r, out);
        airborne(r, out);
        wheels(r, held, out);
        overrun(r, out);
        problems(r, out);
        out.sort(Comparator.comparing((Alert a) -> a.level()).reversed().thenComparing(Alert::code));
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

    public static Alert headline(List<Alert> alerts) {
        for (final Alert a : alerts) {
            if (a.level() != Level.ADVISORY) {
                return a;
            }
        }
        return null;
    }

    static void feed(Collection<Code> held, double age, List<Alert> out) {
        if (age > (held.contains(Code.NO_FEED) ? STALE_SECONDS - 1.0 : STALE_SECONDS)) {
            out.add(new Alert(Code.NO_FEED, Level.CAUTION,
                    String.format(Locale.ROOT, "NO FEED  %.0f s since the vehicle reported", Math.min(999.0, age))));
        }
    }

    static void link(DriveReadout r, List<Alert> out) {
        for (final Unit u : r.snapshot.units()) {
            if (!u.isTractor() && u.has(Unit.FLAG_LOST)) {
                out.add(new Alert(Code.LINK, Level.CRITICAL, "LINK  " + u.name() + "'s " + u.link().noun()
                        + " has gone, its brakes are held"));
                return;
            }
        }
    }

    static void runaway(DriveReadout r, Collection<Code> held, List<Alert> out) {
        if (r.state == DriveReadout.State.DRIVING || r.state == DriveReadout.State.TOWED
                || r.state == DriveReadout.State.OFF_CRAFT) {
            return;
        }
        final double limit = held.contains(Code.RUNAWAY) ? DriveMath.RUNAWAY_SPEED * 0.5 : DriveMath.RUNAWAY_SPEED;
        if (r.groundSpeed > limit) {
            out.add(new Alert(Code.RUNAWAY, Level.CRITICAL, String.format(Locale.ROOT,
                    "RUNAWAY  moving at %.1f b/s with nobody at the helm", r.groundSpeed)));
        }
    }

    static void snap(DriveReadout r, Collection<Code> held, List<Alert> out) {
        Unit worst = null;
        for (final Unit u : r.snapshot.units()) {
            if (Float.isFinite(u.snapShare()) && (worst == null || u.snapShare() > worst.snapShare())) {
                worst = u;
            }
        }
        if (worst == null) {
            return;
        }
        final double share = worst.snapShare();
        final double critical = held.contains(Code.SNAP) ? SNAP_CRITICAL - 0.05 : SNAP_CRITICAL;
        final double caution = held.contains(Code.STRAIN) ? LinkTrim.SNAP_WARN - 0.05 : LinkTrim.SNAP_WARN;
        final String text = String.format(Locale.ROOT, "%s's springs at %.0f%% of their breaking length",
                worst.name(), share * 100.0);
        if (share >= critical) {
            out.add(new Alert(Code.SNAP, Level.CRITICAL, "SNAP  " + text));
        } else if (share >= caution || worst.has(Unit.FLAG_SNAPPING)) {
            out.add(new Alert(Code.STRAIN, Level.CAUTION, "STRAIN  " + text));
        }
    }

    static void sway(DriveReadout r, List<Alert> out) {
        for (final Unit u : r.snapshot.units()) {
            if (!u.isTractor() && u.has(Unit.FLAG_SWAYING)) {
                out.add(new Alert(Code.SWAY, Level.CAUTION, "SWAY  " + u.name() + " is swinging, Assist is braking it"));
                return;
            }
        }
    }

    static void breakaway(DriveReadout r, List<Alert> out) {
        if (r.snapshot.drive().alert().startsWith("BREAKAWAY")) {
            out.add(new Alert(Code.BREAKAWAY, Level.CAUTION, "BREAKAWAY  a trailer came loose, its brakes hold it"));
        }
    }

    static void impact(DriveReadout r, List<Alert> out) {
        final GroundSnapshot.Log log = r.snapshot.log();
        if (log.impactAge() >= 0 && log.impactAge() <= IMPACT_TICKS && log.lastImpact() >= DriveLog.IMPACT_G) {
            out.add(new Alert(Code.IMPACT, Level.CAUTION, String.format(Locale.ROOT, "IMPACT  %.1f g", log.lastImpact())));
        }
    }

    static void supply(DriveReadout r, List<Alert> out) {
        for (final Governor g : r.snapshot.governors()) {
            if (!g.starved()) {
                continue;
            }
            out.add(new Alert(Code.SUPPLY, Level.CAUTION, g.supply() == Supply.OVERSTRESSED
                    ? "SUPPLY  " + g.name() + "'s shafts are overstressed"
                    : "SUPPLY  " + g.name() + " gets no rotation from its engine"));
            return;
        }
    }

    static void foreign(DriveReadout r, List<Alert> out) {
        if (r.state != DriveReadout.State.DRIVING) {
            return;
        }
        for (final Governor g : r.snapshot.governors()) {
            if (g.supply() == Supply.FOREIGN) {
                out.add(new Alert(Code.FOREIGN, Level.CAUTION, "FOREIGN  " + g.name() + " is held by another controller"));
                return;
            }
        }
        for (final Jet j : r.snapshot.jets()) {
            if (j.foreign()) {
                out.add(new Alert(Code.FOREIGN, Level.CAUTION, "FOREIGN  " + j.name() + " is held by another console"));
                return;
            }
        }
    }

    static void brakes(DriveReadout r, List<Alert> out) {
        if (!r.snapshot.identity().brakesAvailable() && r.snapshot.identity().onCraft()) {
            out.add(new Alert(Code.BRAKES, Level.CAUTION, "BRAKES  unavailable: the wheel brake hook did not load"));
        }
    }

    static void rollover(DriveReadout r, Collection<Code> held, List<Alert> out) {
        if (!Double.isFinite(r.rolloverShare) || r.groundSpeed < DriveMath.RUNAWAY_SPEED || r.airborne) {
            return;
        }
        if (r.rolloverShare >= (held.contains(Code.ROLLOVER) ? ROLLOVER_CLEAR : ROLLOVER_RAISE)) {
            out.add(new Alert(Code.ROLLOVER, Level.CAUTION, String.format(Locale.ROOT,
                    "ROLLOVER  %.2f g of the %.2f g it tips at (modelled)", Math.abs(r.latG), r.stability)));
        }
    }

    static void fuel(DriveReadout r, List<Alert> out) {
        for (final Jet j : r.snapshot.jets()) {
            if (j.supply() == JetSupply.EMPTY) {
                out.add(new Alert(Code.FUEL, Level.CAUTION, "FUEL  " + j.name() + " has nothing it can burn"));
                return;
            }
            if (j.supply() == JetSupply.UNPOWERED) {
                out.add(new Alert(Code.FUEL, Level.CAUTION, "FUEL  " + j.name() + " has no energy"));
                return;
            }
        }
        for (final Jet j : r.snapshot.jets()) {
            if (j.supply() == JetSupply.LOW) {
                out.add(new Alert(Code.FUEL, Level.ADVISORY, "FUEL  " + j.name() + " is nearly out"));
                return;
            }
        }
    }

    static void airborne(DriveReadout r, List<Alert> out) {
        final double air = r.snapshot.log().airNow();
        if (r.airborne && air >= DriveLog.MIN_JUMP_TICKS / 20.0) {
            out.add(new Alert(Code.AIRBORNE, Level.ADVISORY, String.format(Locale.ROOT, "AIRBORNE  %.1f s", air)));
        }
    }

    static void wheels(DriveReadout r, Collection<Code> held, List<Alert> out) {
        final double throttle = r.snapshot.drive().throttle();
        final boolean braking = r.snapshot.drive().braking();
        final double spinAt = held.contains(Code.WHEELSPIN) ? LedBar.WARN_SLIP - 0.05 : LedBar.WARN_SLIP;
        final double lockAt = held.contains(Code.LOCKUP) ? LedBar.WARN_SLIP - 0.05 : LedBar.WARN_SLIP;
        double spin = 0.0;
        double lock = 0.0;
        for (final DriveReadout.WheelRead w : r.wheels) {
            final float slip = w.wheel().slip();
            if (!w.touching() || !Float.isFinite(slip)) {
                continue;
            }
            if (w.wheel().driven() && Math.abs(throttle) > 0.05 && slip > spin) {
                spin = slip;
            }
            if (braking && -slip > lock) {
                lock = -slip;
            }
        }
        if (spin > spinAt) {
            out.add(new Alert(Code.WHEELSPIN, Level.ADVISORY, String.format(Locale.ROOT, "WHEELSPIN  %.0f%% slip",
                    spin * 100.0)));
        }
        if (lock > lockAt) {
            out.add(new Alert(Code.LOCKUP, Level.ADVISORY, String.format(Locale.ROOT, "LOCK-UP  %.0f%% slip",
                    lock * 100.0)));
        }
    }

    static void overrun(DriveReadout r, List<Alert> out) {
        for (final Unit u : r.snapshot.units()) {
            if (!u.isTractor() && u.has(Unit.FLAG_OVERRUN)) {
                out.add(new Alert(Code.OVERRUN, Level.ADVISORY, "OVERRUN  " + u.name() + " pushes while you brake"));
                return;
            }
        }
    }

    static void problems(DriveReadout r, List<Alert> out) {
        final List<String> p = r.snapshot.problems();
        if (!p.isEmpty()) {
            out.add(new Alert(Code.PROBLEMS, Level.ADVISORY, (p.size() == 1 ? "1 PROBLEM  " : p.size() + " PROBLEMS  ")
                    + p.get(0)));
        }
    }
}
