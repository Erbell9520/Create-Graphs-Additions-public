package com.createstats.aerial;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.createstats.aerial.AerialSnapshot.Air;
import com.createstats.aerial.AerialSnapshot.Autopilot;
import com.createstats.aerial.AerialSnapshot.Balloon;
import com.createstats.aerial.AerialSnapshot.Body;
import com.createstats.aerial.AerialSnapshot.Force;
import com.createstats.aerial.AerialSnapshot.Helm;
import com.createstats.aerial.AerialSnapshot.Mass;
import com.createstats.aerial.AerialSnapshot.Part;
import com.createstats.aerial.AerialSnapshot.Parts;
import com.createstats.aerial.AerialSnapshot.Propeller;
import com.createstats.aerial.AerialSnapshot.Silhouette;
import com.createstats.aerial.AerialSnapshot.Target;
import com.createstats.aerial.AerialSnapshot.Terrain;
import com.createstats.vccmk4.air.core.AerialPilot;
import com.createstats.vccmk4.air.core.FlightIntent;
import com.createstats.vccmk4.air.core.LeaveMode;
import com.createstats.vccmk4.air.core.TerrainGuard;

public final class AerialRealistic {
    static final UUID SHIP = new UUID(0x53da7fe2_0000_4000L, 0x8000_0000_0000_0001L);
    static final String NAME = "Ship 53da7fe2";
    static final double MASS = 697.5;
    static final double LOAD = 223.0;
    static final double G = 11.0;
    static final double SEA = 63.0;
    static final double GROUND = 64.0;
    static final double HELD = 96.0;
    static final double HEADING = 16.0;

    static final V3 COM = new V3(0.5, 3.0, 0.5);
    static final V3 NOSE = new V3(0.0, 0.0, -1.0);
    static final V3 LODESTONE = new V3(448.5, 63.5, 540.5);
    static final V3 START = new V3(160.0, 0.0, 720.0);
    static final long T0 = 1_200_000L;
    static final int PROPS = 9;

    private static Silhouette silhouette;
    private static AerialRecorder flown;
    private static State now;

    private static V3 hill;

    private AerialRealistic() {
    }

    public static synchronized AerialSnapshot scene(AerialFixtures.Scene scene) {
        fly();
        return switch (scene) {
            case CRUISE -> flown.recorded(snapshot(now));
            case SINKING -> sinking();
            case MOORED -> moored();
        };
    }

    record State(long tick, V3 pos, V3 vel, double heading, String mode, String pilot, double order, int rings,
                 double held, double flown, double gas, double target, int lit, double load, boolean weighing,
                 TerrainGuard.Result guard, List<String> notes, double pitch, double roll, boolean liftShort) {
    }

    static synchronized Silhouette silhouette() {
        if (silhouette == null) {
            final List<V3> all = new ArrayList<>();
            final List<V3> env = new ArrayList<>();
            for (int x = -6; x <= 6; x++) {
                for (int y = 2; y <= 10; y++) {
                    for (int z = -13; z <= 13; z++) {
                        final boolean side = Math.abs(x) == 6 || Math.abs(z) == 13 || y == 10;
                        if (side) {
                            final V3 c = new V3(x + 0.5, y + 0.5, z + 0.5).minus(COM);
                            all.add(c);
                            env.add(c);
                        }
                    }
                }
            }
            for (int z = -12; z <= 12; z++) {
                for (final int x : new int[]{-5, 5}) {
                    all.add(new V3(x + 0.5, 1.5, z + 0.5).minus(COM));
                    all.add(new V3(x + 0.5, -5.5, z + 0.5).minus(COM));
                }
            }
            for (final int z : new int[]{-12, -6, 0, 6, 12}) {
                for (int x = -4; x <= 4; x++) {
                    all.add(new V3(x + 0.5, 1.5, z + 0.5).minus(COM));
                }
            }
            for (final int z : new int[]{-12, 12}) {
                for (final int x : new int[]{-5, 5}) {
                    for (int y = -5; y <= 0; y++) {
                        all.add(new V3(x + 0.5, y + 0.5, z + 0.5).minus(COM));
                    }
                }
                for (int x = -4; x <= 4; x++) {
                    all.add(new V3(x + 0.5, -5.5, z + 0.5).minus(COM));
                }
            }
            for (int x = -2; x <= 2; x++) {
                for (int y = -5; y <= -3; y++) {
                    for (int z = -3; z <= 3; z++) {
                        all.add(new V3(x + 0.5, y + 0.5, z + 0.5).minus(COM));
                    }
                }
            }
            for (int i = 0; i < PROPS; i++) {
                all.add(prop(i));
            }
            final Silhouette sil = AerialSilhouette.of(all, NOSE);
            silhouette = sil.withEnvelope(bits(sil, env, true), bits(sil, env, false));
        }
        return silhouette;
    }

    static byte[] bits(Silhouette sil, List<V3> env, boolean side) {
        final V3 f = NOSE;
        final V3 r = f.cross(V3.UP).normalized();
        final byte[] out = new byte[(sil.cols() * (side ? sil.sideRows() : sil.topRows()) + 7) >> 3];
        for (final V3 b : env) {
            final int col = cell(b.dot(f), sil.originF(), sil.cell(), sil.cols());
            final int row = side ? cell(b.y(), sil.originU(), sil.cell(), sil.sideRows())
                    : cell(b.dot(r), sil.originR(), sil.cell(), sil.topRows());
            final int i = row * sil.cols() + col;
            out[i >> 3] |= (byte) (1 << (i & 7));
        }
        return out;
    }

    private static int cell(double v, double min, double cell, int count) {
        return Math.max(0, Math.min(count - 1, (int) Math.floor((v - min) / cell)));
    }

    static V3 prop(int i) {
        return new V3(0.5, 11.5, -12.0 + i * 3.0 + 0.5).minus(COM);
    }

    static double pressure(double y) {
        return Math.exp(-0.004 * (y - SEA));
    }

    static Terrain terrain(V3 pos) {
        final int size = 24;
        final double cell = 8.0;
        final double ox = Math.floor((pos.x() - size * cell / 2.0) / cell) * cell;
        final double oz = Math.floor((pos.z() - size * cell / 2.0) / cell) * cell;
        final short[] h = new short[size * size];
        for (int iz = 0; iz < size; iz++) {
            for (int ix = 0; ix < size; ix++) {
                final double x = ox + (ix + 0.5) * cell;
                final double z = oz + (iz + 0.5) * cell;

                final double d = hill == null ? Double.POSITIVE_INFINITY : Math.hypot(x - hill.x(), z - hill.z());
                final double y = GROUND + 1.5 * Math.sin(x * 0.03) + 1.0 * Math.cos(z * 0.025)
                        + (d < 60.0 ? 22.0 * Math.cos(d / 60.0 * Math.PI * 0.5) : 0.0);
                h[iz * size + ix] = (short) Math.round(y);
            }
        }
        final Terrain t = new Terrain(ox, oz, cell, size, h, Double.NaN, Double.NaN);
        return new Terrain(ox, oz, cell, size, h, t.heightAt(pos.x(), pos.z()), Double.NaN);
    }

    static AerialSnapshot snapshot(State s) {
        final Silhouette sil = silhouette();
        final double p = pressure(s.pos().y());
        final Quat q = AerialFlightSim.orientation(s.heading(), s.pitch(), s.roll());
        final V3 air = s.vel();
        final V3 fwd = q.rotate(NOSE);
        final double along = air.dot(fwd);
        final boolean moored = "MOORED".equals(s.mode());
        final double rpm = moored ? 0.0 : 64.0 + 192.0 * Math.max(0.0, s.order());
        final List<Propeller> props = new ArrayList<>();
        V3 push = V3.ZERO;
        V3 pushAt = V3.ZERO;
        double pushSum = 0.0;
        for (int i = 0; i < PROPS; i++) {
            final double flow = 0.1 * rpm;
            final double scaling = AirframeMath.airflowScaling(flow, along);
            props.add(new Propeller("G" + (i + 1), "small", prop(i), NOSE, rpm, flow, scaling, p, rpm, 0.04, rpm > 0.0));
            final double t = rpm * scaling * p;
            push = push.plus(fwd.times(t));
            pushAt = pushAt.plus(prop(i).times(t));
            pushSum += t;
        }
        final double thruster = moored ? 0.0 : 267.0;
        push = push.plus(fwd.times(thruster));
        pushAt = pushAt.plus(new V3(0.5, -2.5, 13.5).minus(COM).times(thruster));
        pushSum += thruster;
        final V3 balloonAt = new V3(0.5, 6.5, 0.5).minus(COM);
        final List<Force> forces = new ArrayList<>();
        forces.add(new Force("balloon_lift", new V3(0.0, s.gas() * 1.5 * p * G, 0.0),
                s.pos().plus(q.rotate(balloonAt))));
        if (pushSum > 1.0e-6) {
            forces.add(new Force("propulsion", push, s.pos().plus(q.rotate(pushAt.times(1.0 / pushSum)))));
        }
        final List<Balloon> balloons = List.of(new Balloon("B1", "hot_air", balloonAt, s.gas() * 1.5, s.gas(),
                s.target(), 1924.0, s.target() > s.gas() + 1.0 ? 2.5 * s.lit() : (moored ? -0.8 : 0.0),
                2.5 * s.lit(), p, 6, s.lit(), true));
        final double ceiling = 190.0;
        final double groundClear = s.pos().y() + sil.originU() - terrain(s.pos()).groundY();
        final FlightIntent.Line line = FlightIntent.banner(new FlightIntent.Now(AerialPilot.Mode.valueOf(s.mode()),
                LeaveMode.GO_TO, false, s.pos().y(), s.held(), s.flown(), s.vel().y(), s.heading(),
                "HELM".equals(s.mode()) ? HEADING : Double.NaN, s.vel().horizontalLength(),
                LODESTONE.minus(s.pos()).horizontalLength(), AirframeMath.headingOf(LODESTONE.minus(s.pos())), "LODESTONE",
                groundClear), s.notes().isEmpty() ? null : s.notes().get(0), s.guard());
        final TerrainGuard.Result g = s.guard();
        final Helm helm = new Helm(s.mode(), "GO_TO", s.pilot(), s.tick() - 400L, "STANDARD", s.order(),
                s.order(), s.rings(), Math.abs(s.order()) < 1.0e-6, s.held(), s.flown(),
                "HELM".equals(s.mode()) ? HEADING : Double.NaN, Double.NaN, 5.1, moored ? Double.NaN : ceiling, ceiling,
                Double.NaN, 320.0, moored ? 0.0 : s.liftShort() ? 1.0 : s.gas() / (1924.0 * 0.9 * 0.92), s.liftShort(),
                s.weighing(), 0.0, 0, V3.NAN,
                new AerialSnapshot.Guard(g.verdict().name(), Double.isFinite(g.floor()) ? g.floor() : Double.NaN,
                        Double.isFinite(g.speedCap()) ? g.speedCap() : Double.NaN, g.groundY(), g.distance(), g.peakY(),
                        g.peakAt(), g.seen()),
                line.text(), line.alert(), s.notes(), 2285.0, 1100.0, 12000.0);
        final Autopilot autopilot = moored ? new Autopilot("MOORED", false, Double.NaN, Double.NaN, Double.NaN, V3.NAN,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN)
                : new Autopilot(s.mode(), true, s.held(), Double.NaN, "HELM".equals(s.mode()) ? HEADING : Double.NaN,
                "GO_TO".equals(s.mode()) ? LODESTONE : V3.NAN, pushSum / 2285.0, 0.05, Double.NaN,
                helm.liftUse());
        return new AerialSnapshot(s.tick(), NAME, SHIP, AerialCommand.all(),
                new Body(s.pos(), q, NOSE, s.vel(), V3.ZERO),
                new Air(true, air, p, pressure(s.pos().y() + Air.PROBE), SEA, new V3(0.0, -G, 0.0)),
                new Mass(MASS, new V3(62000.0, 88000.0, 31000.0), 1562, s.load()), forces, props, balloons, null,
                new Target("Lodestone", LODESTONE, "nav_table"), autopilot, null, List.of(), terrain(s.pos()), null, null,
                null, null, sil, helm, parts(s, pushSum, moored), null);
    }

    static Parts parts(State s, double pushSum, boolean moored) {
        final List<Part> out = new ArrayList<>();
        final double each = moored ? 0.0 : (pushSum - 267.0) / PROPS;
        for (int i = 0; i < PROPS; i++) {
            out.add(new Part(Part.GOVERNOR, "G" + (i + 1), "auto", moored ? Part.OFF : Part.OK, prop(i), NOSE,
                    moored ? 0.0 : Math.max(0.0, s.order()), Math.round(each)));
        }
        out.add(new Part(Part.THRUSTER, "T1 thruster", "auto", moored ? Part.OFF : Part.OK,
                new V3(0.5, -2.5, 13.5).minus(COM), NOSE, moored ? 0.0 : 0.4, moored ? 0.0 : 267.0));
        for (final int x : new int[]{-3, 3}) {
            out.add(new Part(Part.GUN, "cannon mount", "battle bridge", Part.BRIDGE,
                    new V3(x + 0.5, -6.5, -8.5).minus(COM), V3.UP, 0.0, 0.0));
        }
        return new Parts(out, new AerialSnapshot.Learned(0.63, 0.0, 0.0, 0.0, 2400.0, 9, 9), true);
    }

    static double padY() {
        return terrain(START).groundY() - silhouette().originU() + 0.05;
    }

    private static void fly() {
        if (flown != null) {
            return;
        }
        hill = null;
        hill = run(null, 165.0);
        final AerialRecorder rec = new AerialRecorder();
        run(rec, Double.NaN);
        flown = rec;
    }

    private static V3 run(AerialRecorder rec, double at) {
        V3 pos = new V3(START.x(), padY(), START.z());
        final double pad = pos.y();
        double heading = HEADING;
        long t = T0;
        double load = 0.0;
        int rings = 0;
        double order = 0.0;
        String mode = "HELM";
        String pilot = "Alex";
        State s = null;
        V3 found = null;
        final int dt = AerialRecorder.SAMPLE_TICKS;
        final double h = dt / 20.0;
        for (int step = 0; step < 20 * 60 * 5 / dt; step++) {
            final double sec = (t - T0) / 20.0;
            double vy = 0.0;
            double speed = 0.0;
            double held;
            double flownTo = HELD;
            double gas;
            boolean weighing = false;
            TerrainGuard.Result guard = TerrainGuard.Result.NONE;
            if (sec < 30.0) {
                weighing = sec > 4.0;
                load = Math.min(LOAD, Math.max(0.0, (sec - 4.0) / 24.0 * LOAD));
                gas = 520.0 + sec * 5.0;
                held = pad + 3.0;
                flownTo = held;
            } else {
                if (sec >= 34.0 && order == 0.0) {
                    order = 0.25;
                    rings++;
                }
                if (sec >= 70.0 && order == 0.25 && sec < 200.0) {
                    order = 0.5;
                    rings++;
                }
                if (sec >= 120.0 && sec < 200.0) {
                    mode = "GO_TO";
                    pilot = "";
                } else if (sec >= 200.0) {
                    mode = "HELM";
                    pilot = "Alex";
                    if (order == 0.5) {
                        order = 0.25;
                        rings++;
                    }
                }
                speed = order * 2.0 * 5.1 * 0.75;
                if (sec >= 150.0 && sec < 175.0 && hill != null) {
                    guard = new TerrainGuard.Result(TerrainGuard.Verdict.CLIMB, 104.0, Double.POSITIVE_INFINITY, 86.0,
                            60.0, 86.0, 60.0, Double.POSITIVE_INFINITY);
                    flownTo = 104.0;
                }
                final double aim = sec < 34.0 ? pad + 3.0 : flownTo;
                held = sec < 34.0 ? aim : HELD;

                final double off = aim - pos.y();
                vy = Math.signum(off) * Math.min(Math.abs(off) > 0.2 ? 3.0 : 0.0, Math.sqrt(2.0 * 0.4 * Math.abs(off)));

                gas = (MASS + load) / (1.5 * pressure(pos.y())) * (1.0 + 0.012 * Math.signum(vy));
                if (sec >= 120.0 && sec < 200.0) {
                    heading = AirframeMath.headingOf(LODESTONE.minus(pos));
                }
            }
            if (Double.isFinite(at) && found == null && sec >= at) {
                found = pos;
            }
            final double hr = Math.toRadians(heading);
            final V3 vel = new V3(Math.sin(hr) * speed, vy, -Math.cos(hr) * speed);
            pos = pos.plus(vel.times(h));
            s = new State(t, pos, vel, heading, mode, pilot, order, rings, held, flownTo, gas, gas + 4.0, 4, load,
                    weighing, guard, List.of(), sec < 30.0 ? 0.0 : 1.2, sec < 30.0 ? 0.0 : -0.3, false);
            if (rec != null) {
                rec.observe(snapshot(s));
            }
            t += dt;
        }
        if (rec != null) {
            now = s;
        }
        return found;
    }

    private static AerialSnapshot sinking() {
        final AerialRecorder rec = AerialRecorder.load(flown.save());
        final State base = now;
        State s = base;
        final String note = "Lift short: even full burners cannot hold this. Lighten the ship or add burners.";
        for (int i = 0; i < 20; i++) {
            final V3 pos = base.pos().plus(new V3(0.0, -6.0 - i * 0.28, 0.0));
            final double gas = (MASS + LOAD) / (1.5 * pressure(pos.y())) * 0.94;
            s = new State(base.tick() + 40L + i * 4L, pos, new V3(base.vel().x(), -1.4, base.vel().z()), base.heading(),
                    "HELM", "Alex", base.order(), base.rings(), HELD, HELD, gas, 780.0, 3, LOAD, false,
                    TerrainGuard.Result.NONE, List.of(note), 2.0, -0.4, true);
            rec.observe(snapshot(s));
        }
        return rec.recorded(snapshot(s));
    }

    private static AerialSnapshot moored() {
        final AerialRecorder rec = AerialRecorder.load(flown.save());
        final State base = now;

        final double pad = terrain(base.pos()).groundY() - silhouette().originU() + 0.05;
        V3 pos = base.pos();
        State s = base;
        final String banked = "Moored with the burners banked: your own redstone would light them and lift the ship with"
                + " nobody at the helm. Set a balloon By hand on the Lift page to fly it on your own redstone.";
        long t = base.tick() + 20L;

        while (pos.y() > pad + 0.05) {
            final double room = pos.y() - pad;
            final double vy = -Math.min(2.0, Math.sqrt(2.0 * 0.25 * room) + 0.02);
            pos = new V3(pos.x(), Math.max(pad, pos.y() + vy * 0.2), pos.z());
            final double gas = (MASS + LOAD) / (1.5 * pressure(pos.y())) * 0.99;

            final boolean rung = t - base.tick() > 30L;
            s = new State(t, pos, new V3(0.0, vy, 0.0), base.heading(), "HELM", "Alex", rung ? 0.0 : base.order(),
                    rung ? base.rings() + 1 : base.rings(), pad, pad, gas, gas, 2, LOAD, false, TerrainGuard.Result.NONE,
                    List.of(), 0.0, 0.0, false);
            rec.observe(snapshot(s));
            t += 4L;
        }
        pos = new V3(pos.x(), pad, pos.z());
        for (int i = 0; i < 40; i++) {
            s = new State(t, pos, V3.ZERO, base.heading(), "MOORED", "", 0.0, base.rings() + 1, Double.NaN, Double.NaN,
                    180.0, 0.0, 0, LOAD, false, TerrainGuard.Result.NONE, List.of(banked), 0.0, 0.0, false);
            rec.observe(snapshot(s));
            t += 4L;
        }
        return rec.recorded(snapshot(s));
    }
}
