package com.createstats.ground;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.createstats.aerial.Quat;
import com.createstats.aerial.V3;
import com.createstats.vccmk4.core.BodyRole;
import com.createstats.vccmk4.core.DriveModel;
import com.createstats.vccmk4.core.JetKind;
import com.createstats.vccmk4.core.JetSupply;
import com.createstats.vccmk4.core.LinkKind;
import com.createstats.vccmk4.core.LinkSource;
import com.createstats.vccmk4.core.SteerPreset;
import com.createstats.vccmk4.core.TrailerMode;

public record GroundSnapshot(

        long gameTime,
        Identity identity,
        Body body,
        Log log,
        Drive drive,
        Setup setup,
        Mass mass,
        List<Wheel> wheels,
        List<Governor> governors,
        List<Jet> jets,

        List<Unit> units,
        List<Tie> ties,
        Lights lights,
        List<Machine> machines,

        List<String> problems,
        Terrain terrain,
        Odometer odometer,
        Trail trail,
        History history,
        Events events,
        Envelope envelope,
        List<Shape> shapes) {
    public static final int MAX_NAME = 48;
    public static final int MAX_WHEELS = 48;
    public static final int MAX_GOVERNORS = 24;
    public static final int MAX_JETS = 24;
    public static final int MAX_UNITS = 8;
    public static final int MAX_TIES = 16;
    public static final int MAX_LAMPS = 32;
    public static final int MAX_MACHINES = 12;
    public static final int MAX_PROBLEMS = 12;
    public static final int MAX_PROBLEM_LENGTH = 200;

    public GroundSnapshot {
        identity = identity == null ? Identity.NONE : identity;
        body = body == null ? Body.AT_REST : body;
        log = log == null ? Log.NONE : log;
        drive = drive == null ? Drive.IDLE : drive;
        setup = setup == null ? Setup.DEFAULT : setup;
        mass = mass == null ? Mass.UNKNOWN : mass;
        wheels = capped(wheels, MAX_WHEELS);
        governors = capped(governors, MAX_GOVERNORS);
        jets = capped(jets, MAX_JETS);
        units = capped(units, MAX_UNITS);
        ties = capped(ties, MAX_TIES);
        lights = lights == null ? Lights.DARK : lights;
        machines = capped(machines, MAX_MACHINES);
        problems = clipAll(capped(problems, MAX_PROBLEMS), MAX_PROBLEM_LENGTH);
        terrain = terrain == null ? Terrain.NONE : terrain;
        odometer = odometer == null ? Odometer.ZERO : odometer;
        trail = trail == null ? Trail.EMPTY : trail;
        history = history == null ? History.EMPTY : history;
        events = events == null ? Events.NONE : events;
        envelope = envelope == null ? Envelope.UNKNOWN : envelope;
        shapes = capped(shapes, MAX_UNITS);
    }

    public static GroundSnapshot empty(long gameTime) {
        return new GroundSnapshot(gameTime, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null);
    }

    public boolean accepts(GroundCommand command) {
        return command != null && (this.identity.commands() & command.bit()) != 0;
    }

    public GroundSnapshot withRecorded(Odometer odometer, Trail trail, History history, Events events,
                                       Envelope envelope) {
        return new GroundSnapshot(this.gameTime, this.identity, this.body, this.log, this.drive, this.setup,
                this.mass, this.wheels, this.governors, this.jets, this.units, this.ties, this.lights,
                this.machines, this.problems, this.terrain, odometer, trail, history, events, envelope,
                this.shapes);
    }

    public GroundSnapshot withBody(Body body) {
        return new GroundSnapshot(this.gameTime, this.identity, body, this.log, this.drive, this.setup, this.mass,
                this.wheels, this.governors, this.jets, this.units, this.ties, this.lights, this.machines,
                this.problems, this.terrain, this.odometer, this.trail, this.history, this.events, this.envelope,
                this.shapes);
    }

    public Unit tractor() {
        return this.units.isEmpty() ? Unit.TRACTOR : this.units.get(0);
    }

    public record Identity(String name, UUID vehicleId, String profile, VehicleKind kind, int commands,
                           boolean onCraft, boolean brakesAvailable, String towedBy) {
        public static final Identity NONE = new Identity("", null, "", VehicleKind.EMPTY, 0, false, true, "");

        public Identity {
            name = clip(name, MAX_NAME);
            profile = clip(profile, 64);
            kind = kind == null ? VehicleKind.EMPTY : kind;
            towedBy = clip(towedBy, 24);
        }
    }

    public record Drive(String driver, long helmTick, boolean helmStill, boolean driving, boolean parked,
                        boolean towed, double throttle, double steer, boolean braking, boolean boosting,
                        boolean holding, char gear, double lockDeg, double lift, String alert, int machines,
                        float[] controls) {
        public static final Drive IDLE = new Drive("", Long.MIN_VALUE, false, false, false, false, 0.0, 0.0, false,
                false, false, '-', 30.0, 0.0, "", 0, new float[0]);

        public Drive {
            driver = clip(driver, 24);
            alert = clip(alert, 24);
            controls = controls == null ? new float[0] : controls.clone();
        }
    }

    public record Setup(SteerPreset preset, boolean presetAuto, boolean articulated, boolean jetDrive,
                        DriveModel.Options options, int axles, boolean greeting) {
        public static final Setup DEFAULT = new Setup(SteerPreset.FRONT, true, false, false,
                DriveModel.Options.DEFAULT, 0, true);

        public Setup {
            preset = preset == null ? SteerPreset.FRONT : preset;
            options = options == null ? DriveModel.Options.DEFAULT : options.sane();
        }
    }

    public record Body(V3 position, Quat orientation, V3 nose, V3 velocity, double yawRate, double forwardSpeed,
                       V3 accel, V3 gravity) {
        public static final V3 NORTH = new V3(0.0, 0.0, -1.0);
        public static final V3 STANDARD_GRAVITY = new V3(0.0, -11.0, 0.0);
        public static final Body AT_REST = new Body(V3.ZERO, Quat.IDENTITY, NORTH, V3.ZERO, 0.0, 0.0, V3.ZERO,
                STANDARD_GRAVITY);

        public Body {
            position = position == null ? V3.ZERO : position;
            orientation = orientation == null ? Quat.IDENTITY : orientation.normalized();
            nose = nose == null || nose.length() < 1.0e-6 ? NORTH : nose.normalized();
            velocity = velocity == null ? V3.ZERO : velocity;
            accel = accel == null ? V3.ZERO : accel;
            gravity = gravity == null || gravity.length() < 1.0e-6 ? STANDARD_GRAVITY : gravity;
        }
    }

    public record Log(double peak, double lastImpact, int impactAge, double tripImpact, double airNow,
                      double lastAir) {
        public static final Log NONE = new Log(0.0, 0.0, -1, 0.0, 0.0, 0.0);
    }

    public record Mass(double mass, double trainMass, double yawInertia, boolean comMeasured, V3 console,
                       int blocks) {
        public static final Mass UNKNOWN = new Mass(Double.NaN, Double.NaN, Double.NaN, false, V3.ZERO, -1);

        public Mass {
            console = console == null ? V3.ZERO : console;
        }
    }

    public record Wheel(int unit, int axle, int governor, float x, float y, float z, boolean driven, boolean steered,
                        float radius, float capMass, float strength, float angle, float applied, float actual,
                        float slip, float grip, float load, boolean airborne, int brake) {
    }

    public enum Supply {
        OK,

        IDLE,

        NO_ROTATION,

        OVERSTRESSED,

        FOREIGN,

        EXCLUDED
    }

    public record Governor(String name, int unit, float x, float z, int applied, int commanded, float ratio,
                           int wheels, int jets, boolean machine, Supply supply, boolean starved) {
        public Governor {
            name = clip(name, 8);
            supply = supply == null ? Supply.IDLE : supply;
        }
    }

    public record Jet(String name, int unit, float x, float y, float z, V3 axis, JetKind kind, int job,
                      float command, float force, float ceiling, float clear, JetSupply supply, boolean lit,
                      boolean foreign, float rpm) {
        public Jet {
            name = clip(name, 8);
            axis = axis == null ? new V3(0.0, 0.0, 1.0) : axis;
            kind = kind == null ? JetKind.PROPELLER : kind;
            supply = supply == null ? JetSupply.NONE : supply;
        }
    }

    public record Unit(int index, int parent, BodyRole role, LinkKind link, TrailerMode mode, boolean placed,
                       boolean foreign, float x, float z, float yaw, float articulation, float rate, float reading,
                       float force, float snapShare, int flags, int wheels, float mass, String name) {
        public static final int FLAG_SWAYING = 1;
        public static final int FLAG_SNAPPING = 2;
        public static final int FLAG_POWERED = 4;
        public static final int FLAG_DOCKING = 8;
        public static final int FLAG_OVERRUN = 16;
        public static final int FLAG_LOST = 32;

        public static final Unit TRACTOR = new Unit(0, -1, BodyRole.PART, LinkKind.JOINT, TrailerMode.OFF, true,
                false, 0f, 0f, 0f, Float.NaN, 0f, Float.NaN, Float.NaN, Float.NaN, 0, 0, Float.NaN, "B1");

        public Unit {
            role = role == null ? BodyRole.PART : role;
            link = link == null ? LinkKind.JOINT : link;
            mode = mode == null ? TrailerMode.OFF : mode;
            name = clip(name, 8);
        }

        public boolean has(int flag) {
            return (this.flags & flag) != 0;
        }

        public boolean isTractor() {
            return this.index == 0;
        }
    }

    public record Tie(LinkKind kind, int a, float ax, float az, int b, float bx, float bz, float reading) {
        public Tie {
            kind = kind == null ? LinkKind.ROPE : kind;
        }
    }

    public record Lights(boolean driving, boolean braking, boolean reversing, boolean boosting, float steer,
                         boolean beam, List<Lamp> lamps) {
        public static final Lights DARK = new Lights(false, false, false, false, 0f, false, List.of());

        public Lights {
            lamps = capped(lamps, MAX_LAMPS);
        }
    }

    public record Lamp(int unit, float x, float y, float z, int colour, LinkSource source, int strength,
                       boolean proposed) {
        public Lamp {
            source = source == null ? LinkSource.OFF : source;
        }
    }

    public record Machine(String name, int unit, float x, float z, String drives, boolean runner, int input,
                          float output, int applied, boolean foreign) {
        public Machine {
            name = clip(name, 8);
            drives = clip(drives, 64);
        }
    }

    public record Terrain(double originX, double originZ, double cell, int size, short[] heights,
                          double groundY) {
        public static final short UNKNOWN = Short.MIN_VALUE;
        public static final int MAX_SIZE = 48;
        public static final Terrain NONE = new Terrain(0.0, 0.0, 4.0, 0, new short[0], Double.NaN);

        public Terrain {
            size = Math.max(0, Math.min(MAX_SIZE, size));
            final short[] copy = new short[size * size];
            Arrays.fill(copy, UNKNOWN);
            if (heights != null) {
                System.arraycopy(heights, 0, copy, 0, Math.min(heights.length, copy.length));
            }
            heights = copy;
            cell = cell > 0.0 ? cell : 4.0;
        }

        public double sample(int ix, int iz) {
            if (ix < 0 || iz < 0 || ix >= this.size || iz >= this.size) {
                return Double.NaN;
            }
            final short h = this.heights[iz * this.size + ix];
            return h == UNKNOWN ? Double.NaN : h;
        }

        public double heightAt(double x, double z) {
            if (this.size == 0) {
                return Double.NaN;
            }
            return sample((int) Math.floor((x - this.originX) / this.cell), (int) Math.floor((z - this.originZ) / this.cell));
        }
    }

    public record Odometer(double odometer, double trip, long tripTicks, double topSpeed, double tripTop,
                           long tripStart) {
        public static final Odometer ZERO = new Odometer(0.0, 0.0, 0L, Double.NaN, Double.NaN, 0L);
    }

    public record Trail(List<Fix> fixes) {
        public static final int MAX_FIXES = 512;
        public static final Trail EMPTY = new Trail(List.of());

        public Trail {
            fixes = capped(fixes, MAX_FIXES);
        }
    }

    public record Fix(V3 position, long gameTime) {
        public Fix {
            position = position == null ? V3.NAN : position;
        }
    }

    public record History(int periodTicks, long lastTick, float[] speed, float[] throttle, float[] brake,
                          float[] steer, float[] latG, float[] lonG, float[] slip, float[] pitch) {
        public static final int MAX_SAMPLES = 180;
        public static final History EMPTY = new History(10, 0L, new float[0], new float[0], new float[0],
                new float[0], new float[0], new float[0], new float[0], new float[0]);

        public History {
            periodTicks = Math.max(1, periodTicks);
            final int n = Math.min(MAX_SAMPLES, minLength(speed, throttle, brake, steer, latG, lonG, slip, pitch));
            speed = tail(speed, n);
            throttle = tail(throttle, n);
            brake = tail(brake, n);
            steer = tail(steer, n);
            latG = tail(latG, n);
            lonG = tail(lonG, n);
            slip = tail(slip, n);
            pitch = tail(pitch, n);
        }

        public int size() {
            return this.speed.length;
        }
    }

    public enum EventKind {
        HELM,

        LEFT,

        IMPACT,

        AIRTIME,

        COUPLED,

        LOST,

        PROBLEM,

        CLEARED,

        TOP_SPEED,

        TRIP
    }

    public record Event(long tick, EventKind kind, float value, String text, V3 position) {
        public Event {
            kind = kind == null ? EventKind.PROBLEM : kind;
            text = clip(text, 80);
            position = position == null ? V3.NAN : position;
        }
    }

    public record Events(List<Event> list) {
        public static final int MAX_EVENTS = 48;
        public static final Events NONE = new Events(List.of());

        public Events {
            list = capped(list, MAX_EVENTS);
        }
    }

    public record Envelope(double seconds, double topSpeed, double maxAccel, double maxDecel, double maxLatG,
                           float[] turnRadius, double bucketWidth) {
        public static final int BUCKETS = 12;
        public static final Envelope UNKNOWN = new Envelope(0.0, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                nanArray(BUCKETS), 2.0);

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

    public record Shape(int unit, double cell, int cols, int topRows, int sideRows, double originZ, double originX,
                        double originY, byte[] top, byte[] side) {
        public static final int MAX_CELLS = 64;
        public static final Shape NONE = new Shape(0, 1.0, 0, 0, 0, 0.0, 0.0, 0.0, new byte[0], new byte[0]);

        public Shape {
            cols = Math.max(0, Math.min(MAX_CELLS, cols));
            topRows = Math.max(0, Math.min(MAX_CELLS, topRows));
            sideRows = Math.max(0, Math.min(MAX_CELLS, sideRows));
            top = fitBits(top, cols * topRows);
            side = fitBits(side, cols * sideRows);
            cell = cell > 0.0 ? cell : 1.0;
        }

        public boolean isEmpty() {
            return this.cols == 0;
        }

        public boolean topAt(int col, int row) {
            return bit(this.top, col, row, this.topRows);
        }

        public boolean sideAt(int col, int row) {
            return bit(this.side, col, row, this.sideRows);
        }

        private boolean bit(byte[] bits, int col, int row, int rows) {
            if (col < 0 || row < 0 || col >= this.cols || row >= rows) {
                return false;
            }
            final int index = row * this.cols + col;
            return (bits[index >> 3] & (1 << (index & 7))) != 0;
        }

        public double length() {
            return this.cols * this.cell;
        }

        public double width() {
            return this.topRows * this.cell;
        }
    }

    static String clip(String s, int max) {
        if (s == null) {
            return "";
        }
        final String t = s.strip();

        return t.length() <= max ? t : t.substring(0, max).strip();
    }

    static List<String> clipAll(List<String> in, int max) {
        final List<String> out = new ArrayList<>(in.size());
        for (final String s : in) {
            out.add(clip(s, max));
        }
        return List.copyOf(out);
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

    static float[] nanArray(int n) {
        final float[] a = new float[n];
        Arrays.fill(a, Float.NaN);
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
