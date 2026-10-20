package com.createstats.vccmk4;

import com.createstats.CreateStats;
import com.createstats.aerial.Quat;
import com.createstats.aerial.V3;
import com.createstats.driverhelmet.DriveLog;
import com.createstats.driverhelmet.OffroadWheels;
import com.createstats.driverhelmet.TelemetryMath;
import com.createstats.flight.blockentity.RpmGovernorBE;
import com.createstats.flight.bridge.ReflectionAccessors;
import com.createstats.flight.bridge.SableBridge;
import com.createstats.ground.GroundCommand;
import com.createstats.ground.GroundCost;
import com.createstats.ground.GroundRecorder;
import com.createstats.ground.GroundSilhouette;
import com.createstats.ground.GroundSnapshot;
import com.createstats.ground.VehicleKind;
import com.createstats.vccmk4.core.AxleLayout;
import com.createstats.vccmk4.core.BodyRole;
import com.createstats.vccmk4.core.Control;
import com.createstats.vccmk4.core.DriveModel;
import com.createstats.vccmk4.core.JetMixer;
import com.createstats.vccmk4.core.JetSupply;
import com.createstats.vccmk4.core.LinkKind;
import com.createstats.vccmk4.core.LinkLogic;
import com.createstats.vccmk4.core.TrailerMode;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// read only
final class Vcc4Telemetry {
    static final int IDLE_INTERVAL = 20;

    static final long WATCHED_TICKS = 100;

    static final double ACCEL_SMOOTHING = 0.3;

    static final int PATCH_SIZE = 32;
    static final double PATCH_CELL = 4.0;
    static final int PATCH_INTERVAL = 100;

    static final long SHAPE_MAX_CELLS = 96L * 96L * 64L;

    static final int SHAPE_INTERVAL = 40;

    private final Vcc4BE host;
    private GroundRecorder recorder = new GroundRecorder();
    private final DriveLog log = new DriveLog();
    private final GroundCost cost = new GroundCost();

    @Nullable
    private GroundSnapshot snapshot;
    private long lastBuild = Long.MIN_VALUE;
    private long lastAsked = Long.MIN_VALUE / 2;
    private boolean failing;

    private final Vector3d lastVelocity = new Vector3d();
    private long lastSample = Long.MIN_VALUE;
    private final double[] accel = new double[3];

    private GroundSnapshot.Terrain patch = GroundSnapshot.Terrain.NONE;
    private long lastPatch = Long.MIN_VALUE;

    private List<GroundSnapshot.Shape> shapes = List.of();
    private final Map<Integer, String> shapeKeys = new HashMap<>();
    private long lastShapes = Long.MIN_VALUE;
    private int blocks = -1;

    Vcc4Telemetry(Vcc4BE host) {
        this.host = host;
    }

    void tick(ServerLevel server, long now) {
        long t0 = System.nanoTime();
        try {
            sample(server, now);
        } catch (RuntimeException e) {
            if (!failing) {
                CreateStats.LOGGER.warn("[VCC Mk IV] Stats Display sample failed at {}: {}", host.getBlockPos(),
                        e.toString(), e);
                failing = true;
            }
        }
        long t1 = System.nanoTime();
        cost.add(GroundCost.Phase.SAMPLE, t1 - t0);
        if (due(now)) {
            lastBuild = now;
            try {
                GroundSnapshot measured = build(server, now);
                recorder.observe(measured);
                snapshot = recorder.stamp(measured);
                failing = false;
            } catch (RuntimeException e) {
                if (!failing) {
                    CreateStats.LOGGER.warn("[VCC Mk IV] Stats Display snapshot failed at {}: {}", host.getBlockPos(),
                            e.toString(), e);
                    failing = true;
                }
            }
            cost.add(GroundCost.Phase.BUILD, System.nanoTime() - t1);
        }
        cost.endTick();
    }

    private boolean due(long now) {
        if (snapshot == null || lastBuild == Long.MIN_VALUE || now < lastBuild) return true;
        boolean busy = host.driving() || host.towed() || now - lastAsked <= WATCHED_TICKS
                || Math.abs(snapshot.body().forwardSpeed()) > 0.05 || snapshot.body().velocity().length() > 0.05;
        return now - lastBuild >= (busy ? Vcc4BE.SNAPSHOT_INTERVAL : IDLE_INTERVAL);
    }

    @Nullable
    GroundSnapshot snapshot() {
        if (host.getLevel() != null) lastAsked = host.getLevel().getGameTime();
        return snapshot;
    }

    boolean command(GroundCommand command, long now) {
        boolean done = recorder.apply(command, now);
        if (done) lastBuild = Long.MIN_VALUE;
        return done;
    }

    byte[] saveRecorder() {
        return recorder.save();
    }

    void loadRecorder(byte[] data) {
        recorder = GroundRecorder.load(data);
        lastBuild = Long.MIN_VALUE;
    }

    String costLine() {
        return cost.ticks() >= GroundCost.MEAN_TICKS ? cost.summary() : "";
    }

    private void sample(ServerLevel server, long now) {
        Vcc4Scanner.Scan scan = host.scan();
        if (!scan.onCraft()) {
            lastSample = Long.MIN_VALUE;
            return;
        }
        Vector3d v = SableBridge.linearVelocityWorld(host);
        if (v == null || !finite(v)) v = new Vector3d();
        long dt = lastSample == Long.MIN_VALUE ? 0L : now - lastSample;
        double g = gravityOf();
        double rawG = 0.0;
        if (dt >= 1 && dt <= 5) {
            double seconds = dt / 20.0;
            double[] raw = {(v.x - lastVelocity.x) / seconds, (v.y - lastVelocity.y) / seconds,
                    (v.z - lastVelocity.z) / seconds};
            rawG = Math.sqrt(raw[0] * raw[0] + raw[1] * raw[1] + raw[2] * raw[2]) / g;
            for (int i = 0; i < 3; i++) accel[i] += (raw[i] - accel[i]) * ACCEL_SMOOTHING;
        } else {
            Arrays.fill(accel, 0.0);
        }
        lastVelocity.set(v);
        lastSample = now;
        Quaterniond q = SableBridge.bodyOrientation(SableBridge.subLevelAccessOf(host));
        Vector3d nose = host.nose();
        Vector3d forward = q == null ? new Vector3d(nose) : q.transform(new Vector3d(nose));
        Vector3d up = q == null ? new Vector3d(0, 1, 0) : q.transform(new Vector3d(0, 1, 0));
        double[] lateralLongitudinal = TelemetryMath.gForce(accel, new double[]{forward.x, forward.y, forward.z},
                new double[]{up.x, up.y, up.z}, g);
        log.tick(now, Math.hypot(lateralLongitudinal[0], lateralLongitudinal[1]), rawG, allAirborne(server, scan));
    }

    private static boolean allAirborne(ServerLevel server, Vcc4Scanner.Scan scan) {
        int tyres = 0;
        for (Vcc4Scanner.Wheel w : scan.wheels()) {
            BlockEntity be = SableBridge.getBlockEntityOnContraption(server, w.pos());
            if (Double.isNaN(OffroadWheels.radius(be))) continue;
            tyres++;
            if (!TelemetryMath.airborne(OffroadWheels.extension(be))) return false;
        }
        return tyres > 0;
    }

    private double gravityOf() {
        Vector3d gv = SableBridge.gravityWorld(host);
        double g = gv == null ? 0.0 : gv.length();
        return g > 1.0e-6 && Double.isFinite(g) ? g : GroundSnapshot.Body.STANDARD_GRAVITY.length();
    }

    private record Frames(Vector3d[] centre, Vector3d nose, Vector3d right) {
        V3 local(int b, Vector3d p) {
            Vector3d c = b >= 0 && b < centre.length ? centre[b] : centre[0];
            Vector3d d = new Vector3d(p).sub(c);
            return new V3(d.dot(right), d.y, d.dot(nose));
        }

        V3 vehicle(Vector3d onMain) {
            return local(0, onMain);
        }

        V3 axis(Vector3d d) {
            return new V3(d.dot(right), d.y, d.dot(nose));
        }
    }

    private GroundSnapshot build(ServerLevel server, long now) {
        Vcc4Scanner.Scan scan = host.scan();
        Object craft = SableBridge.subLevelAccessOf(host);
        Vcc4Train train = host.train();
        Vector3d nose = host.nose();
        Vector3d right = host.right();
        Frames frames = frames(scan, nose, right);

        List<GroundSnapshot.Governor> governors = new ArrayList<>();
        Map<BlockPos, Integer> governorIndex = new HashMap<>();
        governors(server, scan, frames, governors, governorIndex);
        List<GroundSnapshot.Wheel> wheels = wheels(server, scan, frames, governorIndex, now);
        List<GroundSnapshot.Jet> jets = jets(server, scan, frames);
        Map<Integer, Vcc4Train.Plan> plans = new HashMap<>();
        List<GroundSnapshot.Unit> units = units(server, scan, frames, plans);
        List<GroundSnapshot.Tie> ties = ties(scan, craft, frames, plans);
        shapes(server, scan, frames, now);

        int body0Wheels = 0;
        for (Vcc4Scanner.Wheel w : scan.wheels()) if (w.body() == 0) body0Wheels++;
        int trailers = 0;
        for (Vcc4Scanner.Body b : scan.bodies()) if (b.index() > 0 && train.takesMode(b.index())) trailers++;
        boolean jetDrive = host.jetDriveNow();
        VehicleKind kind = VehicleKind.classify(host.axles().size(), body0Wheels, scan.props().size(), host.setup().preset,
                host.articulated(), jetDrive, trailers, scan.machines().size());
        GroundSnapshot.Identity identity = new GroundSnapshot.Identity(
                craft == null ? "VCC Mk IV Ground" : SableBridge.subLevelName(craft), host.craftId(), host.profile(), kind,
                GroundCommand.all(), scan.onCraft(), Vcc4Reflect.brakeMixinApplied(), train.towedBy());

        Quaterniond q = SableBridge.bodyOrientation(craft);
        Vector3d comWorld = craft == null ? null : SableBridge.localToWorld(craft, host.com());
        if (comWorld == null || !finite(comWorld)) comWorld = Vcc4Scanner.centre(host.getBlockPos());
        Vector3d v = SableBridge.linearVelocityWorld(host);
        if (v == null || !finite(v)) v = new Vector3d();
        Vector3d noseWorld = q == null ? new Vector3d(nose) : q.transform(new Vector3d(nose));
        Vector3d gravity = SableBridge.gravityWorld(host);
        GroundSnapshot.Body body = new GroundSnapshot.Body(v3(comWorld),
                q == null ? Quat.IDENTITY : new Quat(q.x, q.y, q.z, q.w), v3(nose), v3(v), train.yawRate(),
                v.dot(noseWorld), new V3(accel[0], accel[1], accel[2]), gravity == null ? null : v3(gravity));
        GroundSnapshot.Log logRecord = new GroundSnapshot.Log(log.peak(), log.lastImpact(), log.impactAge(now),
                log.tripImpact(), log.airNow(now), log.lastAir());

        DriveModel.Output out = host.lastOutput();
        boolean driving = host.driving();
        boolean parked = host.parked();
        float[] controls = host.controlValues();
        String alert = train.alert();
        GroundSnapshot.Drive drive = new GroundSnapshot.Drive(host.driverName(), host.helmTick(), host.helmStill(),
                driving, parked, host.towed(), out.throttle(), out.steer(), out.braking() || parked, out.boosting(),
                out.holding(), driving ? out.gear() : (parked ? 'P' : '-'), out.lockDeg(),
                controls.length > Control.LIFT.ordinal() ? controls[Control.LIFT.ordinal()] : 0.0,
                alert == null ? "" : alert, host.runningMachines(), controls);
        GroundSnapshot.Setup setup = new GroundSnapshot.Setup(host.setup().preset, host.setup().presetAuto,
                host.articulated(), jetDrive, host.setup().options, host.axles().size(), host.setup().greeting);

        double trainMass = 0.0;
        for (Vcc4Scanner.Body b : scan.bodies()) {
            double m = train.massOf(b.index());
            trainMass = Double.isFinite(m) && Double.isFinite(trainMass) ? trainMass + m : Double.NaN;
        }
        GroundSnapshot.Mass mass = new GroundSnapshot.Mass(train.massOf(0), scan.bodies().isEmpty() ? Double.NaN : trainMass,
                train.bodyYawInertia(0), host.comMeasured(), frames.vehicle(Vcc4Scanner.centre(host.getBlockPos())), blocks);

        LinkLogic.Signals signals = host.signalsNow(now);
        List<GroundSnapshot.Lamp> lamps = new ArrayList<>();
        for (Vcc4Scanner.Link l : scan.links()) {
            long key = l.pos().asLong();
            V3 at = frames.local(l.body(), Vcc4Scanner.centre(l.pos()));
            lamps.add(new GroundSnapshot.Lamp(l.body(), (float) at.x(), (float) at.y(), (float) at.z(),
                    l.isLamp() ? l.lamp().ordinal() + 1 : 0, host.setup().linkSourceFor(key), host.links().strength(key),
                    l.isLamp() && !host.setup().linkSources.containsKey(key)));
        }
        GroundSnapshot.Lights lights = new GroundSnapshot.Lights(signals.driving(), signals.braking(), signals.reversing(),
                signals.boosting(), (float) signals.steer(), signals.beam(), lamps);

        List<GroundSnapshot.Machine> machines = new ArrayList<>();
        for (int i = 0; i < scan.machines().size(); i++) {
            Vcc4Scanner.Machine m = scan.machines().get(i);
            long key = m.governor().asLong();
            V3 at = frames.local(m.body(), Vcc4Scanner.centre(m.governor()));
            RpmGovernorBE g = governorAt(server, m.governor());
            machines.add(new GroundSnapshot.Machine("M" + (i + 1), m.body(), (float) at.x(), (float) at.z(),
                    m.drives().isEmpty() ? "" : m.drives().get(0), m.runner(), host.inputFor(key).ordinal(),
                    (float) host.machineOutputOf(key), g == null ? 0 : g.getAppliedRpm(), host.governorForeign(m.governor())));
        }

        return new GroundSnapshot(now, identity, body, logRecord, drive, setup, mass, wheels, governors, jets, units, ties,
                lights, machines, host.displayProblems(), terrain(server, comWorld, now), null, null, null, null, null,
                shapes);
    }

    private Frames frames(Vcc4Scanner.Scan scan, Vector3d nose, Vector3d right) {
        int n = Math.max(1, scan.bodies().size());
        Vector3d[] centre = new Vector3d[n];
        centre[0] = new Vector3d(host.com());
        for (int b = 1; b < n; b++) {
            Vcc4Scanner.Body body = scan.bodies().get(b);
            Vector3d c = SableBridge.centerOfMassLocal(body.handle());
            if (c == null || !finite(c)) {
                Vector3d sum = new Vector3d();
                int count = 0;
                for (Vcc4Scanner.Wheel w : scan.wheels()) {
                    if (w.body() != b) continue;
                    sum.add(w.contact());
                    count++;
                }
                c = count > 0 ? sum.div(count) : Vcc4Scanner.centre(body.anchor());
            }
            centre[b] = c;
        }
        return new Frames(centre, nose, right);
    }

    private void governors(ServerLevel server, Vcc4Scanner.Scan scan, Frames frames, List<GroundSnapshot.Governor> out,
                           Map<BlockPos, Integer> index) {
        int n = 0;
        for (BlockPos pos : scan.usedGovernors()) {
            n++;
            RpmGovernorBE g = governorAt(server, pos);
            int unit = 0;
            int wheels = 0;
            int jets = 0;
            double ratioSum = 0.0;
            int ratioN = 0;
            for (Vcc4Scanner.Wheel w : scan.wheels()) {
                if (!pos.equals(scan.governorFor(w.pos()))) continue;
                if (wheels++ == 0) unit = w.body();
                Double r = host.learnedRatio(w.pos().asLong());
                if (r != null && Double.isFinite(r)) {
                    ratioSum += r;
                    ratioN++;
                }
            }
            for (Vcc4Scanner.Prop p : scan.props()) {
                if (!pos.equals(scan.governorFor(p.pos()))) continue;
                if (wheels == 0 && jets == 0) unit = p.body();
                jets++;
            }
            V3 at = frames.local(unit, Vcc4Scanner.centre(pos));
            index.put(pos, out.size());
            out.add(new GroundSnapshot.Governor("G" + n, unit, (float) at.x(), (float) at.z(),
                    g == null ? 0 : g.getAppliedRpm(), g == null ? 0 : g.getCommandedRpm(),
                    ratioN == 0 ? Float.NaN : (float) (ratioSum / ratioN), wheels, jets, false, supply(g, pos),
                    host.governorStarved(pos)));
        }
        for (int i = 0; i < scan.machines().size(); i++) {
            Vcc4Scanner.Machine m = scan.machines().get(i);
            RpmGovernorBE g = governorAt(server, m.governor());
            V3 at = frames.local(m.body(), Vcc4Scanner.centre(m.governor()));
            out.add(new GroundSnapshot.Governor("M" + (i + 1), m.body(), (float) at.x(), (float) at.z(),
                    g == null ? 0 : g.getAppliedRpm(), g == null ? 0 : g.getCommandedRpm(), Float.NaN, 0, 0, true,
                    supply(g, m.governor()), host.governorStarved(m.governor())));
        }
    }

    private GroundSnapshot.Supply supply(@Nullable RpmGovernorBE g, BlockPos pos) {
        if (host.governorForeign(pos)) return GroundSnapshot.Supply.FOREIGN;
        if (host.governorExcluded(pos)) return GroundSnapshot.Supply.EXCLUDED;
        if (g == null) return GroundSnapshot.Supply.NO_ROTATION;
        if (g.isOverStressed()) return GroundSnapshot.Supply.OVERSTRESSED;
        if (!g.hasSource() || Math.abs(g.getSpeed()) < 1.0e-4f) return GroundSnapshot.Supply.NO_ROTATION;
        return GroundSnapshot.Supply.OK;
    }

    private List<GroundSnapshot.Wheel> wheels(ServerLevel server, Vcc4Scanner.Scan scan, Frames frames,
                                              Map<BlockPos, Integer> governorIndex, long now) {
        Vcc4Train train = host.train();
        List<Vcc4Scanner.Wheel> all = scan.wheels();
        int[] axle = new int[all.size()];
        Arrays.fill(axle, -1);

        Map<Integer, List<Integer>> byBody = new HashMap<>();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).body() == 0) axle[i] = host.axleOfWheel(i);
            else byBody.computeIfAbsent(all.get(i).body(), k -> new ArrayList<>()).add(i);
        }
        for (Map.Entry<Integer, List<Integer>> e : byBody.entrySet()) {
            List<AxleLayout.WheelSpec> specs = new ArrayList<>();
            for (int i : e.getValue()) {
                V3 at = frames.local(e.getKey(), all.get(i).contact());
                specs.add(new AxleLayout.WheelSpec(at.x(), at.z()));
            }
            int[] of = AxleLayout.axleOf(specs.size(), AxleLayout.cluster(specs));
            for (int k = 0; k < e.getValue().size() && k < of.length; k++) axle[e.getValue().get(k)] = of[k];
        }
        List<GroundSnapshot.Wheel> out = new ArrayList<>(all.size());
        for (int i = 0; i < all.size(); i++) {
            Vcc4Scanner.Wheel w = all.get(i);
            long key = w.pos().asLong();
            V3 at = frames.local(w.body(), w.contact());
            BlockPos governor = scan.governorFor(w.pos());
            boolean driven = governor != null && !host.governorExcluded(governor);
            Double angle = host.wheelAngleOf(key);
            BlockEntity be = SableBridge.getBlockEntityOnContraption(server, w.pos());
            double radius = OffroadWheels.radius(be);
            double extension = OffroadWheels.extension(be);
            boolean airborne = TelemetryMath.airborne(extension);
            double grip = TelemetryMath.grip(OffroadWheels.friction(be), airborne);
            double load = TelemetryMath.load(extension, radius);
            float applied = be instanceof KineticBlockEntity k ? k.getSpeed() : 0.0f;
            int brake = brakeSignal(server, w.pos(), now);
            double actual = Double.NaN;
            double slip = Double.NaN;
            if (!Double.isNaN(radius)) {
                double ground = groundSpeed(server, handleOf(scan, w.body()), w.pos());
                double free = TelemetryMath.freeRpm(applied, brake);
                actual = TelemetryMath.actualRpm(free, TelemetryMath.rollingRpm(ground, radius), grip, airborne);
                slip = airborne ? Double.NaN : TelemetryMath.slip(actual, ground, radius);
            }
            out.add(new GroundSnapshot.Wheel(w.body(), axle[i], governor == null ? -1 : governorIndex.getOrDefault(governor, -1),
                    (float) at.x(), (float) at.y(), (float) at.z(), driven, angle != null, (float) radius,
                    (float) train.wheelCapOf(i), (float) Vcc4Reflect.wheelStrength(be), angle == null ? 0f : angle.floatValue(),
                    applied, (float) actual, (float) slip, (float) grip, (float) load, airborne, brake));
        }
        return out;
    }

    private static int brakeSignal(ServerLevel server, BlockPos pos, long now) {
        int override = Vcc4BrakeOverrides.forTick(pos, now);
        return override >= 0 ? override : server.getSignal(pos.above(), Direction.UP);
    }

    private double groundSpeed(ServerLevel server, @Nullable Object handle, BlockPos pos) {
        Vector3d world = SableBridge.localToWorld(handle, new Vector3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
        Vec3 at = world == null ? null : SableBridge.velocityAt(server, handle, new Vec3(world.x, world.y, world.z));
        Vector3d v = at == null ? new Vector3d(lastVelocity) : new Vector3d(at.x, at.y, at.z);
        Quaterniond q = SableBridge.bodyOrientation(handle);
        Vector3d up = q == null ? new Vector3d(0, 1, 0) : q.transform(new Vector3d(0, 1, 0));
        double along = v.dot(up);
        return v.sub(new Vector3d(up).mul(along)).length();
    }

    private List<GroundSnapshot.Jet> jets(ServerLevel server, Vcc4Scanner.Scan scan, Frames frames) {
        List<GroundSnapshot.Jet> out = new ArrayList<>();
        for (int i = 0; i < scan.props().size(); i++) {
            Vcc4Scanner.Prop p = scan.props().get(i);
            long key = p.pos().asLong();
            V3 at = frames.local(p.body(), p.centre());
            JetMixer.Job job = JetMixer.jobOf(host.jetSpecOf(p));
            Vcc4Jets.Reading r = host.jetReadingNow(p);
            double force;
            double ceiling;
            double clear = 1.0;
            JetSupply supply = JetSupply.NONE;
            boolean lit;
            double rpm = Double.NaN;
            if (r != null) {
                force = r.force();
                ceiling = r.ceiling();
                clear = r.clear();
                supply = r.supply();
                lit = r.lit();
            } else {
                BlockEntity be = SableBridge.getBlockEntityOnContraption(server, p.pos());
                force = Math.abs(Vcc4BE.thrustAlongAxis(be, p));
                ceiling = p.strength();
                SableBridge.PropData data = SableBridge.readProp(be);
                if (data != null) rpm = data.rpm();
                lit = force > 1.0e-3;
            }
            out.add(new GroundSnapshot.Jet("J" + (i + 1), p.body(), (float) at.x(), (float) at.y(), (float) at.z(),
                    frames.axis(p.axis()), p.kind(), job == null ? -1 : job.ordinal(), (float) host.jetCommandOf(key),
                    (float) force, (float) ceiling, (float) clear, supply, lit, host.jetForeign(key), (float) rpm));
        }
        return out;
    }

    private List<GroundSnapshot.Unit> units(ServerLevel server, Vcc4Scanner.Scan scan, Frames frames,
                                            Map<Integer, Vcc4Train.Plan> plans) {
        Vcc4Train train = host.train();
        List<GroundSnapshot.Unit> out = new ArrayList<>();
        int[] wheelCount = new int[Math.max(1, scan.bodies().size())];
        for (Vcc4Scanner.Wheel w : scan.wheels()) if (w.body() >= 0 && w.body() < wheelCount.length) wheelCount[w.body()]++;
        if (scan.bodies().isEmpty()) {
            out.add(new GroundSnapshot.Unit(0, -1, BodyRole.PART, LinkKind.JOINT, TrailerMode.OFF, true, false, 0f, 0f, 0f,
                    Float.NaN, 0f, Float.NaN, Float.NaN, Float.NaN, 0, wheelCount[0], (float) train.massOf(0), "B1"));
            return out;
        }
        Vector3d nose = host.nose();
        Vector3d right = host.right();
        for (Vcc4Scanner.Body body : scan.bodies()) {
            int b = body.index();
            if (b == 0) {
                out.add(new GroundSnapshot.Unit(0, -1, BodyRole.PART, LinkKind.JOINT, TrailerMode.OFF, true, false, 0f, 0f, 0f,
                        Float.NaN, 0f, Float.NaN, Float.NaN, Float.NaN, 0, wheelCount[0], (float) train.massOf(0), "B1"));
                continue;
            }
            BodyRole role = train.roleNow(b);
            Vcc4Train.Plan plan = role.takesMode() ? train.displayPlan(server, b) : null;
            if (plan != null) plans.put(b, plan);
            V3 at = frames.vehicle(host.onMain(b, frames.centre()[b]));
            Vector3d ahead = host.dirOnMain(b, nose);
            double yaw = Math.toDegrees(Math.atan2(ahead.dot(right), ahead.dot(nose)));
            int parent = Math.max(0, body.parent());
            double articulation = plan != null && b < wheelCount.length && wheelCount[b] > 0 ? train.articulationOf(b, plan)
                    : Double.NaN;
            double rate = Double.isFinite(articulation)
                    ? -Math.toDegrees(train.bodyYawRate(b) - train.bodyYawRate(parent)) : 0.0;
            int flags = 0;
            if (plan != null) {
                if (plan.command.swaying()) flags |= GroundSnapshot.Unit.FLAG_SWAYING;
                if (plan.snapping) flags |= GroundSnapshot.Unit.FLAG_SNAPPING;
                if (plan.powered) flags |= GroundSnapshot.Unit.FLAG_POWERED;
                if (plan.docking) flags |= GroundSnapshot.Unit.FLAG_DOCKING;
                if (train.overrun(plan)) flags |= GroundSnapshot.Unit.FLAG_OVERRUN;
                if (plan.lost) flags |= GroundSnapshot.Unit.FLAG_LOST;
            }
            out.add(new GroundSnapshot.Unit(b, body.parent(), role, body.link(), train.modeOf(b), host.framePlaced(b),
                    body.foreign(), (float) at.x(), (float) at.z(), Double.isFinite(yaw) ? (float) yaw : 0f,
                    (float) articulation, (float) rate, plan == null ? Float.NaN : (float) plan.reading,
                    plan == null ? Float.NaN : (float) plan.force, plan == null ? Float.NaN : (float) plan.snapShare, flags,
                    b < wheelCount.length ? wheelCount[b] : 0, (float) train.massOf(b), "B" + (b + 1)));
        }
        return out;
    }

    private List<GroundSnapshot.Tie> ties(Vcc4Scanner.Scan scan, @Nullable Object craft, Frames frames,
                                          Map<Integer, Vcc4Train.Plan> plans) {
        List<GroundSnapshot.Tie> out = new ArrayList<>();
        List<Vcc4Scanner.Body> bodies = scan.bodies();
        for (int i = 0; i < scan.ties().size(); i++) {
            Vcc4Scanner.Tie t = scan.ties().get(i);

            if (t.kind().style() == LinkKind.Style.NONE) continue;
            V3 a = frames.vehicle(host.onMain(t.a(), Vcc4Scanner.centre(t.posA())));
            V3 c;
            if (t.b() >= 0) {
                c = frames.vehicle(host.onMain(t.b(), Vcc4Scanner.centre(t.posB())));
            } else {
                Vector3d local = SableBridge.worldToLocal(craft, Vcc4Scanner.centre(t.posB()));
                if (local == null || !finite(local)) continue;
                c = frames.vehicle(local);
            }
            int child = -1;
            if (t.b() >= 0 && t.b() < bodies.size() && bodies.get(t.b()).parent() == t.a()) child = t.b();
            else if (t.a() > 0 && t.b() >= 0 && t.a() < bodies.size() && bodies.get(t.a()).parent() == t.b()) child = t.a();
            float reading = Float.NaN;
            Vcc4Train.Plan plan = child > 0 ? plans.get(child) : null;
            if (plan != null && bodies.get(child).link() == t.kind()) {
                Double own = plan.tieReadings.get(i);
                if (own != null) reading = own.floatValue();
                else if (Double.isFinite(plan.reading)) reading = (float) plan.reading;
            }
            out.add(new GroundSnapshot.Tie(t.kind(), t.a(), (float) a.x(), (float) a.z(), t.b(), (float) c.x(), (float) c.z(),
                    reading));
        }
        return out;
    }

    private GroundSnapshot.Terrain terrain(ServerLevel server, Vector3d at, long now) {
        double half = 0.5 * PATCH_SIZE * PATCH_CELL;
        boolean off = patch.size() == 0 || Math.abs(at.x - (patch.originX() + half)) > half * 0.5
                || Math.abs(at.z - (patch.originZ() + half)) > half * 0.5;
        if (off || now - lastPatch >= PATCH_INTERVAL || now < lastPatch) {
            lastPatch = now;
            double ox = Math.floor((at.x - half) / PATCH_CELL) * PATCH_CELL;
            double oz = Math.floor((at.z - half) / PATCH_CELL) * PATCH_CELL;
            short[] heights = new short[PATCH_SIZE * PATCH_SIZE];
            for (int iz = 0; iz < PATCH_SIZE; iz++) {
                for (int ix = 0; ix < PATCH_SIZE; ix++) {
                    double h = groundAt(server, ox + (ix + 0.5) * PATCH_CELL, oz + (iz + 0.5) * PATCH_CELL);
                    heights[iz * PATCH_SIZE + ix] = Double.isFinite(h)
                            ? (short) Math.max(Short.MIN_VALUE + 1, Math.min(Short.MAX_VALUE, h))
                            : GroundSnapshot.Terrain.UNKNOWN;
                }
            }
            patch = new GroundSnapshot.Terrain(ox, oz, PATCH_CELL, PATCH_SIZE, heights, Double.NaN);
        }
        return new GroundSnapshot.Terrain(patch.originX(), patch.originZ(), patch.cell(), patch.size(), patch.heights(),
                groundAt(server, at.x, at.z));
    }

    private static double groundAt(ServerLevel server, double x, double z) {
        BlockPos column = BlockPos.containing(x, 0, z);
        if (!server.hasChunkAt(column)) return Double.NaN;
        return server.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
    }

    private void shapes(ServerLevel server, Vcc4Scanner.Scan scan, Frames frames, long now) {
        if (lastShapes != Long.MIN_VALUE && now >= lastShapes && now - lastShapes < SHAPE_INTERVAL) return;
        lastShapes = now;
        int n = Math.max(1, scan.bodies().size());
        List<GroundSnapshot.Shape> next = new ArrayList<>(n);
        Map<Integer, String> keys = new HashMap<>();
        for (int b = 0; b < n; b++) {
            Object handle = handleOf(scan, b);
            int[] box = plotBounds(handle);
            Vector3d c = frames.centre()[b];
            String key = box == null ? "none" : Arrays.toString(box) + "@" + Math.round(c.x * 2) + "," + Math.round(c.y * 2)
                    + "," + Math.round(c.z * 2);
            keys.put(b, key);
            GroundSnapshot.Shape kept = null;
            for (GroundSnapshot.Shape s : shapes) if (s.unit() == b) kept = s;
            if (kept != null && key.equals(shapeKeys.get(b))) {
                next.add(kept);
                continue;
            }
            next.add(walk(server, b, box, frames));
        }
        shapes = List.copyOf(next);
        shapeKeys.clear();
        shapeKeys.putAll(keys);
    }

    private GroundSnapshot.Shape walk(ServerLevel server, int unit, @Nullable int[] box, Frames frames) {
        if (box == null) {
            if (unit == 0) blocks = -1;
            return GroundSilhouette.of(unit, List.of());
        }
        long volume = (long) (box[3] - box[0] + 1) * (box[4] - box[1] + 1) * (box[5] - box[2] + 1);
        if (volume <= 0 || volume > SHAPE_MAX_CELLS) {
            if (unit == 0) blocks = -1;
            return GroundSilhouette.of(unit, List.of());
        }
        List<V3> centres = new ArrayList<>();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = box[0]; x <= box[3]; x++) {
            for (int y = box[1]; y <= box[4]; y++) {
                for (int z = box[2]; z <= box[5]; z++) {
                    at.set(x, y, z);
                    if (server.getBlockState(at).isAir()) continue;
                    centres.add(frames.local(unit, new Vector3d(x + 0.5, y + 0.5, z + 0.5)));
                }
            }
        }
        if (unit == 0) blocks = centres.size();
        return GroundSilhouette.of(unit, centres);
    }

    @Nullable
    private Object handleOf(Vcc4Scanner.Scan scan, int body) {
        if (body == 0) return SableBridge.subLevelAccessOf(host);
        return body > 0 && body < scan.bodies().size() ? scan.bodies().get(body).handle() : null;
    }

    @Nullable
    private static RpmGovernorBE governorAt(ServerLevel server, @Nullable BlockPos pos) {
        if (pos == null) return null;
        return SableBridge.getBlockEntityOnContraption(server, pos) instanceof RpmGovernorBE g ? g : null;
    }

    private static final String[] BOX = {"minX", "minY", "minZ", "maxX", "maxY", "maxZ"};
    private static boolean plotWarned;

    // null when sable can't say, empty is better than wrong
    @Nullable
    private static int[] plotBounds(@Nullable Object subLevel) {
        if (subLevel == null) return null;
        Object plot = ReflectionAccessors.invoke(ReflectionAccessors.method(subLevel.getClass(), "getPlot"), subLevel);
        Object box = plot == null ? null
                : ReflectionAccessors.invoke(ReflectionAccessors.method(plot.getClass(), "getBoundingBox"), plot);
        if (box == null) {
            warnPlot("no getPlot().getBoundingBox() on " + subLevel.getClass().getName());
            return null;
        }
        int[] out = new int[6];
        for (int i = 0; i < 6; i++) {
            Method m = ReflectionAccessors.method(box.getClass(), BOX[i]);
            if (m == null) {
                try {
                    m = box.getClass().getMethod(BOX[i]);
                } catch (NoSuchMethodException e) {
                    warnPlot("no " + BOX[i] + "() on " + box.getClass().getName());
                    return null;
                }
            }
            if (!(ReflectionAccessors.invoke(m, box) instanceof Number n)) return null;
            out[i] = n.intValue();
        }
        return out;
    }

    private static void warnPlot(String detail) {
        if (plotWarned) return;
        plotWarned = true;
        CreateStats.LOGGER.warn("[VCC Mk IV] plot bounds unavailable, the Stats Display draws no outline: {}", detail);
    }

    private static V3 v3(Vector3d v) {
        return new V3(v.x, v.y, v.z);
    }

    private static boolean finite(Vector3d v) {
        return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }
}
