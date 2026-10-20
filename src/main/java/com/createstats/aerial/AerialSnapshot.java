package com.createstats.aerial;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** everything the Aerial knows about its ship, one snapshot. this IS the interface to every display */
public record AerialSnapshot(

        long gameTime,

        String name,

        UUID shipId,

        int commands,
        Body body,
        Air air,
        Mass mass,
        List<Force> forces,
        List<Propeller> propellers,
        List<Balloon> balloons,
        Engine engine,

        Target target,
        Autopilot autopilot,

        Docking docking,
        List<Traffic> traffic,
        Terrain terrain,
        Route route,
        Trail trail,
        History history,
        Envelope envelope,
        Silhouette silhouette,

        Helm helm,

        Parts parts,

        Voyage voyage) {
    public static final int MAX_NAME = 48;
    public static final int MAX_FORCES = 16;
    public static final int MAX_PROPELLERS = 32;
    public static final int MAX_BALLOONS = 16;
    public static final int MAX_TRAFFIC = 24;

    public AerialSnapshot(long gameTime, String name, UUID shipId, int commands, Body body, Air air, Mass mass,
                          List<Force> forces, List<Propeller> propellers, List<Balloon> balloons, Engine engine,
                          Target target, Autopilot autopilot, Docking docking, List<Traffic> traffic, Terrain terrain,
                          Route route, Trail trail, History history, Envelope envelope, Silhouette silhouette) {
        this(gameTime, name, shipId, commands, body, air, mass, forces, propellers, balloons, engine, target, autopilot,
                docking, traffic, terrain, route, trail, history, envelope, silhouette, null, null, null);
    }

    public AerialSnapshot {
        name = clip(name, MAX_NAME);
        body = body == null ? Body.AT_REST : body;
        air = air == null ? Air.NONE : air;
        mass = mass == null ? Mass.UNKNOWN : mass;
        forces = capped(forces, MAX_FORCES);
        propellers = capped(propellers, MAX_PROPELLERS);
        balloons = capped(balloons, MAX_BALLOONS);
        engine = engine == null ? Engine.NONE : engine;
        autopilot = autopilot == null ? Autopilot.OFF : autopilot;
        traffic = capped(traffic, MAX_TRAFFIC);
        terrain = terrain == null ? Terrain.NONE : terrain;
        route = route == null ? Route.NONE : route;
        trail = trail == null ? Trail.EMPTY : trail;
        history = history == null ? History.EMPTY : history;
        envelope = envelope == null ? Envelope.UNKNOWN : envelope;
        silhouette = silhouette == null ? Silhouette.NONE : silhouette;
        helm = helm == null ? Helm.NONE : helm;
        parts = parts == null ? Parts.NONE : parts;
        voyage = voyage == null ? Voyage.NONE : voyage;
    }

    public boolean accepts(AerialCommand command) {
        return command != null && (this.commands & command.bit()) != 0;
    }

    public AerialSnapshot withRecorded(Trail trail, History history, Envelope envelope) {
        return withRecorded(trail, history, envelope, this.voyage);
    }

    public AerialSnapshot withRecorded(Trail trail, History history, Envelope envelope, Voyage voyage) {
        return new AerialSnapshot(this.gameTime, this.name, this.shipId, this.commands, this.body, this.air,
                this.mass, this.forces, this.propellers, this.balloons, this.engine, this.target,
                this.autopilot, this.docking, this.traffic, this.terrain, this.route, trail, history,
                envelope, this.silhouette, this.helm, this.parts, voyage);
    }

    public AerialSnapshot withBody(Body body) {
        return new AerialSnapshot(this.gameTime, this.name, this.shipId, this.commands, body, this.air,
                this.mass, this.forces, this.propellers, this.balloons, this.engine, this.target,
                this.autopilot, this.docking, this.traffic, this.terrain, this.route, this.trail,
                this.history, this.envelope, this.silhouette, this.helm, this.parts, this.voyage);
    }

    public AerialSnapshot withMind(Helm helm, Parts parts) {
        return new AerialSnapshot(this.gameTime, this.name, this.shipId, this.commands, this.body, this.air,
                this.mass, this.forces, this.propellers, this.balloons, this.engine, this.target,
                this.autopilot, this.docking, this.traffic, this.terrain, this.route, this.trail,
                this.history, this.envelope, this.silhouette, helm, parts, this.voyage);
    }

    public record Body(V3 position, Quat orientation, V3 nose, V3 velocity, V3 spin) {
        public static final Body AT_REST = new Body(V3.ZERO, Quat.IDENTITY, new V3(0.0, 0.0, -1.0),
                V3.ZERO, V3.ZERO);

        public Body {
            position = position == null ? V3.ZERO : position;
            orientation = orientation == null ? Quat.IDENTITY : orientation.normalized();
            nose = nose == null || nose.length() < 1.0e-6 ? new V3(0.0, 0.0, -1.0) : nose.normalized();
            velocity = velocity == null ? V3.ZERO : velocity;
            spin = spin == null ? V3.ZERO : spin;
        }
    }

    public record Air(boolean measured, V3 velocity, double pressure, double pressureAbove,
                      double seaLevel, V3 gravity) {
        public static final double PROBE = 16.0;
        public static final Air NONE = new Air(false, V3.ZERO, Double.NaN, Double.NaN, 63.0,
                new V3(0.0, -11.0, 0.0));

        public Air {
            velocity = velocity == null ? V3.ZERO : velocity;
            gravity = gravity == null ? new V3(0.0, -11.0, 0.0) : gravity;
        }
    }

    public record Mass(double mass, V3 inertia, int blocks, double load) {
        public static final Mass UNKNOWN = new Mass(Double.NaN, V3.NAN, -1, Double.NaN);

        public Mass(double mass, V3 inertia, int blocks) {
            this(mass, inertia, blocks, Double.NaN);
        }

        public Mass {
            inertia = inertia == null ? V3.NAN : inertia;
        }

        public double carried() {
            return Double.isFinite(this.load) && this.load > 0.0 ? this.mass + this.load : this.mass;
        }
    }

    public record Force(String group, V3 force, V3 centre) {
        public Force {
            group = clip(group, 24);
            force = force == null ? V3.ZERO : force;
            centre = centre == null ? V3.NAN : centre;
        }
    }

    public record Propeller(String name, String kind, V3 position, V3 direction, double staticThrust,
                            double airflow, double airflowScaling, double pressure, double rpm,
                            double shaftStress, boolean active) {
        public Propeller {
            name = clip(name, 24);
            kind = clip(kind, 12);
            position = position == null ? V3.ZERO : position;
            direction = direction == null || direction.length() < 1.0e-9 ? new V3(0.0, 0.0, -1.0)
                    : direction.normalized();
        }
    }

    public record Balloon(String name, String kind, V3 position, double lift, double filled, double target,
                          double capacity, double fillRate, double gasOutput, double pressure, int heaters, int lit,
                          boolean driven) {
        public Balloon(String name, String kind, V3 position, double lift, double filled, double target,
                       double capacity, double fillRate, double gasOutput, double pressure) {
            this(name, kind, position, lift, filled, target, capacity, fillRate, gasOutput, pressure, -1, -1, true);
        }

        public Balloon {
            name = clip(name, 24);
            kind = clip(kind, 12);
            position = position == null ? V3.ZERO : position;
        }
    }

    public record Engine(int count, double fuelSeconds, double burnFraction, boolean superHeated) {
        public static final Engine NONE = new Engine(0, Double.NaN, Double.NaN, false);
    }

    public record Target(String label, V3 position, String source) {
        public Target {
            label = clip(label, 32);
            source = clip(source, 16);
            position = position == null ? V3.NAN : position;
        }
    }

    public record Autopilot(String mode, boolean engaged, double targetAltitude, double targetSpeed,
                            double targetHeading, V3 lookahead, double authThrottle, double authYaw,
                            double authPitch, double authLift) {
        public static final Autopilot OFF = new Autopilot("OFF", false, Double.NaN, Double.NaN, Double.NaN,
                V3.NAN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);

        public Autopilot {
            mode = clip(mode, 16);
            lookahead = lookahead == null ? V3.NAN : lookahead;
        }
    }

    public record Docking(String partner, boolean connected, V3 offset, V3 relative) {
        public Docking {
            partner = clip(partner, 32);
            offset = offset == null ? V3.NAN : offset;
            relative = relative == null ? V3.ZERO : relative;
        }
    }

    public record Traffic(String name, UUID id, V3 position, V3 velocity, double radius) {
        public Traffic {
            name = clip(name, 24);
            position = position == null ? V3.NAN : position;
            velocity = velocity == null ? V3.ZERO : velocity;
        }
    }

    public record Terrain(double originX, double originZ, double cell, int size, short[] heights,
                          double groundY, double clearance) {
        public static final short UNKNOWN = Short.MIN_VALUE;
        public static final int MAX_SIZE = 48;
        public static final Terrain NONE = new Terrain(0.0, 0.0, 8.0, 0, new short[0], Double.NaN, Double.NaN);

        public Terrain {
            size = Math.max(0, Math.min(MAX_SIZE, size));
            final short[] copy = new short[size * size];
            if (heights != null) {
                System.arraycopy(heights, 0, copy, 0, Math.min(heights.length, copy.length));
                for (int i = heights.length; i < copy.length; i++) {
                    copy[i] = UNKNOWN;
                }
            } else {
                java.util.Arrays.fill(copy, UNKNOWN);
            }
            heights = copy;
            cell = cell > 0.0 ? cell : 8.0;
        }

        public double heightAt(double x, double z) {
            if (this.size == 0) {
                return Double.NaN;
            }
            final double fx = (x - this.originX) / this.cell - 0.5;
            final double fz = (z - this.originZ) / this.cell - 0.5;
            final int ix = (int) Math.floor(fx);
            final int iz = (int) Math.floor(fz);
            final double tx = fx - ix;
            final double tz = fz - iz;
            final double h00 = sample(ix, iz);
            final double h10 = sample(ix + 1, iz);
            final double h01 = sample(ix, iz + 1);
            final double h11 = sample(ix + 1, iz + 1);
            if (Double.isNaN(h00) || Double.isNaN(h10) || Double.isNaN(h01) || Double.isNaN(h11)) {
                final double near = sample((int) Math.round(fx), (int) Math.round(fz));
                return near;
            }
            final double top = h00 + (h10 - h00) * tx;
            final double bottom = h01 + (h11 - h01) * tx;
            return top + (bottom - top) * tz;
        }

        public double sample(int ix, int iz) {
            if (ix < 0 || iz < 0 || ix >= this.size || iz >= this.size) {
                return Double.NaN;
            }
            final short h = this.heights[iz * this.size + ix];
            return h == UNKNOWN ? Double.NaN : h;
        }
    }

    public record Route(String name, List<Waypoint> points, int active) {
        public static final int MAX_POINTS = 128;
        public static final Route NONE = new Route("", List.of(), 0);

        public Route {
            name = clip(name, 32);
            points = capped(points, MAX_POINTS);
            active = Math.max(0, Math.min(Math.max(0, points.size() - 1), active));
        }

        public boolean isEmpty() {
            return this.points.isEmpty();
        }
    }

    public record Waypoint(V3 position, double speed) {
        public Waypoint {
            position = position == null ? V3.NAN : position;
        }
    }

    public record Trail(List<Fix> fixes) {
        public static final int MAX_FIXES = 512;
        public static final Trail EMPTY = new Trail(List.of());

        public Trail {
            fixes = capped(fixes, MAX_FIXES);
        }
    }

    public record Fix(V3 position, long gameTime) {
    }

    public record History(int periodTicks, long lastTick, float[] altitude, float[] airspeed,
                          float[] verticalSpeed, float[] support, float[] crossTrack) {
        public static final int MAX_SAMPLES = 150;
        public static final History EMPTY = new History(40, 0L, new float[0], new float[0], new float[0],
                new float[0], new float[0]);

        public History {
            periodTicks = Math.max(1, periodTicks);
            final int n = Math.min(MAX_SAMPLES, minLength(altitude, airspeed, verticalSpeed, support, crossTrack));
            altitude = tail(altitude, n);
            airspeed = tail(airspeed, n);
            verticalSpeed = tail(verticalSpeed, n);
            support = tail(support, n);
            crossTrack = tail(crossTrack, n);
        }

        public int size() {
            return this.altitude.length;
        }
    }

    public record Envelope(double seconds, double topSpeed, double bestClimb, double maxSink, double maxYawRate,
                           double maxAccel, double maxDecel, float[] turnRadius, double bucketWidth) {
        public static final int BUCKETS = 16;
        public static final Envelope UNKNOWN = new Envelope(0.0, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, nanArray(BUCKETS), 2.0);

        public Envelope {
            final float[] copy = nanArray(BUCKETS);
            if (turnRadius != null) {
                System.arraycopy(turnRadius, 0, copy, 0, Math.min(BUCKETS, turnRadius.length));
            }
            turnRadius = copy;
            bucketWidth = bucketWidth > 0.0 ? bucketWidth : 2.0;
        }

        public boolean known() {
            return this.seconds > 0.0;
        }
    }

    public record Silhouette(double cell, int cols, int sideRows, int topRows, double originF, double originU,
                             double originR, byte[] side, byte[] top, byte[] envSide, byte[] envTop) {
        public static final int MAX_CELLS = 96;
        public static final Silhouette NONE = new Silhouette(1.0, 0, 0, 0, 0.0, 0.0, 0.0, new byte[0], new byte[0]);

        public Silhouette(double cell, int cols, int sideRows, int topRows, double originF, double originU,
                          double originR, byte[] side, byte[] top) {
            this(cell, cols, sideRows, topRows, originF, originU, originR, side, top, null, null);
        }

        public Silhouette {
            cols = Math.max(0, Math.min(MAX_CELLS, cols));
            sideRows = Math.max(0, Math.min(MAX_CELLS, sideRows));
            topRows = Math.max(0, Math.min(MAX_CELLS, topRows));
            side = fitBits(side, cols * sideRows);
            top = fitBits(top, cols * topRows);

            envSide = envSide == null || envSide.length == 0 ? new byte[0] : fitBits(envSide, cols * sideRows);
            envTop = envTop == null || envTop.length == 0 ? new byte[0] : fitBits(envTop, cols * topRows);
            cell = cell > 0.0 ? cell : 1.0;
        }

        public Silhouette withEnvelope(byte[] side, byte[] top) {
            return new Silhouette(this.cell, this.cols, this.sideRows, this.topRows, this.originF, this.originU,
                    this.originR, this.side, this.top, side, top);
        }

        public boolean isEmpty() {
            return this.cols == 0;
        }

        public boolean hasEnvelope() {
            return this.envSide.length > 0 || this.envTop.length > 0;
        }

        public boolean sideAt(int col, int row) {
            return bit(this.side, row * this.cols + col, col, row, this.sideRows);
        }

        public boolean topAt(int col, int row) {
            return bit(this.top, row * this.cols + col, col, row, this.topRows);
        }

        public boolean envSideAt(int col, int row) {
            return this.envSide.length > 0 && bit(this.envSide, row * this.cols + col, col, row, this.sideRows);
        }

        public boolean envTopAt(int col, int row) {
            return this.envTop.length > 0 && bit(this.envTop, row * this.cols + col, col, row, this.topRows);
        }

        private boolean bit(byte[] bits, int index, int col, int row, int rows) {
            if (col < 0 || row < 0 || col >= this.cols || row >= rows) {
                return false;
            }
            return (bits[index >> 3] & (1 << (index & 7))) != 0;
        }

        public double length() {
            return this.cols * this.cell;
        }
    }

    public static final long NO_TICK = Long.MIN_VALUE;

    public record Helm(String mode, String leave, String pilot, long since, String feel, double order, double engine,
                       int rings, boolean braking, double heldAltitude, double flownAltitude, double heldHeading,
                       double heldSpeed, double fullAhead, double ceiling, double balloonCeiling, double cap,
                       double worldTop, double liftUse, boolean liftShort, boolean weighing, double trim, int staff,
                       V3 anchor, Guard guard, String intent, boolean intentAlert, List<String> notes,
                       double thrustAhead, double thrustAstern, double thrustTurn) {
        public static final int MAX_NOTES = 4;
        public static final int MAX_NOTE = 180;
        public static final Helm NONE = new Helm("", "", "", NO_TICK, "", 0.0, 0.0, 0, false, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, false,
                false, 0.0, 0, V3.NAN, Guard.NONE, "", false, List.of(), Double.NaN, Double.NaN, Double.NaN);

        public Helm {
            mode = clip(mode, 16);
            leave = clip(leave, 16);
            pilot = clip(pilot, 32);
            feel = clip(feel, 12);
            anchor = anchor == null ? V3.NAN : anchor;
            guard = guard == null ? Guard.NONE : guard;
            intent = clip(intent, MAX_NOTE);
            final List<String> kept = new ArrayList<>();
            if (notes != null) {
                for (final String n : notes) {
                    if (n != null && !n.isBlank() && kept.size() < MAX_NOTES) {
                        kept.add(clip(n, MAX_NOTE));
                    }
                }
            }
            notes = List.copyOf(kept);
        }

        public boolean known() {
            return !this.mode.isEmpty();
        }

        public boolean moored() {
            return "MOORED".equals(this.mode);
        }

        public boolean atHelm() {
            return "HELM".equals(this.mode);
        }

        public boolean unattended() {
            return "ANCHOR".equals(this.mode) || "KEEP_COURSE".equals(this.mode) || "GO_TO".equals(this.mode);
        }

        public boolean flying() {
            return atHelm() || unattended();
        }

        public Helm withTelegraph(double order, double engine) {
            return new Helm(this.mode, this.leave, this.pilot, this.since, this.feel, order, engine, this.rings,
                    this.braking, this.heldAltitude, this.flownAltitude, this.heldHeading, this.heldSpeed, this.fullAhead,
                    this.ceiling, this.balloonCeiling, this.cap, this.worldTop, this.liftUse, this.liftShort,
                    this.weighing, this.trim, this.staff, this.anchor, this.guard, this.intent, this.intentAlert,
                    this.notes, this.thrustAhead, this.thrustAstern, this.thrustTurn);
        }
    }

    public record Guard(String verdict, double floor, double speedCap, double groundY, double distance, double peakY,
                        double peakAt, double seen) {
        public static final Guard NONE = new Guard("CLEAR", Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.POSITIVE_INFINITY);

        public Guard {
            verdict = verdict == null || verdict.isBlank() ? "CLEAR" : clip(verdict, 8);
        }

        public boolean acting() {
            return !"CLEAR".equals(this.verdict);
        }
    }

    public record Parts(List<Part> parts, Learned learned, boolean bridge) {
        public static final int MAX = 96;
        public static final Parts NONE = new Parts(List.of(), Learned.NONE, false);

        public Parts {
            parts = capped(parts, MAX);
            learned = learned == null ? Learned.NONE : learned;
        }

        public List<Part> of(int kind) {
            final List<Part> out = new ArrayList<>();
            for (final Part p : this.parts) {
                if (p.kind() == kind) {
                    out.add(p);
                }
            }
            return out;
        }

        public boolean isEmpty() {
            return this.parts.isEmpty();
        }
    }

    public record Part(int kind, String name, String job, int state, V3 at, V3 dir, double value, double extra) {
        public static final int GOVERNOR = 0;
        public static final int THRUSTER = 1;
        public static final int FIN = 2;
        public static final int LAMP = 3;
        public static final int TOOL = 4;
        public static final int GUN = 5;

        public static final int OK = 0;
        public static final int STARVED = 1;
        public static final int OVERSTRESSED = 2;
        public static final int FOREIGN = 3;
        public static final int OFF = 4;

        public static final int NO_FUEL = 5;

        public static final int NO_POWER = 6;

        public static final int STUCK = 7;

        public static final int BRIDGE = 8;

        public static final int RECEIVER = 0;
        public static final int WHITE = 1;
        public static final int RED = 2;
        public static final int AMBER = 3;

        public Part {
            name = clip(name, 24);
            job = clip(job, 16);
            at = at == null ? V3.ZERO : at;
            dir = dir == null || dir.length() < 1.0e-9 ? V3.UP : dir.normalized();
        }

        public boolean engine() {
            return this.kind == GOVERNOR || this.kind == THRUSTER;
        }

        public boolean trouble() {
            return this.state == STARVED || this.state == OVERSTRESSED || this.state == FOREIGN
                    || this.state == NO_FUEL || this.state == NO_POWER || this.state == STUCK;
        }
    }

    public record Learned(double drag, double wing, double lean, double hidden, double turnDrag, int propsKnown,
                          int props) {
        public static final Learned NONE = new Learned(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0, 0);

        public boolean known() {
            return Double.isFinite(this.drag);
        }
    }

    public record Voyage(long takeoff, double seconds, double distance, double highest, int tracePeriod,
                         long traceLast, float[] trace, List<Event> events, Leg last) {
        public static final int MAX_TRACE = 240;
        public static final int MAX_EVENTS = 48;
        public static final Voyage NONE = new Voyage(NO_TICK, 0.0, 0.0, Double.NaN, 20, 0L, new float[0], List.of(),
                null);

        public Voyage {
            tracePeriod = Math.max(1, tracePeriod);
            trace = trace == null ? new float[0] : tail(trace, Math.min(MAX_TRACE, trace.length));
            events = tailList(events, MAX_EVENTS);
        }

        public boolean aloft() {
            return this.takeoff != NO_TICK;
        }
    }

    public record Event(long tick, int kind, int level, String text, float altitude) {
        public static final int TAKEOFF = 0;
        public static final int LANDED = 1;
        public static final int ORDER = 2;
        public static final int MODE = 3;
        public static final int PILOT = 4;
        public static final int WEIGHED = 5;
        public static final int GUARD = 6;
        public static final int ALERT = 7;
        public static final int ARRIVED = 8;
        public static final int LOAD = 9;

        public Event {
            text = clip(text, 64);
        }
    }

    public record Leg(long takeoff, long landed, double seconds, double distance, double highest, int alerts,
                      int orders) {
    }

    static String clip(String s, int max) {
        if (s == null) {
            return "";
        }
        final String t = s.strip();

        return t.length() <= max ? t : t.substring(0, max).strip();
    }

    static <T> List<T> capped(List<T> in, int max) {
        if (in == null || in.isEmpty()) {
            return List.of();
        }
        final List<T> out = new ArrayList<>(Math.min(max, in.size()));
        for (final T t : in) {
            if (t == null) {
                continue;
            }
            if (out.size() >= max) {
                break;
            }
            out.add(t);
        }
        return List.copyOf(out);
    }

    private static int minLength(float[]... arrays) {
        int n = Integer.MAX_VALUE;
        for (final float[] a : arrays) {
            n = Math.min(n, a == null ? 0 : a.length);
        }
        return n == Integer.MAX_VALUE ? 0 : n;
    }

    private static float[] tail(float[] a, int n) {
        if (a == null) {
            return new float[0];
        }
        final float[] out = new float[n];
        System.arraycopy(a, a.length - n, out, 0, n);
        return out;
    }

    static <T> List<T> tailList(List<T> in, int max) {
        if (in == null || in.isEmpty()) {
            return List.of();
        }
        final List<T> out = new ArrayList<>(Math.min(max, in.size()));
        for (int i = Math.max(0, in.size() - max); i < in.size(); i++) {
            if (in.get(i) != null) {
                out.add(in.get(i));
            }
        }
        return List.copyOf(out);
    }

    static float[] nanArray(int n) {
        final float[] a = new float[n];
        java.util.Arrays.fill(a, Float.NaN);
        return a;
    }

    private static byte[] fitBits(byte[] bits, int count) {
        final byte[] out = new byte[(count + 7) >> 3];
        if (bits != null) {
            System.arraycopy(bits, 0, out, 0, Math.min(bits.length, out.length));
        }
        return out;
    }
}
