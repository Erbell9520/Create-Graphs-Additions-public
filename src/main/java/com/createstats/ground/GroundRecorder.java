package com.createstats.ground;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;

import com.createstats.aerial.V3;
import com.createstats.ground.GroundSnapshot.Envelope;
import com.createstats.ground.GroundSnapshot.Event;
import com.createstats.ground.GroundSnapshot.EventKind;
import com.createstats.ground.GroundSnapshot.Events;
import com.createstats.ground.GroundSnapshot.Fix;
import com.createstats.ground.GroundSnapshot.History;
import com.createstats.ground.GroundSnapshot.Odometer;
import com.createstats.ground.GroundSnapshot.Trail;

public final class GroundRecorder {
    public static final int SAMPLE_TICKS = 4;

    public static final int TRAIL_TICKS = 20;
    public static final double TRAIL_BLOCKS = 4.0;
    public static final double TRAIL_TURN_DEGREES = 12.0;

    public static final int HISTORY_TICKS = 10;

    static final double MAX_STEP_PER_TICK = 5.0;

    static final double HOLD_SPEED = 1.0;
    static final double HOLD_RATE = 0.5;
    static final double HOLD_TURN = 2.0;

    static final double MIN_TURN = 3.0;

    static final int PROBLEM_CONFIRM = 2;

    static final double RECORD_STEP = 0.5;

    static final long RECORD_SETTLE = 100L;

    private double odometer;
    private double trip;
    private long tripTicks;
    private double topSpeed = Double.NaN;
    private double tripTop = Double.NaN;
    private long tripStart;
    private V3 lastPosition;
    private long lastTick = Long.MIN_VALUE;

    private final List<Fix> fixes = new ArrayList<>();
    private double lastFixTrack = Double.NaN;

    private final float[][] history = new float[8][History.MAX_SAMPLES];
    private int historyCount;
    private long lastHistoryTick = Long.MIN_VALUE;

    private final ArrayDeque<Sample> window = new ArrayDeque<>();

    private double seconds;
    private double learnedTop = Double.NaN;
    private double maxAccel = Double.NaN;
    private double maxDecel = Double.NaN;
    private double maxLatG = Double.NaN;
    private final float[] turn = GroundSnapshot.nanArray(Envelope.BUCKETS);

    private final ArrayDeque<Event> events = new ArrayDeque<>();

    private boolean primed;
    private String driver = "";
    private long impactStart = Long.MIN_VALUE;
    private boolean inAir;
    private int trailers;
    private final Set<String> lostUnits = new HashSet<>();
    private final Map<String, Integer> problemSeen = new HashMap<>();
    private final Map<String, Integer> problemGone = new HashMap<>();
    private final Set<String> problems = new HashSet<>();
    private double recordLogged = Double.NaN;
    private long recordSetAt = Long.MIN_VALUE;
    private V3 recordAt;
    private double recordSeen = Double.NaN;

    private record Sample(long tick, double speed, double groundSpeed, double latG, double turnRate, boolean driving) {
    }

    public void observe(GroundSnapshot measured) {
        if (measured == null) {
            return;
        }
        final long t = measured.gameTime();
        if (this.lastTick != Long.MIN_VALUE && t >= this.lastTick && t - this.lastTick < SAMPLE_TICKS) {
            return;
        }

        final boolean gap = this.lastTick == Long.MIN_VALUE || t < this.lastTick || t - this.lastTick > 100;
        if (gap) {
            this.window.clear();
        }
        final long dt = gap ? 0L : t - this.lastTick;
        final DriveReadout r = DriveMath.read(measured);
        final boolean driving = measured.drive().driving();
        distance(measured, t, dt, driving);
        this.lastTick = t;
        if (driving) {
            this.seconds += dt / 20.0;
            this.window.addLast(new Sample(t, r.speed, r.groundSpeed, r.latG, r.turnRate, true));
        } else {
            this.window.clear();
        }
        while (!this.window.isEmpty() && t - this.window.peekFirst().tick() > 60) {
            this.window.removeFirst();
        }
        learn(t, measured);
        trail(measured, r, t);
        history(measured, r, t);
        events(measured, r, t);
        this.primed = true;
    }

    private void distance(GroundSnapshot s, long t, long dt, boolean driving) {
        final V3 p = s.body().position();
        if (!p.isFinite() || !s.identity().onCraft()) {
            this.lastPosition = null;
            return;
        }
        if (this.lastPosition != null && dt > 0L) {
            final double step = p.minus(this.lastPosition).length();
            if (step <= MAX_STEP_PER_TICK * dt) {
                this.odometer += step;
                this.trip += step;
            }
        }
        this.lastPosition = p;
        if (driving) {
            this.tripTicks += dt;
        }
    }

    private List<Sample> held(long now, double seconds) {
        final long from = now - Math.round(seconds * 20.0);
        final List<Sample> out = new ArrayList<>();
        for (final Sample s : this.window) {
            if (s.tick() >= from) {
                out.add(s);
            }
        }

        if (out.size() < 3 || out.get(0).tick() > from + SAMPLE_TICKS) {
            return List.of();
        }
        return out;
    }

    private void learn(long now, GroundSnapshot s) {
        final List<Sample> speed = held(now, HOLD_SPEED);
        if (!speed.isEmpty()) {
            double min = Double.POSITIVE_INFINITY;
            double minLat = Double.POSITIVE_INFINITY;
            final double sign = Math.signum(speed.get(0).speed());
            final double latSign = Math.signum(speed.get(0).latG());
            boolean sameWay = sign != 0.0;
            boolean sameSide = latSign != 0.0;
            for (final Sample x : speed) {
                min = Math.min(min, Math.abs(x.speed()));
                sameWay &= Math.signum(x.speed()) == sign;
                minLat = Math.min(minLat, Math.abs(x.latG()));
                sameSide &= Math.signum(x.latG()) == latSign;
            }
            if (sameWay && min > 0.1) {
                this.learnedTop = best(this.learnedTop, min);
                this.topSpeed = best(this.topSpeed, min);
                this.tripTop = best(this.tripTop, min);
            }
            if (sameSide && minLat > 0.02) {
                this.maxLatG = best(this.maxLatG, minLat);
            }
        }
        final List<Sample> rate = held(now, HOLD_RATE);
        if (!rate.isEmpty()) {
            double minAccel = Double.POSITIVE_INFINITY;
            double minDecel = Double.POSITIVE_INFINITY;
            for (int i = 1; i < rate.size(); i++) {
                final double dt = (rate.get(i).tick() - rate.get(i - 1).tick()) / 20.0;
                if (dt <= 0.0) {
                    continue;
                }

                final double a = (Math.abs(rate.get(i).speed()) - Math.abs(rate.get(i - 1).speed())) / dt;
                minAccel = Math.min(minAccel, a);
                minDecel = Math.min(minDecel, -a);
            }
            if (Double.isFinite(minAccel) && minAccel > 0.05) {
                this.maxAccel = best(this.maxAccel, minAccel);
            }
            if (Double.isFinite(minDecel) && minDecel > 0.05) {
                this.maxDecel = best(this.maxDecel, minDecel);
            }
        }
        final List<Sample> turning = held(now, HOLD_TURN);
        if (!turning.isEmpty()) {
            final double sign = Math.signum(turning.get(0).turnRate());
            boolean steady = sign != 0.0;
            double speedSum = 0.0;
            double rateSum = 0.0;
            for (final Sample x : turning) {
                steady &= Math.signum(x.turnRate()) == sign && Math.abs(x.turnRate()) >= MIN_TURN
                        && x.groundSpeed() > 1.0;
                speedSum += x.groundSpeed();
                rateSum += Math.abs(x.turnRate());
            }
            if (steady) {
                final double meanSpeed = speedSum / turning.size();
                final double radius = meanSpeed / Math.toRadians(rateSum / turning.size());
                final int b = (int) Math.floor(meanSpeed / 2.0);
                if (b >= 0 && b < this.turn.length && (Float.isNaN(this.turn[b]) || radius < this.turn[b])) {
                    this.turn[b] = (float) radius;
                }
            }
        }
    }

    private static double best(double current, double candidate) {
        if (!Double.isFinite(candidate)) {
            return current;
        }
        return Double.isNaN(current) ? candidate : Math.max(current, candidate);
    }

    private void trail(GroundSnapshot s, DriveReadout r, long t) {
        final V3 p = s.body().position();
        if (!p.isFinite() || !s.identity().onCraft()) {
            return;
        }
        if (this.fixes.isEmpty()) {
            addFix(p, t, r.track);
            return;
        }
        final Fix last = this.fixes.get(this.fixes.size() - 1);
        final double moved = p.minus(last.position()).length();
        final boolean due = t - last.gameTime() >= TRAIL_TICKS && moved >= TRAIL_BLOCKS;
        final boolean turned = moved >= 1.0 && Double.isFinite(r.track) && Double.isFinite(this.lastFixTrack)
                && Math.abs(DriveMath.wrap180(r.track - this.lastFixTrack)) >= TRAIL_TURN_DEGREES;
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

    private void history(GroundSnapshot s, DriveReadout r, long t) {
        if (this.lastHistoryTick != Long.MIN_VALUE && t - this.lastHistoryTick < HISTORY_TICKS && t >= this.lastHistoryTick) {
            return;
        }
        this.lastHistoryTick = t;
        final GroundSnapshot.Drive d = s.drive();
        final float[] row = {(float) r.speed, (float) d.throttle(), d.braking() ? 1.0f : 0.0f, (float) d.steer(),
                (float) r.latG, (float) r.lonG, (float) r.worstSlip, (float) r.pitch};
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

    private void events(GroundSnapshot s, DriveReadout r, long t) {
        final V3 at = s.body().position();
        final GroundSnapshot.Drive d = s.drive();
        final GroundSnapshot.Log log = s.log();
        final int trailersNow = r.trailers;
        final long impactNow = log.impactAge() >= 0 ? t - log.impactAge() : Long.MIN_VALUE;
        final boolean airNow = log.airNow() > 0.0;
        if (!this.primed) {
            this.driver = d.driver();
            this.impactStart = impactNow;
            this.inAir = airNow;
            this.trailers = trailersNow;
            this.problems.addAll(s.problems());
            this.recordLogged = this.topSpeed;
            this.recordSeen = this.topSpeed;
            return;
        }
        if (!d.driver().equals(this.driver)) {
            if (!this.driver.isEmpty()) {
                add(new Event(t, EventKind.LEFT, 0f, this.driver, at));
            }
            if (!d.driver().isEmpty()) {
                add(new Event(t, EventKind.HELM, 0f, d.driver(), at));
            }
            this.driver = d.driver();
        }
        if (impactNow != Long.MIN_VALUE && log.lastImpact() > 0.0) {
            if (impactNow != this.impactStart) {
                add(new Event(impactNow, EventKind.IMPACT, (float) log.lastImpact(), "", at));
                this.impactStart = impactNow;
            } else {
                grow(EventKind.IMPACT, impactNow, (float) log.lastImpact());
            }
        }
        if (this.inAir && !airNow && log.lastAir() > 0.0) {
            add(new Event(t, EventKind.AIRTIME, (float) log.lastAir(), "", at));
        }
        this.inAir = airNow;
        if (trailersNow > this.trailers) {
            add(new Event(t, EventKind.COUPLED, trailersNow, trailersNow == 1 ? "towing 1" : "towing " + trailersNow, at));
        } else if (trailersNow < this.trailers) {
            add(new Event(t, EventKind.LOST, trailersNow, trailersNow == 0 ? "towing none" : "towing " + trailersNow, at));
        }
        this.trailers = trailersNow;
        for (final GroundSnapshot.Unit u : s.units()) {
            if (u.isTractor()) {
                continue;
            }
            if (u.has(GroundSnapshot.Unit.FLAG_LOST)) {
                if (this.lostUnits.add(u.name())) {
                    add(new Event(t, EventKind.LOST, 0f, u.name() + "'s " + u.link().noun() + " went", at));
                }
            } else {
                this.lostUnits.remove(u.name());
            }
        }
        problems(s, t, at);
        record(t, at);
    }

    private void record(long t, V3 at) {
        if (!Double.isFinite(this.topSpeed)) {
            return;
        }
        if (Double.isNaN(this.recordSeen) || this.topSpeed > this.recordSeen) {
            this.recordSeen = this.topSpeed;
            this.recordSetAt = t;
            this.recordAt = at;
            return;
        }
        final boolean worth = Double.isNaN(this.recordLogged) || this.recordSeen >= this.recordLogged + RECORD_STEP;
        if (worth && this.recordSetAt != Long.MIN_VALUE && t - this.recordSetAt >= RECORD_SETTLE) {
            add(new Event(this.recordSetAt, EventKind.TOP_SPEED, (float) this.recordSeen, "", this.recordAt));
            this.recordLogged = this.recordSeen;
        }
    }

    private void problems(GroundSnapshot s, long t, V3 at) {
        final Set<String> now = new HashSet<>(s.problems());
        for (final String p : now) {
            this.problemGone.remove(p);
            if (this.problems.contains(p)) {
                continue;
            }
            final int seen = this.problemSeen.merge(p, 1, Integer::sum);
            if (seen >= PROBLEM_CONFIRM) {
                this.problems.add(p);
                this.problemSeen.remove(p);
                add(new Event(t, EventKind.PROBLEM, 0f, p, at));
            }
        }
        this.problemSeen.keySet().retainAll(now);
        for (final String p : new ArrayList<>(this.problems)) {
            if (now.contains(p)) {
                continue;
            }
            final int gone = this.problemGone.merge(p, 1, Integer::sum);
            if (gone >= PROBLEM_CONFIRM) {
                this.problems.remove(p);
                this.problemGone.remove(p);
                add(new Event(t, EventKind.CLEARED, 0f, p, at));
            }
        }
    }

    private void add(Event e) {
        while (this.events.size() >= Events.MAX_EVENTS) {
            this.events.removeFirst();
        }
        this.events.addLast(e);
    }

    private void grow(EventKind kind, long tick, float value) {
        final List<Event> all = new ArrayList<>(this.events);
        for (int i = all.size() - 1; i >= 0; i--) {
            final Event e = all.get(i);
            if (e.kind() == kind && e.tick() == tick) {
                if (value > e.value()) {
                    all.set(i, new Event(e.tick(), e.kind(), value, e.text(), e.position()));
                    this.events.clear();
                    this.events.addAll(all);
                }
                return;
            }
        }
    }

    public Odometer odometer() {
        return new Odometer(this.odometer, this.trip, this.tripTicks, this.topSpeed, this.tripTop, this.tripStart);
    }

    public Trail trail() {
        return new Trail(this.fixes);
    }

    public History history() {
        final float[][] h = this.history;
        final int n = this.historyCount;
        return new History(HISTORY_TICKS, this.lastHistoryTick == Long.MIN_VALUE ? 0L : this.lastHistoryTick,
                Arrays.copyOf(h[0], n), Arrays.copyOf(h[1], n), Arrays.copyOf(h[2], n), Arrays.copyOf(h[3], n),
                Arrays.copyOf(h[4], n), Arrays.copyOf(h[5], n), Arrays.copyOf(h[6], n), Arrays.copyOf(h[7], n));
    }

    public Events events() {
        return new Events(new ArrayList<>(this.events));
    }

    public Envelope envelope() {
        return new Envelope(this.seconds, this.learnedTop, this.maxAccel, this.maxDecel, this.maxLatG,
                this.turn.clone(), 2.0);
    }

    public GroundSnapshot stamp(GroundSnapshot measured) {
        return measured.withRecorded(odometer(), trail(), history(), events(), envelope());
    }

    public boolean apply(GroundCommand command, long now) {
        if (command == GroundCommand.RESET_TRIP) {
            this.trip = 0.0;
            this.tripTicks = 0L;
            this.tripTop = Double.NaN;
            this.tripStart = now;
            add(new Event(now, EventKind.TRIP, 0f, "", this.lastPosition));
            return true;
        }
        if (command == GroundCommand.CLEAR_TRAIL) {
            this.fixes.clear();
            this.lastFixTrack = Double.NaN;
            return true;
        }
        if (command == GroundCommand.RESET_LEARNED) {
            this.seconds = 0.0;
            this.learnedTop = Double.NaN;
            this.maxAccel = Double.NaN;
            this.maxDecel = Double.NaN;
            this.maxLatG = Double.NaN;
            Arrays.fill(this.turn, Float.NaN);
            this.window.clear();
            return true;
        }
        return false;
    }

    public byte[] save() {
        final GroundSnapshot carrier = GroundSnapshot.empty(this.lastTick == Long.MIN_VALUE ? 0L : this.lastTick)
                .withRecorded(odometer(), trail(), history(), events(), envelope());
        final byte[] body = GroundCodec.encode(carrier, GroundCodec.RECORDED);
        final byte[] out = Arrays.copyOf(body, body.length + 4);
        final int crc = crc(body, body.length);
        for (int i = 0; i < 4; i++) {
            out[body.length + i] = (byte) (crc >>> (24 - 8 * i));
        }
        return out;
    }

    public static GroundRecorder load(byte[] data) {
        final GroundRecorder r = new GroundRecorder();
        if (data == null || data.length < 5) {
            return r;
        }
        final int n = data.length - 4;
        int stored = 0;
        for (int i = 0; i < 4; i++) {
            stored = (stored << 8) | (data[n + i] & 0xFF);
        }
        if (stored != crc(data, n)) {
            return r;
        }
        final GroundSnapshot s;
        try {
            s = GroundCodec.decode(Arrays.copyOf(data, n), null);
        } catch (IOException | RuntimeException e) {
            return r;
        }
        final Odometer o = s.odometer();
        if (Double.isFinite(o.odometer()) && o.odometer() >= 0.0) {
            r.odometer = o.odometer();
        }
        if (Double.isFinite(o.trip()) && o.trip() >= 0.0) {
            r.trip = o.trip();
        }
        r.tripTicks = Math.max(0L, o.tripTicks());
        r.topSpeed = o.topSpeed();
        r.tripTop = o.tripTop();
        r.tripStart = o.tripStart();
        r.fixes.addAll(s.trail().fixes());
        final History h = s.history();
        r.historyCount = h.size();
        final float[][] series = {h.speed(), h.throttle(), h.brake(), h.steer(), h.latG(), h.lonG(), h.slip(), h.pitch()};
        for (int i = 0; i < series.length; i++) {
            r.history[i] = Arrays.copyOf(series[i], History.MAX_SAMPLES);
        }
        r.lastHistoryTick = h.size() > 0 ? h.lastTick() : Long.MIN_VALUE;
        r.events.addAll(s.events().list());
        final Envelope e = s.envelope();
        r.seconds = e.seconds();
        r.learnedTop = e.topSpeed();
        r.maxAccel = e.maxAccel();
        r.maxDecel = e.maxDecel();
        r.maxLatG = e.maxLatG();
        System.arraycopy(e.turnRadius(), 0, r.turn, 0, Math.min(r.turn.length, e.turnRadius().length));
        r.recordLogged = r.topSpeed;
        r.recordSeen = r.topSpeed;
        return r;
    }

    private static int crc(byte[] data, int length) {
        final CRC32 c = new CRC32();
        c.update(data, 0, length);
        return (int) c.getValue();
    }
}
