package com.createstats.aerial;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.createstats.aerial.AerialSnapshot.Envelope;
import com.createstats.aerial.AerialSnapshot.Fix;
import com.createstats.aerial.AerialSnapshot.History;
import com.createstats.aerial.AerialSnapshot.Route;
import com.createstats.aerial.AerialSnapshot.Trail;
import com.createstats.aerial.AerialSnapshot.Waypoint;

public final class AerialRecorder {
    public static final int SAMPLE_TICKS = 4;

    public static final int TRAIL_TICKS = 20;
    public static final double TRAIL_METRES = 4.0;
    public static final double TRAIL_TURN_DEGREES = 12.0;

    public static final int HISTORY_TICKS = 40;
    public static final double BUCKET_WIDTH = 2.0;

    static final double LEVEL = 0.5;

    static final double HOLD_SPEED = 3.0;
    static final double HOLD_RATE = 1.0;
    static final double HOLD_TURN = 2.0;

    static final double MIN_TURN = 2.0;

    public static final int ROUTE_POINTS = 64;

    private final List<Fix> fixes = new ArrayList<>();
    private double lastFixTrack = Double.NaN;

    private final float[][] history = new float[5][History.MAX_SAMPLES];
    private int historyCount;
    private long lastHistoryTick = Long.MIN_VALUE;

    private final ArrayDeque<Sample> window = new ArrayDeque<>();
    private long lastSampleTick = Long.MIN_VALUE;

    private double seconds;
    private double topSpeed = Double.NaN;
    private double bestClimb = Double.NaN;
    private double maxSink = Double.NaN;
    private double maxYawRate = Double.NaN;
    private double maxAccel = Double.NaN;
    private double maxDecel = Double.NaN;
    private final float[] turn = AerialSnapshot.nanArray(Envelope.BUCKETS);

    private record Sample(long tick, double airspeed, double verticalSpeed, double groundSpeed, double turnRate,
                          boolean airborne) {
    }

    static final int AIRBORNE_SAMPLES = 3;

    static final int LANDED_SAMPLES = 10;

    static final int TRACE_TICKS = 20;

    static final long PRE_FLIGHT_TICKS = 1200L;

    static final double TELEPORT = 64.0;

    static final double ARRIVED = 24.0;

    private long takeoff = AerialSnapshot.NO_TICK;
    private double voyageSeconds;
    private double voyageDistance;
    private double highest = Double.NaN;
    private int tracePeriod = TRACE_TICKS;
    private long traceLast = Long.MIN_VALUE;
    private final float[] trace = new float[AerialSnapshot.Voyage.MAX_TRACE];
    private int traceCount;
    private final List<AerialSnapshot.Event> events = new ArrayList<>();
    private AerialSnapshot.Leg lastLeg;
    private int airborneRun;
    private int landedRun;
    private V3 lastPosition;

    private String lastMode;
    private String lastPilot;
    private int lastRings = Integer.MIN_VALUE;
    private boolean lastWeighing;
    private double lastLoad = Double.NaN;
    private String lastGuard = "CLEAR";
    private List<AerialAlerts.Code> heldAlerts = List.of();

    public void observe(AerialSnapshot measured) {
        if (measured == null) {
            return;
        }
        final long t = measured.gameTime();
        if (this.lastSampleTick != Long.MIN_VALUE && t - this.lastSampleTick < SAMPLE_TICKS
                && t >= this.lastSampleTick) {
            return;
        }
        // reload or /time set went backwards, restart the windows

        if (this.lastSampleTick != Long.MIN_VALUE && (t < this.lastSampleTick || t - this.lastSampleTick > 100)) {
            this.window.clear();
        }
        final double dt = this.lastSampleTick == Long.MIN_VALUE || t < this.lastSampleTick
                ? 0.0 : Math.min(1.0, (t - this.lastSampleTick) / 20.0);
        this.lastSampleTick = t;

        final AirframeReadout r = AirframeMath.read(measured);
        final boolean airborne = !r.landed;
        if (airborne) {
            this.seconds += dt;
        }
        this.window.addLast(new Sample(t, r.airspeed, r.verticalSpeed, r.groundSpeed, r.turnRate, airborne));
        while (!this.window.isEmpty() && t - this.window.peekFirst().tick() > 120) {
            this.window.removeFirst();
        }
        learn(t);
        trail(measured, r, t);
        history(r, t);
        voyage(measured, r, t, dt);
    }

    private void voyage(AerialSnapshot s, AirframeReadout r, long t, double dt) {
        final V3 p = s.body().position();
        if (r.landed) {
            this.landedRun++;
            this.airborneRun = 0;
        } else {
            this.airborneRun++;
            this.landedRun = 0;
        }
        final boolean aloft = this.takeoff != AerialSnapshot.NO_TICK;
        if (!aloft && this.airborneRun >= AIRBORNE_SAMPLES) {
            takeOff(t, p);
        } else if (aloft && this.landedRun >= LANDED_SAMPLES) {
            event(t, AerialSnapshot.Event.LANDED, 0, "Landed", p);

            int alerts = 0;
            int orders = 0;
            for (final AerialSnapshot.Event e : this.events) {
                if (e.tick() >= this.takeoff) {
                    alerts += e.kind() == AerialSnapshot.Event.ALERT ? 1 : 0;
                    orders += e.kind() == AerialSnapshot.Event.ORDER ? 1 : 0;
                }
            }
            this.lastLeg = new AerialSnapshot.Leg(this.takeoff, t, this.voyageSeconds, this.voyageDistance, this.highest,
                    alerts, orders);

            this.takeoff = AerialSnapshot.NO_TICK;
        }
        if (this.takeoff != AerialSnapshot.NO_TICK && p.isFinite()) {
            this.voyageSeconds += dt;
            if (this.lastPosition != null && this.lastPosition.isFinite()) {
                final double step = p.minus(this.lastPosition).horizontalLength();
                if (step < TELEPORT) {
                    this.voyageDistance += step;
                }
            }
            this.highest = Double.isNaN(this.highest) ? p.y() : Math.max(this.highest, p.y());
            traceSample(t, p.y());
        }
        this.lastPosition = p;
        helmEvents(s, r, t, p);
        alertEvents(r, t, p);
    }

    private void takeOff(long t, V3 p) {
        this.takeoff = t;
        this.voyageSeconds = 0.0;
        this.voyageDistance = 0.0;
        this.highest = p.y();
        this.tracePeriod = TRACE_TICKS;
        this.traceCount = 0;
        this.traceLast = Long.MIN_VALUE;

        this.events.removeIf(e -> t - e.tick() > PRE_FLIGHT_TICKS || e.kind() == AerialSnapshot.Event.LANDED);
        event(t, AerialSnapshot.Event.TAKEOFF, 0, "Took off", p);
    }

    private void traceSample(long t, double y) {
        if (this.traceLast != Long.MIN_VALUE && t - this.traceLast < this.tracePeriod && t >= this.traceLast) {
            return;
        }
        if (this.traceCount == this.trace.length) {
            final int half = this.traceCount / 2;
            for (int i = 0; i < half; i++) {
                this.trace[i] = 0.5f * (this.trace[2 * i] + this.trace[2 * i + 1]);
            }
            this.traceCount = half;
            this.tracePeriod *= 2;
        }
        this.trace[this.traceCount++] = (float) y;
        this.traceLast = t;
    }

    private void helmEvents(AerialSnapshot s, AirframeReadout r, long t, V3 p) {
        final AerialSnapshot.Helm h = s.helm();
        if (!h.known()) {
            return;
        }
        if (this.lastMode != null && !h.mode().equals(this.lastMode)) {
            final boolean arrived = "GO_TO".equals(this.lastMode) && "ANCHOR".equals(h.mode()) && r.nav != null
                    && r.nav.distance() < ARRIVED;
            if (arrived) {
                event(t, AerialSnapshot.Event.ARRIVED, 0, "Arrived and anchored", p);
            } else {
                final String words = HelmWords.modeChange(this.lastMode, h.mode(), this.lastPilot, h.pilot());
                if (!words.isEmpty()) {
                    event(t, AerialSnapshot.Event.MODE, 0, words, p);
                }
            }
        } else if (this.lastPilot != null && !h.pilot().equals(this.lastPilot)) {
            event(t, AerialSnapshot.Event.PILOT, 0, h.pilot().isEmpty() ? this.lastPilot + " left the helm"
                    : h.pilot() + " took the helm", p);
        }
        if (this.lastRings != Integer.MIN_VALUE && h.rings() != this.lastRings) {
            event(t, AerialSnapshot.Event.ORDER, 0, HelmWords.order(h.order()), p);
        }
        final double load = s.mass().load();
        if (this.lastWeighing && !h.weighing() && Double.isFinite(load) && load > 0.5) {
            event(t, AerialSnapshot.Event.WEIGHED, 0, String.format(java.util.Locale.ROOT, "Weighed off: %s kpg aboard",
                    Math.round(load)), p);
        } else if (!h.weighing() && Double.isFinite(load) && Double.isFinite(this.lastLoad)
                && Math.abs(load - this.lastLoad) > Math.max(10.0, 0.05 * Math.max(load, this.lastLoad))) {
            event(t, AerialSnapshot.Event.LOAD, 0, load < 0.5 ? "Load lifted off"
                    : String.format(java.util.Locale.ROOT, "Load now %s kpg", Math.round(load)), p);
        }
        final String guard = h.guard().verdict();
        if (!"CLEAR".equals(guard) && !guard.equals(this.lastGuard)) {
            event(t, AerialSnapshot.Event.GUARD, 0, HelmWords.guard(h.guard()), p);
        }
        this.lastMode = h.mode();
        this.lastPilot = h.pilot();
        this.lastRings = h.rings();
        this.lastWeighing = h.weighing();
        this.lastLoad = load;
        this.lastGuard = guard;
    }

    private void alertEvents(AirframeReadout r, long t, V3 p) {
        final List<AerialAlerts.Alert> alerts = AerialAlerts.evaluate(r, this.heldAlerts, 0.0);
        for (final AerialAlerts.Alert a : alerts) {
            if (a.level() != AerialAlerts.Level.ADVISORY && !this.heldAlerts.contains(a.code())) {
                event(t, AerialSnapshot.Event.ALERT, a.level().ordinal(), a.text(), p);
            }
        }
        this.heldAlerts = AerialAlerts.codes(alerts);
    }

    private void event(long t, int kind, int level, String text, V3 p) {
        if (this.events.size() >= AerialSnapshot.Voyage.MAX_EVENTS) {
            int drop = 0;
            if (this.events.get(0).kind() == AerialSnapshot.Event.TAKEOFF && this.events.size() > 1) {
                drop = 1;
            }
            this.events.remove(drop);
        }
        this.events.add(new AerialSnapshot.Event(t, kind, level, text, p.isFinite() ? (float) p.y() : Float.NaN));
    }

    private List<Sample> held(long now, double seconds) {
        final long from = now - Math.round(seconds * 20.0);
        final List<Sample> out = new ArrayList<>();
        for (final Sample s : this.window) {
            if (s.tick() >= from) {
                out.add(s);
            }
        }

        if (out.isEmpty() || out.get(0).tick() > from + SAMPLE_TICKS || out.size() < 3) {
            return List.of();
        }
        for (final Sample s : out) {
            if (!s.airborne()) {
                return List.of();
            }
        }
        return out;
    }

    private void learn(long now) {
        final List<Sample> speed = held(now, HOLD_SPEED);
        if (!speed.isEmpty()) {
            boolean level = true;
            double minAir = Double.POSITIVE_INFINITY;
            double minVs = Double.POSITIVE_INFINITY;
            double maxVs = Double.NEGATIVE_INFINITY;
            for (final Sample s : speed) {
                level &= Math.abs(s.verticalSpeed()) < LEVEL;
                minAir = Math.min(minAir, s.airspeed());
                minVs = Math.min(minVs, s.verticalSpeed());
                maxVs = Math.max(maxVs, s.verticalSpeed());
            }
            if (level) {
                this.topSpeed = best(this.topSpeed, minAir, true);
            }
            if (minVs > 0.05) {
                this.bestClimb = best(this.bestClimb, minVs, true);
            }
            if (maxVs < -0.05) {
                this.maxSink = best(this.maxSink, maxVs, false);
            }
        }

        final List<Sample> rate = held(now, HOLD_RATE);
        if (!rate.isEmpty()) {
            double minTurn = Double.POSITIVE_INFINITY;
            final double sign = Math.signum(rate.get(0).turnRate());
            boolean sameSign = sign != 0.0;
            for (final Sample s : rate) {
                sameSign &= Math.signum(s.turnRate()) == sign;
                minTurn = Math.min(minTurn, Math.abs(s.turnRate()));
            }
            if (sameSign) {
                this.maxYawRate = best(this.maxYawRate, minTurn, true);
            }
            double minAccel = Double.POSITIVE_INFINITY;
            double minDecel = Double.POSITIVE_INFINITY;
            for (int i = 1; i < rate.size(); i++) {
                final double dt = (rate.get(i).tick() - rate.get(i - 1).tick()) / 20.0;
                if (dt <= 0.0) {
                    continue;
                }
                final double a = (rate.get(i).groundSpeed() - rate.get(i - 1).groundSpeed()) / dt;
                minAccel = Math.min(minAccel, a);
                minDecel = Math.min(minDecel, -a);
            }
            if (minAccel > 0.05 && Double.isFinite(minAccel)) {
                this.maxAccel = best(this.maxAccel, minAccel, true);
            }
            if (minDecel > 0.05 && Double.isFinite(minDecel)) {
                this.maxDecel = best(this.maxDecel, minDecel, true);
            }
        }

        final List<Sample> turning = held(now, HOLD_TURN);
        if (!turning.isEmpty()) {
            final double sign = Math.signum(turning.get(0).turnRate());
            double speedSum = 0.0;
            double rateSum = 0.0;
            boolean steady = sign != 0.0;
            for (final Sample s : turning) {
                steady &= Math.signum(s.turnRate()) == sign && Math.abs(s.turnRate()) >= MIN_TURN
                        && s.groundSpeed() > 1.0;
                speedSum += s.groundSpeed();
                rateSum += Math.abs(s.turnRate());
            }
            if (steady) {
                final double meanSpeed = speedSum / turning.size();
                final double radius = meanSpeed / Math.toRadians(rateSum / turning.size());
                final int b = (int) Math.floor(meanSpeed / BUCKET_WIDTH);
                if (b >= 0 && b < this.turn.length && (Float.isNaN(this.turn[b]) || radius < this.turn[b])) {
                    this.turn[b] = (float) radius;
                }
            }
        }
    }

    private static double best(double current, double candidate, boolean higher) {
        if (!Double.isFinite(candidate)) {
            return current;
        }
        if (Double.isNaN(current)) {
            return candidate;
        }
        return higher ? Math.max(current, candidate) : Math.min(current, candidate);
    }

    private void trail(AerialSnapshot s, AirframeReadout r, long t) {
        final V3 p = s.body().position();
        if (!p.isFinite()) {
            return;
        }
        if (this.fixes.isEmpty()) {
            addFix(p, t, r.track);
            return;
        }
        final Fix last = this.fixes.get(this.fixes.size() - 1);
        final double moved = p.minus(last.position()).length();
        final boolean due = t - last.gameTime() >= TRAIL_TICKS && moved >= TRAIL_METRES;
        final boolean turned = moved >= 1.0 && r.groundSpeed > 0.5 && Double.isFinite(this.lastFixTrack)
                && Math.abs(AirframeMath.wrap180(r.track - this.lastFixTrack)) >= TRAIL_TURN_DEGREES;
        if (due || turned) {
            addFix(p, t, r.track);
        }
    }

    private void addFix(V3 p, long t, double track) {
        if (this.fixes.size() >= Trail.MAX_FIXES) {
            thin();
        }
        this.fixes.add(new Fix(p, t));
        this.lastFixTrack = track;
    }

    private void thin() {
        final int half = this.fixes.size() / 2;
        final List<Fix> kept = new ArrayList<>(this.fixes.size());
        for (int i = 0; i < half; i++) {
            if (i % 2 == 0) {
                kept.add(this.fixes.get(i));
            }
        }
        kept.addAll(this.fixes.subList(half, this.fixes.size()));
        this.fixes.clear();
        this.fixes.addAll(kept);
    }

    private void history(AirframeReadout r, long t) {
        if (this.lastHistoryTick != Long.MIN_VALUE && t - this.lastHistoryTick < HISTORY_TICKS
                && t >= this.lastHistoryTick) {
            return;
        }
        this.lastHistoryTick = t;
        final float[] row = {(float) r.altitude, (float) r.airspeed, (float) r.verticalSpeed, (float) r.support,
                r.route == null ? Float.NaN : (float) r.route.crossTrack()};
        if (this.historyCount == History.MAX_SAMPLES) {
            for (final float[] series : this.history) {
                System.arraycopy(series, 1, series, 0, series.length - 1);
            }
            this.historyCount--;
        }
        for (int i = 0; i < row.length; i++) {
            this.history[i][this.historyCount] = row[i];
        }
        this.historyCount++;
    }

    public Trail trail() {
        return new Trail(this.fixes);
    }

    public History history() {
        return new History(HISTORY_TICKS, this.lastHistoryTick == Long.MIN_VALUE ? 0L : this.lastHistoryTick,
                Arrays.copyOf(this.history[0], this.historyCount), Arrays.copyOf(this.history[1], this.historyCount),
                Arrays.copyOf(this.history[2], this.historyCount), Arrays.copyOf(this.history[3], this.historyCount),
                Arrays.copyOf(this.history[4], this.historyCount));
    }

    public Envelope envelope() {
        return new Envelope(this.seconds, this.topSpeed, this.bestClimb, this.maxSink, this.maxYawRate,
                this.maxAccel, this.maxDecel, this.turn.clone(), BUCKET_WIDTH);
    }

    public AerialSnapshot.Voyage voyage() {
        return new AerialSnapshot.Voyage(this.takeoff, this.voyageSeconds, this.voyageDistance, this.highest,
                this.tracePeriod, this.traceLast == Long.MIN_VALUE ? 0L : this.traceLast,
                Arrays.copyOf(this.trace, this.traceCount), List.copyOf(this.events), this.lastLeg);
    }

    public AerialSnapshot recorded(AerialSnapshot measured) {
        return measured.withRecorded(trail(), history(), envelope(), voyage());
    }

    public void clearTrail() {
        this.fixes.clear();
        this.lastFixTrack = Double.NaN;
    }

    public void resetEnvelope() {
        this.seconds = 0.0;
        this.topSpeed = Double.NaN;
        this.bestClimb = Double.NaN;
        this.maxSink = Double.NaN;
        this.maxYawRate = Double.NaN;
        this.maxAccel = Double.NaN;
        this.maxDecel = Double.NaN;
        Arrays.fill(this.turn, Float.NaN);
        this.window.clear();
    }

    public Route trailAsRoute(String name) {
        if (this.fixes.size() < 2) {
            return Route.NONE;
        }
        double tolerance = 2.0;
        List<Integer> kept = simplify(tolerance);
        while (kept.size() > ROUTE_POINTS) {
            tolerance *= 2.0;
            kept = simplify(tolerance);
        }
        final List<Waypoint> points = new ArrayList<>(kept.size());
        for (int k = 0; k < kept.size(); k++) {
            final int i = kept.get(k);
            final int from = k == 0 ? kept.get(0) : kept.get(k - 1);
            final int to = k == 0 ? (kept.size() > 1 ? kept.get(1) : i) : i;
            points.add(new Waypoint(this.fixes.get(i).position(), stretchSpeed(from, to)));
        }
        return new Route(name, points, 0);
    }

    private double stretchSpeed(int from, int to) {
        if (to <= from) {
            return Double.NaN;
        }
        double length = 0.0;
        for (int i = from + 1; i <= to; i++) {
            length += this.fixes.get(i).position().minus(this.fixes.get(i - 1).position()).length();
        }
        final double time = (this.fixes.get(to).gameTime() - this.fixes.get(from).gameTime()) / 20.0;
        return time > 0.0 ? length / time : Double.NaN;
    }

    private List<Integer> simplify(double tolerance) {
        final boolean[] keep = new boolean[this.fixes.size()];
        keep[0] = true;
        keep[keep.length - 1] = true;
        simplify(0, keep.length - 1, tolerance, keep);
        final List<Integer> out = new ArrayList<>();
        for (int i = 0; i < keep.length; i++) {
            if (keep[i]) {
                out.add(i);
            }
        }
        return out;
    }

    private void simplify(int a, int b, double tolerance, boolean[] keep) {
        if (b <= a + 1) {
            return;
        }
        final V3 pa = this.fixes.get(a).position();
        final V3 pb = this.fixes.get(b).position();
        double worst = -1.0;
        int at = -1;
        for (int i = a + 1; i < b; i++) {
            final double d = distanceToSegment(this.fixes.get(i).position(), pa, pb);
            if (d > worst) {
                worst = d;
                at = i;
            }
        }
        if (worst > tolerance) {
            keep[at] = true;
            simplify(a, at, tolerance, keep);
            simplify(at, b, tolerance, keep);
        }
    }

    static double distanceToSegment(V3 p, V3 a, V3 b) {
        final V3 ab = b.minus(a);
        final double len2 = ab.dot(ab);
        if (len2 < 1.0e-12) {
            return p.minus(a).length();
        }
        final double t = AirframeMath.clamp(p.minus(a).dot(ab) / len2, 0.0, 1.0);
        return p.minus(a.plus(ab.times(t))).length();
    }

    public boolean apply(AerialCommand command) {
        if (command == AerialCommand.CLEAR_TRAIL) {
            clearTrail();
            return true;
        }
        if (command == AerialCommand.RESET_ENVELOPE) {
            resetEnvelope();
            return true;
        }
        return false;
    }

    public byte[] save() {
        final AerialSnapshot carrier = new AerialSnapshot(this.lastSampleTick == Long.MIN_VALUE ? 0L
                : this.lastSampleTick, "", null, 0, null, null, null, null, null, null, null, null, null, null, null,
                null, null, trail(), history(), envelope(), null, null, null, voyage());
        return AerialCodec.encode(carrier, AerialCodec.Section.HEADER.bit() | AerialCodec.Section.TRAIL.bit()
                | AerialCodec.Section.HISTORY.bit() | AerialCodec.Section.ENVELOPE.bit()
                | AerialCodec.Section.VOYAGE.bit());
    }

    public static AerialRecorder load(byte[] data) {
        final AerialRecorder r = new AerialRecorder();
        if (data == null || data.length == 0) {
            return r;
        }
        final AerialSnapshot s;
        try {
            s = AerialCodec.decode(data, null);
        } catch (IOException e) {
            return r;
        }
        r.fixes.addAll(s.trail().fixes());
        final History h = s.history();
        r.historyCount = h.size();
        r.history[0] = Arrays.copyOf(h.altitude(), History.MAX_SAMPLES);
        r.history[1] = Arrays.copyOf(h.airspeed(), History.MAX_SAMPLES);
        r.history[2] = Arrays.copyOf(h.verticalSpeed(), History.MAX_SAMPLES);
        r.history[3] = Arrays.copyOf(h.support(), History.MAX_SAMPLES);
        r.history[4] = Arrays.copyOf(h.crossTrack(), History.MAX_SAMPLES);
        r.lastHistoryTick = h.size() > 0 ? h.lastTick() : Long.MIN_VALUE;
        final Envelope e = s.envelope();
        r.seconds = e.seconds();
        r.topSpeed = e.topSpeed();
        r.bestClimb = e.bestClimb();
        r.maxSink = e.maxSink();
        r.maxYawRate = e.maxYawRate();
        r.maxAccel = e.maxAccel();
        r.maxDecel = e.maxDecel();
        System.arraycopy(e.turnRadius(), 0, r.turn, 0, Math.min(r.turn.length, e.turnRadius().length));

        final AerialSnapshot.Voyage v = s.voyage();
        r.takeoff = v.takeoff();
        r.voyageSeconds = v.seconds();
        r.voyageDistance = v.distance();
        r.highest = v.highest();
        r.tracePeriod = v.tracePeriod();
        r.traceLast = v.trace().length > 0 ? v.traceLast() : Long.MIN_VALUE;
        r.traceCount = Math.min(r.trace.length, v.trace().length);
        System.arraycopy(v.trace(), 0, r.trace, 0, r.traceCount);
        r.events.addAll(v.events());
        r.lastLeg = v.last();
        if (r.takeoff != AerialSnapshot.NO_TICK) {
            r.airborneRun = AIRBORNE_SAMPLES;
        }
        return r;
    }
}
