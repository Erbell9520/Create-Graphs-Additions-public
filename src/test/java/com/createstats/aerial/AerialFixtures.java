package com.createstats.aerial;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.createstats.aerial.AerialSnapshot.Air;
import com.createstats.aerial.AerialSnapshot.Autopilot;
import com.createstats.aerial.AerialSnapshot.Balloon;
import com.createstats.aerial.AerialSnapshot.Body;
import com.createstats.aerial.AerialSnapshot.Docking;
import com.createstats.aerial.AerialSnapshot.Engine;
import com.createstats.aerial.AerialSnapshot.Force;
import com.createstats.aerial.AerialSnapshot.Mass;
import com.createstats.aerial.AerialSnapshot.Propeller;
import com.createstats.aerial.AerialSnapshot.Route;
import com.createstats.aerial.AerialSnapshot.Silhouette;
import com.createstats.aerial.AerialSnapshot.Target;
import com.createstats.aerial.AerialSnapshot.Terrain;
import com.createstats.aerial.AerialSnapshot.Traffic;
import com.createstats.aerial.AerialSnapshot.Waypoint;
import com.createstats.vccmk4.air.core.AerialPilot;
import com.createstats.vccmk4.air.core.FlightIntent;
import com.createstats.vccmk4.air.core.LeaveMode;
import com.createstats.vccmk4.air.core.TerrainGuard;

public final class AerialFixtures {
    public static final V3 ORIGIN = new V3(812.0, 142.0, -2240.0);

    static final V3 WIND = toward(140.0).times(2.3);
    static final double MASS = 1180.0;
    static final double G = 11.0;

    static final double DRAG = 180.0;

    static final double SAIL = 0.12 * MASS * G / (11.4 * AerialFlightSim.pressure(142.0));

    static final double LIFT_PER_M3 = 1.5;

    static final int ACTIVE = 3;
    static final double FAN_MAX_RPM = 256.0;

    private static AerialRecorder recorded;
    private static V3 flownTo;
    private static long flownTick;

    private AerialFixtures() {
    }

    public static synchronized AerialRecorder flown() {
        if (recorded == null) {
            fly();
        }
        return recorded;
    }

    private static void fly() {
        final AerialFlightSim sim = new AerialFlightSim(ORIGIN.minus(new V3(900.0, 0.0, -700.0)), 72.0);
        final AerialRecorder rec = new AerialRecorder();
        final double[][] script = {
                {40, 0, 0, 1}, {10, 9, 0, 1}, {30, 0, 12, 1}, {10, 9, 12, 1}, {35, 0, 0, 1}, {12, -9, -10, 1},
                {40, 0, -10, 1}, {10, 6, 0, 1},

                {30, 0, 0, 0.5}, {12, 9, 0, 0.5}, {25, 0, 0, 0.3}, {12, -9, 0, 0.3}, {25, 0, 0, 1}};

        int rings = 0;
        double order = Double.NaN;
        for (final double[] leg : script) {
            sim.yawCommand = leg[1];
            sim.targetAltitude = ORIGIN.y() + leg[2];
            sim.throttle = leg[3];
            final double o = orderFor(leg[3]);
            if (o != order) {
                order = o;
                rings++;
            }
            for (int i = 0; i < leg[0] * 20; i++) {
                sim.step();
                rec.observe(annotate(sim.snapshot(), sim, "HELM", "Alex", order, rings));
            }
        }

        final List<Waypoint> wps = route().points();
        rec.apply(AerialCommand.CLEAR_TRAIL);
        sim.position = wps.get(0).position();
        sim.heading = AirframeMath.headingOf(wps.get(1).position().minus(wps.get(0).position()));
        sim.wind = WIND;
        sim.velocity = toward(sim.heading).times(12.0).plus(WIND);
        sim.throttle = 1.0;
        sim.route = wps;
        sim.active = 1;
        final V3 from = wps.get(ACTIVE - 1).position();
        final V3 leg = wps.get(ACTIVE).position().minus(from).horizontal().normalized();
        final double stop = ORIGIN.minus(from).horizontal().dot(leg);
        if (order != 1.0) {
            order = 1.0;
            rings++;
        }
        for (int i = 0; i < 20 * 600; i++) {
            sim.pursue();
            sim.step();
            rec.observe(annotate(sim.snapshot(), sim, "GO_TO", "", order, rings));
            if (sim.active == ACTIVE && sim.position.minus(from).horizontal().dot(leg) >= stop) {
                break;
            }
        }
        recorded = rec;
        flownTo = sim.position;
        flownTick = sim.tick;
        flownRings = rings;
    }

    private static int flownRings;

    static double orderFor(double throttle) {
        return throttle >= 0.99 ? 1.0 : (throttle >= 0.49 ? 0.5 : 0.25);
    }

    private static AerialSnapshot annotate(AerialSnapshot s, AerialFlightSim sim, String mode, String pilot, double order,
                                           int rings) {
        return s.withMind(new AerialSnapshot.Helm(mode, "GO_TO", pilot, 0L, "STANDARD", order, order, rings, false,
                sim.targetAltitude, sim.targetAltitude, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, 320.0, 0.6, false, false, 0.0, 0, V3.NAN, AerialSnapshot.Guard.NONE, "", false, List.of(),
                Double.NaN, Double.NaN, Double.NaN), null);
    }

    public enum Scene {
        CRUISE, SINKING, MOORED
    }

    public static AerialSnapshot full(Scene scene) {
        return switch (scene) {
            case CRUISE -> mind(cruise(), false);
            case SINKING -> mind(sinking(), true);
            case MOORED -> moored();
        };
    }

    private static AerialSnapshot mind(AerialSnapshot s, boolean sinking) {
        final AirframeReadout r = AirframeMath.read(s);

        final AirframeReadout rated = AirframeMath.read(cruise());
        final double held = 140.0;
        final double bearing = r.nav == null ? Double.NaN : r.nav.bearing();
        final List<String> notes = sinking
                ? List.of("Lift short: even full burners cannot hold this. Lighten the ship or add burners.")
                : List.of();

        final FlightIntent.Line line = FlightIntent.banner(new FlightIntent.Now(AerialPilot.Mode.GO_TO, LeaveMode.GO_TO,
                false, r.altitude, held, held, r.verticalSpeed, r.heading, Double.NaN, r.groundSpeed,
                r.nav == null ? Double.NaN : r.nav.distance(), bearing, "TABLE TARGET", r.agl),
                sinking ? notes.get(0) : null, TerrainGuard.Result.NONE);
        final AerialSnapshot.Helm helm = new AerialSnapshot.Helm("GO_TO", "GO_TO", "", flownTick - 600L * 20L,
                "STANDARD", 1.0, 1.0, flownRings, false, held, held, bearing, 12.0, s.envelope().topSpeed(),
                rated.ceilingY, rated.ceilingY, Double.NaN, 320.0, sinking ? 1.0 : 0.78, sinking, false, 0.1, 0, V3.NAN,
                AerialSnapshot.Guard.NONE, line.text(), line.alert(), notes, 9800.0, 4200.0, 26000.0);
        return s.withMind(helm, kestrelParts(sinking)).withRecorded(s.trail(), s.history(), s.envelope(),
                flown().voyage());
    }

    public static AerialSnapshot anchored(AerialSnapshot flying) {
        final AerialSnapshot.Body b = flying.body();
        final AerialSnapshot still = flying.withBody(new AerialSnapshot.Body(b.position(), b.orientation(), b.nose(),
                b.velocity().times(0.03), V3.ZERO));
        final AirframeReadout r = AirframeMath.read(still);
        final double held = Math.round(b.position().y());
        final FlightIntent.Line line = FlightIntent.banner(new FlightIntent.Now(AerialPilot.Mode.ANCHOR, LeaveMode.ANCHOR,
                false, r.altitude, held, held, r.verticalSpeed, r.heading, Double.NaN, r.groundSpeed, Double.NaN,
                Double.NaN, "", r.agl), null, TerrainGuard.Result.NONE);
        final V3 anchor = b.position().plus(new V3(2.5, 0.0, -1.5));

        final List<AerialSnapshot.Fix> swing = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            final double a = i * 0.31;
            final double rr = 1.2 + 1.4 * Math.sin(i * 0.17);
            swing.add(new AerialSnapshot.Fix(anchor.plus(new V3(Math.cos(a) * rr, 0.0, Math.sin(a) * rr * 0.7)),
                    flying.gameTime() - (40L - i) * 120L));
        }
        swing.add(new AerialSnapshot.Fix(b.position(), flying.gameTime()));
        return still.withRecorded(new AerialSnapshot.Trail(swing), still.history(), still.envelope()).withMind(helm(flying.helm(), "ANCHOR", "ANCHOR", "", flying.gameTime() - (4L * 60L + 12L) * 20L,
                false, held, anchor, line.text(), List.of()), flying.parts());
    }

    public static AerialSnapshot weighing(AerialSnapshot moored) {
        final String note = "Weighing off: the ship is heavier than its own blocks (a body resting on it?), so the balloons"
                + " take up lift until it rises.";
        return moored.withMind(helm(moored.helm(), "HELM", moored.helm().leave(), "Alex", moored.gameTime() - 200L, true,
                moored.body().position().y() + 4.0, V3.NAN, "", List.of(note)), moored.parts());
    }

    static AerialSnapshot.Helm helm(AerialSnapshot.Helm h, String mode, String leave, String pilot, long since,
                                    boolean weighing, double held, V3 anchor, String intent, List<String> notes) {
        return new AerialSnapshot.Helm(mode, leave, pilot, since, h.feel(), 0.0, 0.0, h.rings(), false, held, held,
                h.heldHeading(), h.heldSpeed(), h.fullAhead(), h.ceiling(), h.balloonCeiling(), h.cap(), h.worldTop(),
                h.liftUse(), h.liftShort(), weighing, h.trim(), h.staff(), anchor, AerialSnapshot.Guard.NONE, intent, false,
                notes, h.thrustAhead(), h.thrustAstern(), h.thrustTurn());
    }

    static AerialSnapshot.Parts kestrelParts(boolean sinking) {
        final List<AerialSnapshot.Part> p = new ArrayList<>();
        final double[][] podAt = {{-9.0, -1.0, -5.0}, {9.0, -1.0, -5.0}, {-9.0, -1.0, 5.0}, {9.0, -1.0, 5.0}};
        final String[] podName = {"Fore port", "Fore stbd", "Aft port", "Aft stbd"};
        for (int i = 0; i < 4; i++) {
            p.add(new AerialSnapshot.Part(AerialSnapshot.Part.GOVERNOR, podName[i], "push",
                    AerialSnapshot.Part.OK, new V3(podAt[i][0], podAt[i][1], podAt[i][2]), new V3(0.0, 0.0, -1.0),
                    i == 2 ? 0.74 : 1.0, i == 2 ? 1010.0 : 1370.0));
        }
        for (final double x : new double[]{-4.0, 4.0}) {
            p.add(new AerialSnapshot.Part(AerialSnapshot.Part.GOVERNOR, x < 0 ? "Lift fan port" : "Lift fan stbd", "lift",
                    AerialSnapshot.Part.OK, new V3(x, 3.0, 0.0), V3.UP, sinking ? 1.0 : 0.62, sinking ? 2900.0 : 1800.0));
        }
        p.add(new AerialSnapshot.Part(AerialSnapshot.Part.FIN, "F1", "rudder", AerialSnapshot.Part.OK,
                new V3(0.0, 1.0, 15.0), V3.UP, sinking ? -8.0 : 4.5, 30.0));
        p.add(new AerialSnapshot.Part(AerialSnapshot.Part.FIN, "F2", "elevator", AerialSnapshot.Part.OK,
                new V3(-2.0, 0.0, 14.0), new V3(1.0, 0.0, 0.0), 2.0, 20.0));
        p.add(new AerialSnapshot.Part(AerialSnapshot.Part.LAMP, "red", "position", AerialSnapshot.Part.OK,
                new V3(-6.5, 6.0, 0.0), new V3(-1.0, 0.0, 0.0), 15.0, AerialSnapshot.Part.RED));
        p.add(new AerialSnapshot.Part(AerialSnapshot.Part.LAMP, "amber", "beacon", AerialSnapshot.Part.OK,
                new V3(0.0, 12.5, 0.0), V3.UP, 0.0, AerialSnapshot.Part.AMBER));
        p.add(new AerialSnapshot.Part(AerialSnapshot.Part.LAMP, "white", "landing", AerialSnapshot.Part.OK,
                new V3(0.0, -3.0, -10.0), new V3(0.0, -1.0, 0.0), 0.0, AerialSnapshot.Part.WHITE));
        p.add(new AerialSnapshot.Part(AerialSnapshot.Part.TOOL, "rope pulley", "aux a", AerialSnapshot.Part.OK,
                new V3(0.0, -3.0, 4.0), V3.UP, 0.0, 0.0));
        return new AerialSnapshot.Parts(p, new AerialSnapshot.Learned(0.61, 38.0, -0.02, 150.0, 1.4e4, 6, 6), false);
    }

    static AerialSnapshot moored() {
        final AerialSnapshot c = cruise();
        final AerialRecorder rec = AerialRecorder.load(flown().save());
        AerialSnapshot last = null;
        for (int i = 0; i < 24; i++) {
            last = mooredAt(c, flownTick + 400L + i * 4L);
            rec.observe(last);
        }
        return rec.recorded(last);
    }

    private static AerialSnapshot mooredAt(AerialSnapshot c, long tick) {
        final V3 at = c.body().position();
        final double ground = c.terrain().heightAt(at.x(), at.z());
        final double keel = -c.silhouette().originU();
        final V3 pos = new V3(at.x(), ground + keel + 0.1, at.z());
        final double p = AerialFlightSim.pressure(pos.y());
        final Quat q = AerialFlightSim.orientation(72.0, 0.0, 0.0);
        final List<AerialSnapshot.Balloon> balloons = List.of(
                new AerialSnapshot.Balloon("Balloon 1", "hot_air", new V3(0.0, 4.0, 4.0), 160.0 * LIFT_PER_M3, 160.0,
                        0.0, 500.0, -0.6, 0.0, p, 1, 0, true),
                new AerialSnapshot.Balloon("Balloon 2", "hot_air", new V3(0.0, 4.0, -4.0), 150.0 * LIFT_PER_M3, 150.0,
                        0.0, 500.0, -0.6, 0.0, p, 1, 0, true));
        final List<Propeller> props = new ArrayList<>();
        for (final Propeller pr : c.propellers()) {
            props.add(new Propeller(pr.name(), pr.kind(), pr.position(), pr.direction(), 0.0, 0.0, 1.0, p, 0.0, 0.0, false));
        }
        final List<Force> forces = List.of(
                new Force("balloon_lift", new V3(0.0, 310.0 * LIFT_PER_M3 * p * G, 0.0), pos.plus(new V3(0.0, 4.0, 0.0))),
                new Force("gravity", new V3(0.0, -MASS * G, 0.0), pos));
        final AerialSnapshot.Helm helm = new AerialSnapshot.Helm("MOORED", "GO_TO", "", tick - 2400L, "STANDARD", 0.0, 0.0,
                flownRings + 1, true, Double.NaN, Double.NaN, Double.NaN, Double.NaN, 13.3, Double.NaN, 118.0, Double.NaN,
                320.0, 0.0, false, false, 0.0, 0, V3.NAN, AerialSnapshot.Guard.NONE, "MOORED: SIT TO FLY", false, List.of(),
                9800.0, 4200.0, 26000.0);
        return new AerialSnapshot(tick, "Kestrel", c.shipId(), c.commands(),
                new Body(pos, q, new V3(0.0, 0.0, -1.0), V3.ZERO, V3.ZERO),
                new Air(true, V3.ZERO, p, AerialFlightSim.pressure(pos.y() + Air.PROBE), 63.0, new V3(0.0, -G, 0.0)),
                new Mass(MASS, c.mass().inertia(), 1204, 0.0), forces, props, balloons,
                new Engine(1, 2460.0, 0.0, false), c.target(), new Autopilot("MOORED", false, Double.NaN, Double.NaN,
                Double.NaN, V3.NAN, Double.NaN, Double.NaN, Double.NaN, Double.NaN), null, c.traffic(), c.terrain(),
                c.route(), null, null, null, c.silhouette(), helm, kestrelParts(false), null);
    }

    public static AerialSnapshot cruise() {
        return scenario(false);
    }

    public static AerialSnapshot sinking() {
        return scenario(true);
    }

    private static AerialSnapshot scenario(boolean sinking) {
        flown();
        final double weight = MASS * G;
        final double alt = sinking ? 104.0 : 142.0;
        final V3 pos = new V3(flownTo.x(), alt, flownTo.z());
        final double heading = sinking ? 64.0 : 72.0;
        final double pitch = sinking ? -4.0 : 3.5;
        final double bank = sinking ? 18.0 : -12.0;
        final Quat q = AerialFlightSim.orientation(heading, pitch, bank);
        final double p = AerialFlightSim.pressure(alt);
        final double pAbove = AerialFlightSim.pressure(alt + Air.PROBE);

        final double ias = sinking ? 6.2 : 11.4;
        final V3 fwd = toward(heading);
        final V3 wind = sinking ? WIND.times(3.4 / 2.3) : WIND;
        final double vs = sinking ? -3.8 : 1.2;
        final V3 air = fwd.times(ias).plus(new V3(0.0, vs, 0.0));
        final V3 velocity = air.plus(wind);

        final double[][] podAt = {{-9.0, -1.0, -5.0}, {9.0, -1.0, -5.0}, {-9.0, -1.0, 5.0}, {9.0, -1.0, 5.0}};
        final String[] podName = {"Fore port", "Fore stbd", "Aft port", "Aft stbd"};
        final double[] podRpm = {128.0, 128.0, 96.0, 128.0};
        final List<Propeller> pushers = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            pushers.add(bearing(podName[i], new V3(podAt[i][0], podAt[i][1], podAt[i][2]), new V3(0.0, 0.0, -1.0), 16,
                    podRpm[i], i == 2 ? 0.94 : 0.41, q, air, p));
        }
        double pushUp = 0.0;
        for (final Propeller pr : pushers) {
            pushUp += thrust(pr, q).y();
        }
        final double sails = SAIL * air.horizontalLength() * p;
        final double target = sinking ? 0.92 : 1.07;

        final double aft = 395.0;
        final double fanRpm;
        final double forward;
        if (sinking) {
            fanRpm = FAN_MAX_RPM;
            final double fans = fanLift(fanRpm, q, air, p);
            final double balloonForce = target * weight - pushUp - sails - fans;
            forward = balloonForce / (p * G) / LIFT_PER_M3 - aft;
        } else {
            forward = 395.0;
            final double need = target * weight - pushUp - sails - (aft + forward) * LIFT_PER_M3 * p * G;
            double lo = 0.0;
            double hi = FAN_MAX_RPM;
            for (int i = 0; i < 80; i++) {
                final double mid = (lo + hi) * 0.5;
                if (fanLift(mid, q, air, p) < need) {
                    lo = mid;
                } else {
                    hi = mid;
                }
            }
            fanRpm = (lo + hi) * 0.5;
        }
        final List<Propeller> props = new ArrayList<>(pushers);
        props.add(fan("Lift fan port", -4.0, fanRpm, q, air, p));
        props.add(fan("Lift fan stbd", 4.0, fanRpm, q, air, p));
        final List<Balloon> balloons = List.of(
                new Balloon("Balloon 1", "hot_air", new V3(0.0, 4.0, 4.0), aft * LIFT_PER_M3, aft, 400.0, 500.0, 0.4,
                        14.4, p),

                new Balloon("Balloon 2", "hot_air", new V3(0.0, 4.0, -4.0), forward * LIFT_PER_M3, forward, 400.0,
                        500.0, sinking ? 0.0 : 0.4, sinking ? 6.0 : 14.4, p));

        final V3 com = pos;
        final List<Force> forces = new ArrayList<>();
        V3 balloonCentre = V3.ZERO;
        double balloonLift = 0.0;
        for (final Balloon b : balloons) {
            balloonCentre = balloonCentre.plus(b.position().times(b.lift()));
            balloonLift += b.lift();
        }
        forces.add(new Force("balloon_lift", new V3(0.0, balloonLift * p * G, 0.0),
                com.plus(q.rotate(balloonCentre.times(1.0 / balloonLift)))));
        V3 push = V3.ZERO;
        V3 pushCentre = V3.ZERO;
        double pushSum = 0.0;
        for (final Propeller pr : props) {
            final V3 t = thrust(pr, q);
            push = push.plus(t);
            pushCentre = pushCentre.plus(pr.position().times(t.length()));
            pushSum += t.length();
        }
        forces.add(new Force("propulsion", push, com.plus(q.rotate(pushCentre.times(1.0 / pushSum)))));
        forces.add(new Force("lift", new V3(0.0, sails, 0.0), com.plus(q.rotate(new V3(0.0, 0.0, -1.2)))));
        forces.add(new Force("drag", air.times(-DRAG), V3.NAN));
        forces.add(new Force("gravity", new V3(0.0, -weight, 0.0), com));

        final AerialRecorder rec = flown();

        final Route route = route();
        final V3 look = AerialFlightSim.pursuitPoint(route.points(), ACTIVE, pos);
        final V3 lookahead = new V3(look.x(), alt, look.z());
        final double steer = AirframeMath.headingOf(lookahead.minus(pos));
        final AerialSnapshot measured = new AerialSnapshot(flownTick + 1L, "Kestrel", new UUID(0x3f2aL, 0x77L),
                AerialCommand.all(),
                new Body(pos, q, new V3(0.0, 0.0, -1.0), velocity, new V3(0.0, -Math.toRadians(1.8), 0.0)),
                new Air(true, air, p, pAbove, 63.0, new V3(0.0, -G, 0.0)),
                new Mass(MASS, new V3(41000.0, 66000.0, 38000.0), 1204), forces, props, balloons,
                new Engine(1, sinking ? 95.0 : 2460.0, 0.8, false),
                new Target("Harbor Tower", pos.plus(new V3(Math.sin(Math.toRadians(81)) * 612.0, -20.0,
                        -Math.cos(Math.toRadians(81)) * 612.0)), "nav_table"),
                new Autopilot(sinking ? "ALT HOLD" : "ROUTE", true, 140.0, 12.0, steer, lookahead,
                        podRpm[0] / 256.0, sinking ? 0.97 : 0.88, 0.35, fanRpm / FAN_MAX_RPM),
                null, traffic(pos, sinking), terrain(pos), route, null, null, null, silhouette());
        return measured.withRecorded(rec.trail(), rec.history(), rec.envelope());
    }

    static V3 toward(double headingDeg) {
        final double h = Math.toRadians(headingDeg);
        return new V3(Math.sin(h), 0.0, -Math.cos(h));
    }

    static Propeller bearing(String name, V3 at, V3 dir, int sails, double rpm, double stress, Quat q, V3 air,
                             double p) {
        final double airflow = Math.sqrt(sails) * rpm * 0.05;
        return new Propeller(name, "bearing", at, dir, Math.pow(sails, 1.5) * rpm * 0.2, airflow,
                AirframeMath.airflowScaling(airflow, air.dot(q.rotate(dir))), p, rpm, stress, true);
    }

    static Propeller fan(String name, double x, double rpm, Quat q, V3 air, double p) {
        return bearing(name, new V3(x, 3.0, 0.0), V3.UP, 12, rpm, 0.55, q, air, p);
    }

    static double fanLift(double rpm, Quat q, V3 air, double p) {
        return thrust(fan("", -4.0, rpm, q, air, p), q).y() + thrust(fan("", 4.0, rpm, q, air, p), q).y();
    }

    static V3 thrust(Propeller pr, Quat q) {
        return q.rotate(pr.direction()).times(pr.staticThrust() * pr.airflowScaling() * pr.pressure());
    }

    static Terrain terrain(V3 pos) {
        final int size = Terrain.MAX_SIZE;
        final double cell = 8.0;
        final double ox = Math.floor((pos.x() - size * cell / 2.0) / cell) * cell;
        final double oz = Math.floor((pos.z() - size * cell / 2.0) / cell) * cell;
        final short[] h = new short[size * size];
        for (int iz = 0; iz < size; iz++) {
            for (int ix = 0; ix < size; ix++) {
                final double east = ox + (ix + 0.5) * cell - pos.x();
                final double south = oz + (iz + 0.5) * cell - pos.z();
                double y = 84.0 + 10.0 * Math.sin(east * 0.021 - 0.5) + 8.0 * Math.cos(south * 0.017 + 0.3);
                if (east > 110.0 && east < 175.0) {
                    y += 30.0 * Math.sin((east - 110.0) / 65.0 * Math.PI);
                }
                h[iz * size + ix] = (short) Math.round(y);
            }
        }
        final Terrain t = new Terrain(ox, oz, cell, size, h, Double.NaN, Double.NaN);
        return new Terrain(ox, oz, cell, size, h, t.heightAt(pos.x(), pos.z()), Double.NaN);
    }

    static Route route() {
        final List<Waypoint> w = new ArrayList<>();
        w.add(new Waypoint(ORIGIN.plus(new V3(-380.0, 0.0, 240.0)), 12.0));
        w.add(new Waypoint(ORIGIN.plus(new V3(-260.0, 0.0, 40.0)), 12.0));
        w.add(new Waypoint(ORIGIN.plus(new V3(-110.0, 0.0, 70.0)), 12.0));
        w.add(new Waypoint(ORIGIN.plus(new V3(160.0, 0.0, -30.0)), 12.0));
        w.add(new Waypoint(ORIGIN.plus(new V3(200.0, 0.0, -150.0)), 12.0));
        w.add(new Waypoint(ORIGIN.plus(new V3(330.0, -10.0, -230.0)), 12.0));
        w.add(new Waypoint(ORIGIN.plus(new V3(280.0, -20.0, -275.0)), 8.0));
        return new Route("Harbor run", w, ACTIVE);
    }

    static List<Traffic> traffic(V3 pos, boolean sinking) {
        final List<Traffic> t = new ArrayList<>();
        t.add(new Traffic("Skiff", new UUID(5L, 1L), pos.plus(new V3(-40.0, 2.0, -74.0)),
                new V3(6.0, 0.0, 9.0), 6.0));
        t.add(new Traffic("Heron", new UUID(5L, 2L), pos.plus(new V3(-160.0, 12.0, -110.0)),
                new V3(-8.0, 0.0, -2.0), 11.0));
        t.add(new Traffic("Barge 2", new UUID(5L, 3L), pos.plus(new V3(210.0, -30.0, 120.0)),
                new V3(1.5, 0.0, 2.0), 18.0));
        if (!sinking) {
            t.add(new Traffic("Dock ferry", new UUID(5L, 4L), pos.plus(new V3(-40.0, -71.0, 70.0)),
                    new V3(0.0, 0.0, -0.8), 7.0));
        }
        return t;
    }

    static Silhouette silhouette() {
        final List<V3> blocks = new ArrayList<>();
        final List<V3> envelope = new ArrayList<>();
        for (int z = -16; z <= 16; z++) {
            final double rz = 1.0 - Math.pow(z / 17.0, 2.0);
            final int r = (int) Math.round(6.0 * Math.sqrt(Math.max(0.0, rz)));
            for (int y = -r; y <= r; y++) {
                for (int x = -r; x <= r; x++) {
                    if (x * x + y * y <= r * r) {
                        blocks.add(new V3(x + 0.5, 6.5 + y, z + 0.5));
                        envelope.add(new V3(x + 0.5, 6.5 + y, z + 0.5));
                    }
                }
            }
        }
        for (int z = -12; z <= 12; z++) {
            for (int x = -3; x <= 3; x++) {
                for (int y = -3; y <= 0; y++) {
                    if (y > -3 || Math.abs(z) < 10) {
                        blocks.add(new V3(x + 0.5, y + 0.5, z + 0.5));
                    }
                }
            }
        }
        for (final int z : new int[]{-5, 5}) {
            for (final int x : new int[]{-9, 9}) {
                blocks.add(new V3(x + 0.5, -0.5, z + 0.5));
                blocks.add(new V3(x + 0.5 + (x < 0 ? 1 : -1), -0.5, z + 0.5));
            }
        }
        final Silhouette sil = AerialSilhouette.of(blocks, new V3(0.0, 0.0, -1.0));

        return sil.withEnvelope(AerialRealistic.bits(sil, envelope, true), AerialRealistic.bits(sil, envelope, false));
    }

    public static AerialSnapshot docking() {
        final AerialSnapshot s = cruise();
        return new AerialSnapshot(s.gameTime(), "Dock ferry", s.shipId(), s.commands(), s.body(), s.air(), s.mass(),
                s.forces(), s.propellers(), s.balloons(), s.engine(), s.target(), s.autopilot(),
                new Docking("Harbor Tower", false, new V3(0.7, 0.4, -4.6), new V3(0.0, 0.0, 0.8)), s.traffic(),
                s.terrain(), s.route(), s.trail(), s.history(), s.envelope(), s.silhouette());
    }
}
