package com.createstats.vccmk4;

import com.createstats.flight.bridge.SableBridge;
import com.createstats.flight.runtime.WheelMountSteeringOverrides;
import com.createstats.vccmk4.core.BodyRole;
import com.createstats.vccmk4.core.DriveModel;
import com.createstats.vccmk4.core.JetKind;
import com.createstats.vccmk4.core.JetMixer;
import com.createstats.vccmk4.core.JetRole;
import com.createstats.vccmk4.core.LinkKind;
import com.createstats.vccmk4.core.LinkTrim;
import com.createstats.vccmk4.core.ThrustShare;
import com.createstats.vccmk4.core.TrailerAssist;
import com.createstats.vccmk4.core.TrailerMode;
import com.createstats.vccmk4.core.TrailerSteer;
import com.createstats.vccmk4.core.TrainBalance;
import com.createstats.vccmk4.core.TrainRoles;
import com.createstats.vccmk4.core.WheelForce;
import com.createstats.vccmk4.network.Vcc4Snapshot;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class Vcc4Train {
    static final int BREAKAWAY_TICKS = 100;

    static final int SWAY_CONFIRM_TICKS = 4;

    static final int RESCAN_ASK_TICKS = 10;

    private static final Map<UUID, Tow> TOWED = new ConcurrentHashMap<>();

    private record Tow(WeakReference<Vcc4BE> by, long tick) {
    }

    private record Breakaway(List<BlockPos> wheels, long until) {
    }

    static final class Runtime {
        double lastReading = Double.NaN;
        double readingRate;
        Vector3d lastTow;

        long tieKey;
        double vx;
        double vy;
        boolean hasVelocity;

        Vector3d lastHitch;
        double hvx;
        double hvy;
        boolean hasHitchVelocity;
        int swayTicks;

        final Map<Long, Double> steer = new HashMap<>();
    }

    static final class Plan {
        TrailerMode mode = TrailerMode.OFF;
        TrailerAssist.Command command = TrailerAssist.Command.OFF;

        double tx;
        double ty = 1.0;

        double hx;
        double hy;
        double reading = Double.NaN;
        double force;

        double snapShare = Double.NaN;
        boolean snapping;
        boolean docking;
        boolean powered;

        boolean lost;
        final Map<Long, Double> steer = new HashMap<>();

        final Map<Integer, Double> tieReadings = new HashMap<>();
    }

    private final Vcc4BE host;
    private BodyRole[] roles = {BodyRole.PART};
    private int[] owners = {0};
    private int[] roots = {0};
    private List<UUID> ids = List.of();
    private double[] masses = new double[0];

    private double[] yawInertia = new double[0];
    private double[] wheelCap = new double[0];
    private double[] bodyYaw = new double[0];
    private double[] bodyYawRate = new double[0];
    private double lastYaw = Double.NaN;
    private double yawRate;
    private final Map<UUID, Runtime> runtime = new HashMap<>();
    private Plan[] plans = new Plan[0];
    private final Set<BlockPos> handsOff = new HashSet<>();
    private final Map<Long, Double> jetCommands = new HashMap<>();
    private final Map<UUID, List<BlockPos>> drivenWheels = new HashMap<>();
    private final Map<UUID, Breakaway> breakaways = new HashMap<>();
    private Vector3d jetCom = new Vector3d();
    private String towedBy = "";
    private long rescanAskedAt = Long.MIN_VALUE / 2;

    private double pushWay;

    Vcc4Train(Vcc4BE host) {
        this.host = host;
    }

    void rescan(ServerLevel server, int articulatedHalf, long now) {
        Vcc4Scanner.Scan scan = host.scan();
        List<Vcc4Scanner.Body> bodies = scan.bodies();
        Map<UUID, double[]> yaws = new HashMap<>();
        for (int b = 1; b < ids.size() && b < bodyYaw.length && b < bodyYawRate.length; b++) {
            if (ids.get(b) != null) yaws.put(ids.get(b), new double[]{bodyYaw[b], bodyYawRate[b]});
        }
        List<TrainRoles.Facts> facts = new ArrayList<>(bodies.size());
        List<UUID> newIds = new ArrayList<>(bodies.size());
        int[] parents = new int[bodies.size()];
        boolean[] rigid = new boolean[bodies.size()];
        for (int b = 0; b < bodies.size(); b++) {
            Vcc4Scanner.Body body = bodies.get(b);
            facts.add(new TrainRoles.Facts(body.parent(), hasWheels(body.index()), body.load(), body.towed(),
                    body.index() == articulatedHalf));
            newIds.add(body.id());
            parents[b] = body.parent();
            rigid[b] = b > 0 && body.link().rigid();
        }
        roles = TrainRoles.classify(facts);
        owners = TrainRoles.owners(facts, roles);
        roots = TrainRoles.rigidRoots(parents, rigid);
        ids = newIds;
        bodyYaw = new double[bodies.size()];
        Arrays.fill(bodyYaw, Double.NaN);
        bodyYawRate = new double[bodies.size()];
        for (int b = 1; b < bodies.size(); b++) {
            double[] kept = ids.get(b) == null ? null : yaws.get(ids.get(b));
            if (kept == null) continue;
            bodyYaw[b] = kept[0];
            bodyYawRate[b] = kept[1];
        }
        Set<UUID> present = new HashSet<>();
        for (UUID id : ids) if (id != null) present.add(id);
        runtime.keySet().retainAll(present);

        for (Map.Entry<UUID, List<BlockPos>> e : drivenWheels.entrySet()) {
            if (!present.contains(e.getKey())) breakaways.put(e.getKey(), new Breakaway(List.copyOf(e.getValue()), now + BREAKAWAY_TICKS));
        }
        drivenWheels.keySet().retainAll(present);
        breakaways.keySet().removeAll(present);
        plans = new Plan[bodies.size()];
        measure(server);
        jetCom = rigidGroupCentre();
    }

    private void measure(ServerLevel server) {
        Vcc4Scanner.Scan scan = host.scan();
        List<Vcc4Scanner.Body> bodies = scan.bodies();
        masses = new double[bodies.size()];
        yawInertia = new double[bodies.size()];
        Arrays.fill(yawInertia, Double.NaN);
        for (Vcc4Scanner.Body body : bodies) {
            Object handle = handleOf(body.index());
            double m = handle == null ? Double.NaN : SableBridge.massOf(handle);
            masses[body.index()] = m > 0.0 && Double.isFinite(m) ? m : Double.NaN;
        }
        wheelCap = new double[scan.wheels().size()];
        for (int i = 0; i < scan.wheels().size(); i++) {
            Vcc4Scanner.Wheel w = scan.wheels().get(i);
            BlockEntity mount = SableBridge.getBlockEntityOnContraption(server, w.pos());
            double normal = Double.NaN;
            Vector3d centre = SableBridge.centerOfMassLocal(handleOf(w.body()));
            double[] inertia = SableBridge.inertiaTensor(mount);
            double m = w.body() < masses.length ? masses[w.body()] : Double.NaN;
            if (centre != null && inertia != null) {
                Vector3d r = new Vector3d(w.contact()).sub(centre);
                normal = WheelForce.normalMass(m, inertia, r.x, r.y, r.z);

                if (w.body() < yawInertia.length && !Double.isFinite(yawInertia[w.body()]) && inertia[4] > 0.0) {
                    yawInertia[w.body()] = inertia[4];
                }
            }
            wheelCap[i] = WheelForce.capMass(normal, Vcc4Reflect.wheelStrength(mount));
        }
        for (Vcc4Scanner.Prop p : scan.props()) {
            if (p.body() >= yawInertia.length || Double.isFinite(yawInertia[p.body()])) continue;
            double[] inertia = SableBridge.inertiaTensor(SableBridge.getBlockEntityOnContraption(server, p.pos()));
            if (inertia != null && inertia[4] > 0.0) yawInertia[p.body()] = inertia[4];
        }
    }

    BodyRole roleNow(int b) {
        BodyRole r = b >= 0 && b < roles.length ? roles[b] : BodyRole.PART;
        if (r != BodyRole.TOWED) return r;
        Vcc4BE other = towedConsole(b);
        return other != null && other.driving() ? BodyRole.SEPARATE : BodyRole.TOWED;
    }

    boolean takesMode(int b) {
        return b > 0 && roleNow(b).takesMode();
    }

    int ownerOf(int b) {
        return b >= 0 && b < owners.length ? owners[b] : 0;
    }

    TrailerMode modeOf(int b) {
        List<Vcc4Scanner.Body> bodies = host.scan().bodies();
        return b > 0 && b < bodies.size() ? host.setup().trailerModeFor(bodies.get(b).id()) : TrailerMode.OFF;
    }

    boolean leftAlone(int b) {
        if (b <= 0) return false;
        BodyRole r = roleNow(b);
        if (r.neverTouched()) return true;
        if (r == BodyRole.PART) {
            int owner = ownerOf(b);
            return owner > 0 && owner != b && leftAlone(owner);
        }
        return r.takesMode() && modeOf(b) == TrailerMode.OFF;
    }

    boolean inTractor(int b) {
        return b == 0 || (b > 0 && ownerOf(b) == 0 && roleNow(b) == BodyRole.PART);
    }

    boolean ownFrame(int b) {
        int owner = ownerOf(b);
        return owner > 0 && takesMode(owner) && rootOf(owner) != 0;
    }

    int rootOf(int b) {
        return b >= 0 && b < roots.length ? roots[b] : 0;
    }

    @Nullable
    private Vcc4BE towedConsole(int b) {
        List<Vcc4Scanner.Body> bodies = host.scan().bodies();
        Level level = host.getLevel();
        int at = b;
        int guard = 0;
        while (at > 0 && at < bodies.size() && guard++ < bodies.size()) {
            Vcc4Scanner.Body body = bodies.get(at);
            if (body.console() != null && level != null) {
                return SableBridge.getBlockEntityOnContraption(level, body.console()) instanceof Vcc4BE c ? c : null;
            }
            at = body.parent();
        }
        return null;
    }

    private boolean hasWheels(int body) {
        for (Vcc4Scanner.Wheel w : host.scan().wheels()) if (w.body() == body) return true;
        return false;
    }

    @Nullable
    private Object handleOf(int body) {
        if (body == 0) return SableBridge.subLevelAccessOf(host);
        List<Vcc4Scanner.Body> bodies = host.scan().bodies();
        return body > 0 && body < bodies.size() ? bodies.get(body).handle() : null;
    }

    void updateRates() {
        Quaterniond q = SableBridge.bodyOrientation(SableBridge.subLevelAccessOf(host));
        if (q != null) {
            Vector3d f = q.transform(host.nose());
            double yaw = Math.atan2(-f.z, f.x);
            if (Double.isFinite(lastYaw) && Double.isFinite(yaw)) yawRate = smooth(yawRate, TrailerSteer.wrapPi(yaw - lastYaw) * 20.0);
            lastYaw = yaw;
        }
        for (int b = 1; b < bodyYaw.length; b++) {
            Vector3d ahead = host.dirOnMain(b, host.nose());
            double yaw = Math.atan2(ahead.dot(host.nose()), ahead.dot(host.right()));
            if (Double.isFinite(bodyYaw[b]) && Double.isFinite(yaw)) {
                bodyYawRate[b] = smooth(bodyYawRate[b], TrailerSteer.wrapPi(yaw - bodyYaw[b]) * 20.0);
            }
            bodyYaw[b] = yaw;
        }
    }

    private static double smooth(double previous, double sample) {
        return Double.isFinite(sample) ? previous + 0.5 * (sample - previous) : previous;
    }

    void tick(ServerLevel server, long now, DriveModel.Output out, @Nullable double[] geometric) {
        Vcc4Scanner.Scan scan = host.scan();
        int n = scan.bodies().size();
        plans = new Plan[n];
        handsOff.clear();
        jetCommands.clear();
        drivenWheels.clear();
        brakeBreakaways(now);
        jetCom = host.com();
        if (n <= 1) return;
        Quaterniond q0 = SableBridge.bodyOrientation(SableBridge.subLevelAccessOf(host));
        double speed0 = host.forwardSpeed();
        double a0 = tractorAcceleration(server, out);
        int going = TrainBalance.travel(speed0);
        pushWay = going != 0 ? going : (Double.isFinite(a0) ? Math.signum(a0) : 0.0);
        boolean holding = out.holding() || (out.braking() && Math.abs(speed0) < TrainBalance.REST_SPEED);

        TrailerAssist.Train train = new TrailerAssist.Train(a0, speed0, holding, out.throttle(),
                out.brakeSignal(), host.setup().options.maxRpm());
        List<List<WheelForce.Wheel>> wheelsByBody = wheelsByBody(server);
        for (int b = 1; b < n; b++) {
            if (!takesMode(b)) continue;
            Plan plan = new Plan();
            plans[b] = plan;
            plan.mode = modeOf(b);
            place(b, plan);
        }
        List<TrainBalance.Body> balance = balance(wheelsByBody);
        int[] carriers = TrainBalance.carriers(balance);
        double[] centre = turnCentre(geometric, q0);
        int travel = TrainBalance.travel(speed0);
        for (int b = 1; b < n; b++) {
            Plan plan = plans[b];
            if (plan == null) continue;
            Vcc4Scanner.Body body = scan.bodies().get(b);
            Runtime rt = body.id() == null ? new Runtime() : runtime.computeIfAbsent(body.id(), k -> new Runtime());
            read(server, b, body, plan, rt);
            trackTowPoint(b, body, rt, q0);
            List<WheelForce.Wheel> wheels = wheelsByBody.get(b);
            plan.powered = powered(wheels) || pushJets(b, plan) > 0;
            if (plan.mode == TrailerMode.OFF) {
                plan.command = TrailerAssist.Command.OFF;
                continue;
            }
            if (plan.lost) {
                plan.command = TrailerAssist.Command.HOLD;
                continue;
            }
            if (plan.mode == TrailerMode.MIRROR) {
                plan.command = TrailerAssist.mirror(train);
                continue;
            }
            TrailerSteer.Input input = steerInput(b, body, plan, rt, centre, out);
            if (!body.link().holds() && rt.hasHitchVelocity) {
                Vector3d pin = host.onMain(Math.max(0, body.parent()), hitchLocal(b));
                input = TrailerSteer.drawbar(input, host.lateral(pin), host.longitudinal(pin), rt.hvx, rt.hvy);
            }
            double[] angles = TrailerSteer.angles(input);
            List<Long> keys = wheelKeys(b);
            if (angles != null) {
                for (int i = 0; i < angles.length && i < keys.size(); i++) rt.steer.put(keys.get(i), angles[i]);
            }
            plan.steer.putAll(rt.steer);
            double m = massWithParts(b);
            double speed = trailerSpeed(body, plan, q0);
            double error = body.link().articulates() && b < bodyYawRate.length
                    ? yawRate + bodyYawRate[b] - TrailerSteer.desiredRate(input) : Double.NaN;

            rt.swayTicks = Double.isFinite(error) && Math.abs(error) > TrailerAssist.SWAY_RATE ? rt.swayTicks + 1 : 0;
            double sway = rt.swayTicks >= SWAY_CONFIRM_TICKS ? error : Double.NaN;
            double demand = TrainBalance.demand(b, balance, carriers, a0, speed0, travel);
            double trim = LinkTrim.of(body.link().reading(), m, plan.reading, plan.force, rt.readingRate);
            if (Double.isFinite(m) && Double.isFinite(demand) && Double.isFinite(trim)) {
                double[] push = measuredPush(server, b, plan);
                plan.command = TrailerAssist.assist(train, demand - push[1], trim, wheels, speed, maxMount(b), sway, push[0], m);
                if (body.link().reading() != LinkKind.Reading.STRETCH) plan.force = demand + trim;
            } else {
                plan.command = TrailerAssist.mirror(train);
            }
        }

        for (int b = 1; b < n; b++) {
            if (plans[b] != null && plans[b].command.touch() && ids.get(b) != null && roleNow(b) == BodyRole.TRAILER) {
                drivenWheels.put(ids.get(b), wheelPositions(b));
            }
        }
        planJets(server, out);
        planGovernors();
        for (int b = 1; b < n; b++) {
            if (ids.get(b) != null && roleNow(b) == BodyRole.TOWED) TOWED.put(ids.get(b), new Tow(new WeakReference<>(host), now));
        }
        jetCom = rigidGroupCentre();
    }

    void idle(long now) {
        brakeBreakaways(now);
    }

    void release() {
        runtime.clear();
        plans = new Plan[host.scan().bodies().size()];
        handsOff.clear();
        jetCommands.clear();
        drivenWheels.clear();
        TOWED.values().removeIf(t -> t.by().get() == host || t.by().get() == null);
    }

    private void brakeBreakaways(long now) {
        breakaways.values().removeIf(b -> b.until() < now);
        for (Breakaway b : breakaways.values()) {
            for (BlockPos p : b.wheels()) Vcc4BrakeOverrides.setForTick(p, Vcc4BE.FULL_BRAKE, now);
        }
    }

    @Nullable
    Plan planFor(int b) {
        int owner = ownerOf(b);
        int at = owner > 0 ? owner : b;
        return at > 0 && at < plans.length ? plans[at] : null;
    }

    Set<BlockPos> handsOff() {
        return handsOff;
    }

    double jetCommand(Vcc4Scanner.Prop p) {
        Double u = jetCommands.get(p.pos().asLong());
        return u == null ? Double.NaN : u;
    }

    Vector3d jetCom() {
        return jetCom;
    }

    private List<TrainBalance.Body> balance(List<List<WheelForce.Wheel>> wheelsByBody) {
        List<Vcc4Scanner.Body> bodies = host.scan().bodies();
        List<TrainBalance.Body> out = new ArrayList<>(bodies.size());
        for (Vcc4Scanner.Body body : bodies) {
            int b = body.index();
            Plan plan = b < plans.length ? plans[b] : null;
            if (plan == null) {
                out.add(new TrainBalance.Body(body.parent(), 0.0, List.of()));
                continue;
            }
            double m = massWithParts(b);
            boolean selfDriven = plan.mode == TrailerMode.MIRROR && powered(wheelsByBody.get(b));
            if (selfDriven) {
                out.add(new TrainBalance.Body(body.parent(), 0.0, List.of()));
                continue;
            }
            boolean assist = plan.mode == TrailerMode.ASSIST;
            List<WheelForce.Wheel> wheels = assist ? wheelsByBody.get(b) : undriven(wheelsByBody.get(b));
            boolean jets = assist && ownFrame(b) && pushJets(b, plan) > 0;
            out.add(new TrainBalance.Body(body.parent(), Double.isFinite(m) ? m : 0.0, wheels, jets));
        }
        return out;
    }

    private double massWithParts(int b) {
        if (b >= masses.length) return Double.NaN;
        double m = masses[b];
        for (int p = 1; p < masses.length && p < owners.length; p++) {
            if (p != b && owners[p] == b && roleNow(p) == BodyRole.PART) m += masses[p];
        }
        return m;
    }

    private double tractorAcceleration(ServerLevel server, DriveModel.Output out) {
        Vcc4Scanner.Scan scan = host.scan();
        double brake = out.brakeSignal() / 15.0;
        double mass = 0.0;
        for (int b = 0; b < masses.length; b++) {
            if (!inTractor(b)) continue;
            if (!(masses[b] > 0.0)) return Double.NaN;
            mass += masses[b];
        }
        double drive = 0.0;
        double drag = 0.0;
        for (int i = 0; i < scan.wheels().size(); i++) {
            Vcc4Scanner.Wheel w = scan.wheels().get(i);
            if (!inTractor(w.body())) continue;
            BlockEntity be = SableBridge.getBlockEntityOnContraption(server, w.pos());
            double surface = Vcc4Reflect.wheelSurface(be);
            double speed = be instanceof KineticBlockEntity k ? k.getSpeed() * host.wheelForwardSign(w) : 0.0;
            drive += WheelForce.drive(speed, brake, surface);
            drag += WheelForce.dragPerSpeed(new WheelForce.Wheel(true, surface, capOf(i)), brake);
        }
        for (Vcc4Scanner.Prop p : scan.props()) {
            if (!inTractor(p.body())) continue;
            // measured force through the live direction or assist asks for the wrong amount

            drive += jetForce(server, p).dot(host.nose());
        }
        return TrainBalance.tractorAcceleration(mass, drive, drag, host.forwardSpeed());
    }

    private Vector3d jetForce(ServerLevel server, Vcc4Scanner.Prop p) {
        if (p.direct()) {
            Vcc4Jets.Reading r = host.jetReading(server, p);
            return r == null ? new Vector3d() : host.dirOnMain(p.body(), r.forceVector());
        }
        BlockEntity be = SableBridge.getBlockEntityOnContraption(server, p.pos());
        return host.dirOnMain(p.body(), p.axis()).mul(Vcc4BE.thrustAlongAxis(be, p));
    }

    private double[] measuredPush(ServerLevel server, int body, Plan plan) {
        double auto = 0.0;
        double other = 0.0;
        for (Vcc4Scanner.Prop p : host.scan().props()) {
            if (ownerOf(p.body()) != body && p.body() != body) continue;
            if (!pushes(axisOf(p), plan)) continue;
            Vector3d f = jetForce(server, p);
            double along = f.dot(host.right()) * plan.tx + f.dot(host.nose()) * plan.ty;
            if (!Double.isFinite(along)) continue;
            if (trailerJet(p)) auto += along;
            else other += along;
        }
        return new double[]{auto, other};
    }

    boolean towsArticulatingAssist() {
        for (Vcc4Scanner.Body body : host.scan().bodies()) {
            int b = body.index();
            if (takesMode(b) && modeOf(b) == TrailerMode.ASSIST && body.link().articulates()) return true;
        }
        return false;
    }

    private double capOf(int wheel) {
        return wheel >= 0 && wheel < wheelCap.length ? wheelCap[wheel] : WheelForce.DEFAULT_STRENGTH;
    }

    private List<List<WheelForce.Wheel>> wheelsByBody(ServerLevel server) {
        Vcc4Scanner.Scan scan = host.scan();
        List<List<WheelForce.Wheel>> out = new ArrayList<>();
        for (int b = 0; b < scan.bodies().size(); b++) out.add(new ArrayList<>());
        for (int i = 0; i < scan.wheels().size(); i++) {
            Vcc4Scanner.Wheel w = scan.wheels().get(i);
            if (w.body() < 0 || w.body() >= out.size()) continue;
            BlockPos g = scan.governorFor(w.pos());
            boolean driven = g != null && !host.setup().governorExcluded(g.asLong());
            double surface = Vcc4Reflect.wheelSurface(SableBridge.getBlockEntityOnContraption(server, w.pos()));
            out.get(w.body()).add(new WheelForce.Wheel(driven, surface, capOf(i)));
        }
        return out;
    }

    private static boolean powered(List<WheelForce.Wheel> wheels) {
        for (WheelForce.Wheel w : wheels) if (w.driven()) return true;
        return false;
    }

    private static List<WheelForce.Wheel> undriven(List<WheelForce.Wheel> wheels) {
        List<WheelForce.Wheel> out = new ArrayList<>(wheels.size());
        for (WheelForce.Wheel w : wheels) out.add(new WheelForce.Wheel(false, w.surface(), w.capMass()));
        return out;
    }

    private List<Long> wheelKeys(int body) {
        List<Long> out = new ArrayList<>();
        for (Vcc4Scanner.Wheel w : host.scan().wheels()) if (w.body() == body) out.add(w.pos().asLong());
        return out;
    }

    private List<BlockPos> wheelPositions(int body) {
        List<BlockPos> out = new ArrayList<>();
        for (Vcc4Scanner.Wheel w : host.scan().wheels()) if (w.body() == body) out.add(w.pos());
        return out;
    }

    private double maxMount(int body) {
        double best = Double.MAX_VALUE;
        for (Vcc4Scanner.Wheel w : host.scan().wheels()) {
            if (w.body() != body || host.scan().governorFor(w.pos()) == null) continue;
            double ratio = Math.abs(host.wheelRatio(w.pos().asLong()));
            best = Math.min(best, Vcc4BE.MAX_PROP_RPM * Math.max(0.05, ratio));
        }
        return best == Double.MAX_VALUE ? 0.0 : best;
    }

    private void place(int b, Plan plan) {
        Vector3d tow = host.onMain(b, towLocal(b));
        plan.hx = host.lateral(tow);
        plan.hy = host.longitudinal(tow);
        double cx = 0.0;
        double cy = 0.0;
        int count = 0;
        Vector3d axis = null;
        for (Vcc4Scanner.Wheel w : host.scan().wheels()) {
            if (w.body() != b) continue;
            Vector3d c = host.onMain(b, w.contact());
            cx += host.lateral(c);
            cy += host.longitudinal(c);
            count++;
            if (axis == null) axis = host.dirOnMain(b, w.push());
        }
        if (count == 0) return;
        double ax = axis.dot(host.right());
        double ay = axis.dot(host.nose());
        double len = Math.hypot(ax, ay);
        if (len < 1.0e-6) return;
        ax /= len;
        ay /= len;
        double toward = (plan.hx - cx / count) * ax + (plan.hy - cy / count) * ay;
        if (toward < 0.0 || (Math.abs(toward) < 1.0e-3 && ay < 0.0)) {
            ax = -ax;
            ay = -ay;
        }
        plan.tx = ax;
        plan.ty = ay;
    }

    Plan placement(int b) {
        Plan live = b < plans.length ? plans[b] : null;
        if (live != null) return live;
        Plan plan = new Plan();
        place(b, plan);
        return plan;
    }

    private List<Integer> tiesOf(int b) {
        Vcc4Scanner.Scan scan = host.scan();
        List<Integer> out = new ArrayList<>(2);
        if (b <= 0 || b >= scan.bodies().size()) return out;
        Vcc4Scanner.Body body = scan.bodies().get(b);
        for (int i = 0; i < scan.ties().size(); i++) {
            Vcc4Scanner.Tie t = scan.ties().get(i);
            if (t.kind() == body.link() && t.joins(b, body.parent())) out.add(i);
        }
        return out;
    }

    private Vector3d towLocal(int b) {
        return endsCentre(b, b);
    }

    private Vector3d hitchLocal(int b) {
        return endsCentre(b, Math.max(0, host.scan().bodies().get(b).parent()));
    }

    private Vector3d endsCentre(int b, int on) {
        Vector3d sum = new Vector3d();
        int n = 0;
        for (int i : tiesOf(b)) {
            sum.add(Vcc4Scanner.centre(host.scan().ties().get(i).endOn(on)));
            n++;
        }
        if (n > 0) return sum.div(n);
        Vcc4Scanner.Body body = host.scan().bodies().get(b);
        BlockPos end = on == b || body.joint() == null ? body.anchor() : body.joint();
        return Vcc4Scanner.centre(end);
    }

    private long tieKey(int b) {
        long key = 17L;
        for (int i : tiesOf(b)) {
            Vcc4Scanner.Tie t = host.scan().ties().get(i);
            key += Long.rotateLeft(t.posA().asLong(), 17) ^ (t.posB().asLong() * 0x9E3779B97F4A7C15L);
        }
        return key;
    }

    Plan displayPlan(ServerLevel server, int b) {
        Plan live = b < plans.length ? plans[b] : null;
        if (live != null) return live;
        pushWay = 0.0;
        Plan plan = new Plan();
        plan.mode = modeOf(b);
        place(b, plan);
        read(server, b, host.scan().bodies().get(b), plan, new Runtime());
        for (Vcc4Scanner.Wheel w : host.scan().wheels()) {
            if (w.body() != b) continue;
            BlockPos g = host.scan().governorFor(w.pos());
            plan.powered |= g != null && !host.setup().governorExcluded(g.asLong());
        }
        return plan;
    }

    private void read(ServerLevel server, int b, Vcc4Scanner.Body body, Plan plan, Runtime rt) {
        int parent = Math.max(0, body.parent());
        List<Integer> ties = tiesOf(b);
        List<TrailerLinks.Reading> each = new ArrayList<>(Math.max(1, ties.size()));
        plan.tieReadings.clear();
        if (ties.isEmpty()) {
            BlockEntity near = body.joint() == null ? null : SableBridge.getBlockEntityOnContraption(server, body.joint());
            BlockEntity far = SableBridge.getBlockEntityOnContraption(server, body.anchor());
            each.add(body.linkType() == null ? null
                    : body.linkType().read(server, new TrailerLinks.Ends(near, parent, far, b, this::worldPoint)));
        }
        for (int i : ties) {
            Vcc4Scanner.Tie t = host.scan().ties().get(i);
            BlockEntity near = SableBridge.getBlockEntityOnContraption(server, t.endOn(parent));
            BlockEntity far = SableBridge.getBlockEntityOnContraption(server, t.endOn(b));
            TrailerLinks.Reading one = t.type().read(server, new TrailerLinks.Ends(near, parent, far, b, this::worldPoint));
            each.add(one);
            if (one != null && Double.isFinite(one.value())) plan.tieReadings.put(i, one.value());
        }
        boolean changed = false;
        for (TrailerLinks.Reading one : each) changed |= one == null || (one.kind() != null && one.kind() != body.link());
        TrailerLinks.Reading r = TrailerLinks.combine(body.link().reading(), each);
        if (r == null) plan.lost = true;
        if (changed) askRescan(server.getGameTime());
        double reading = r == null ? Double.NaN : r.value();
        if (body.link().reading() == LinkKind.Reading.ANGLE) reading = articulationDeg(parent, plan);
        plan.reading = reading;
        plan.force = r == null ? Double.NaN : r.force();
        plan.snapShare = r == null ? Double.NaN : r.share();
        plan.snapping = r != null && r.warning();
        plan.docking = r != null && r.settling();
        boolean gap = body.link().reading() == LinkKind.Reading.GAP || body.link().reading() == LinkKind.Reading.STRETCH;
        if (gap && Double.isFinite(reading) && Double.isFinite(rt.lastReading)) {
            rt.readingRate = smooth(rt.readingRate, (reading - rt.lastReading) * 20.0);
        } else {
            rt.readingRate = 0.0;
        }
        rt.lastReading = gap ? reading : Double.NaN;
    }

    private void askRescan(long now) {
        if (now - rescanAskedAt < RESCAN_ASK_TICKS) return;
        rescanAskedAt = now;
        host.requestRescan();
    }

    private double articulationDeg(int parent, Plan plan) {
        double mine = Math.atan2(plan.ty, plan.tx);
        double theirs = Math.PI / 2;
        if (parent > 0) {
            Plan p = parent < plans.length ? plans[parent] : null;
            if (p != null) theirs = Math.atan2(p.ty, p.tx);
            else if (parent < bodyYaw.length && Double.isFinite(bodyYaw[parent])) theirs = bodyYaw[parent];
        }
        return -Math.toDegrees(TrailerSteer.wrapPi(mine - theirs));
    }

    private void trackTowPoint(int b, Vcc4Scanner.Body body, Runtime rt, @Nullable Quaterniond q0) {
        long key = tieKey(b);
        if (key != rt.tieKey) {
            rt.tieKey = key;
            rt.lastTow = null;
            rt.lastHitch = null;
            rt.hasHitchVelocity = false;
        }
        Vector3d tow = worldPoint(b, towLocal(b));
        double[] v = velocityBetween(tow, rt.lastTow, q0);
        if (v != null) {
            rt.vx = rt.hasVelocity ? smooth(rt.vx, v[0]) : v[0];
            rt.vy = rt.hasVelocity ? smooth(rt.vy, v[1]) : v[1];
            rt.hasVelocity = true;
        } else if (q0 != null) {
            Vector3d sable = SableBridge.linearVelocityWorldOf(body.handle());
            if (sable != null && finite(sable)) {
                Vector3d local = new Quaterniond(q0).conjugate().transform(new Vector3d(sable));
                rt.vx = local.dot(host.right());
                rt.vy = local.dot(host.nose());
                rt.hasVelocity = true;
            }
        }
        rt.lastTow = tow;
        Vector3d hitch = worldPoint(Math.max(0, body.parent()), hitchLocal(b));
        double[] hv = velocityBetween(hitch, rt.lastHitch, q0);
        if (hv != null) {
            rt.hvx = rt.hasHitchVelocity ? smooth(rt.hvx, hv[0]) : hv[0];
            rt.hvy = rt.hasHitchVelocity ? smooth(rt.hvy, hv[1]) : hv[1];
            rt.hasHitchVelocity = true;
        }
        rt.lastHitch = hitch;
    }

    @Nullable
    private double[] velocityBetween(@Nullable Vector3d now, @Nullable Vector3d before, @Nullable Quaterniond q0) {
        if (now == null || before == null || q0 == null) return null;
        Vector3d local = new Quaterniond(q0).conjugate().transform(new Vector3d(now).sub(before).mul(20.0));
        double vx = local.dot(host.right());
        double vy = local.dot(host.nose());
        return Double.isFinite(vx) && Double.isFinite(vy) && Math.hypot(vx, vy) < 60.0 ? new double[]{vx, vy} : null;
    }

    private double trailerSpeed(Vcc4Scanner.Body body, Plan plan, @Nullable Quaterniond q0) {
        Vector3d v = SableBridge.linearVelocityWorldOf(body.handle());
        if (v == null || q0 == null || !finite(v)) return host.forwardSpeed();
        Vector3d local = new Quaterniond(q0).conjugate().transform(new Vector3d(v));
        double s = local.dot(host.right()) * plan.tx + local.dot(host.nose()) * plan.ty;
        return Double.isFinite(s) ? s : host.forwardSpeed();
    }

    private TrailerSteer.Input steerInput(int b, Vcc4Scanner.Body body, Plan plan, Runtime rt, @Nullable double[] centre,
                                          DriveModel.Output out) {
        List<Double> xs = new ArrayList<>();
        List<Double> ys = new ArrayList<>();
        for (Vcc4Scanner.Wheel w : host.scan().wheels()) {
            if (w.body() != b) continue;
            Vector3d c = host.onMain(b, w.contact());
            xs.add(host.lateral(c));
            ys.add(host.longitudinal(c));
        }
        int parent = body.parent();
        double parentRate = yawRate + (parent > 0 && parent < bodyYawRate.length ? bodyYawRate[parent] : 0.0);
        return new TrailerSteer.Input(plan.hx, plan.hy, rt.vx, rt.vy, Math.atan2(plan.ty, plan.tx), parentRate, centre, Math.PI / 2,
                xs.stream().mapToDouble(Double::doubleValue).toArray(), ys.stream().mapToDouble(Double::doubleValue).toArray(),
                body.link(), out.lockDeg());
    }

    @Nullable
    private double[] turnCentre(@Nullable double[] geometric, @Nullable Quaterniond q0) {
        Vector3d v = SableBridge.linearVelocityWorld(host);
        if (v == null || q0 == null || !finite(v)) return geometric;
        Vector3d local = new Quaterniond(q0).conjugate().transform(new Vector3d(v));
        Vector3d com = host.com();
        return TrailerSteer.turnCentre(geometric, host.lateral(com), host.longitudinal(com), local.dot(host.right()),
                local.dot(host.nose()), yawRate);
    }

    @Nullable
    Vector3d worldPoint(int body, Vector3d local) {
        Object handle = handleOf(body);
        if (handle == null) return null;
        Vector3d w = SableBridge.localToWorld(handle, local);
        return w != null && finite(w) ? w : null;
    }

    @Nullable
    private Vector3d consolePoint(Vector3d world) {
        Vector3d p = SableBridge.worldToLocal(SableBridge.subLevelAccessOf(host), world);
        return p != null && finite(p) ? p : null;
    }

    private Vector3d rigidGroupCentre() {
        Vector3d com = host.com();
        if (!(masses.length > 0 && masses[0] > 0.0)) return com;
        Vector3d sum = new Vector3d(com).mul(masses[0]);
        double total = masses[0];
        List<Vcc4Scanner.Body> bodies = host.scan().bodies();
        for (int b = 1; b < bodies.size() && b < masses.length; b++) {
            if (!takesMode(b) || rootOf(b) != 0 || leftAlone(b)) continue;
            Vector3d c = SableBridge.centerOfMassLocal(bodies.get(b).handle());
            if (c == null || !(masses[b] > 0.0)) return com;
            sum.add(host.onMain(b, c).mul(masses[b]));
            total += masses[b];
        }
        return sum.div(total);
    }

    Vector3d centreOf(int body) {
        Vector3d c = SableBridge.centerOfMassLocal(handleOf(body));
        if (c != null && finite(c)) return host.onMain(body, c);
        Vector3d sum = new Vector3d();
        int n = 0;
        for (Vcc4Scanner.Wheel w : host.scan().wheels()) {
            if (w.body() != body) continue;
            sum.add(host.onMain(body, w.contact()));
            n++;
        }
        return n > 0 ? sum.div(n) : host.onMain(body, Vcc4Scanner.centre(host.scan().bodies().get(body).anchor()));
    }

    void driveWheel(long now, Vcc4Scanner.Wheel w, Plan plan, Map<BlockPos, double[]> governorDemand, Map<Long, Double> wheelAngle) {
        long key = w.pos().asLong();
        TrailerAssist.Command c = plan.command;
        if (c == null || !c.touch()) {
            wheelAngle.remove(key);
            return;
        }
        double angle = plan.steer.getOrDefault(key, 0.0);
        int steps = com.createstats.vccmk4.core.GroundSteer.signalSteps(angle);
        WheelMountSteeringOverrides.setForTick(w.pos(), angle > 0 ? steps : 0, angle < 0 ? steps : 0, now);

        Vcc4YawOverrides.setForTick(w.pos(), angle, now);
        Vector3d p = host.onMain(w.body(), w.contact());

        double side = (host.lateral(p) - plan.hx) * plan.ty - (host.longitudinal(p) - plan.hy) * plan.tx;
        Vcc4BrakeOverrides.setForTick(w.pos(), side > 0.0 ? c.brakeRight() : c.brakeLeft(), now);
        wheelAngle.put(key, angle);
        BlockPos governor = host.scan().governorFor(w.pos());
        if (governor == null || host.setup().governorExcluded(governor.asLong())) return;
        double sign = trailerWheelSign(w, plan);
        if (sign == 0.0) return;
        double ratio = host.wheelRatio(key);
        if (host.setup().governorFlipped(governor.asLong())) ratio = -ratio;
        Vcc4BE.accumulate(governorDemand, governor, c.mountSpeed() * sign / ratio);
    }

    private double trailerWheelSign(Vcc4Scanner.Wheel w, Plan plan) {
        Vector3d push = host.dirOnMain(w.body(), w.push());
        double along = push.dot(host.right()) * plan.tx + push.dot(host.nose()) * plan.ty;
        return Math.abs(along) < 0.5 ? 0.0 : Math.signum(along);
    }

    private record Axis(double x, double y, boolean vertical) {
    }

    private Axis axisOf(Vcc4Scanner.Prop p) {
        Vector3d axis = p.body() == 0 ? new Vector3d(p.axis()) : host.dirOnMain(p.body(), p.axis());
        return new Axis(axis.dot(host.right()), axis.dot(host.nose()), Math.abs(axis.y) >= JetMixer.VERTICAL);
    }

    private boolean trailerJet(Vcc4Scanner.Prop p) {
        return ownFrame(p.body()) && host.setup().jetRoleFor(p.pos().asLong()) == JetRole.AUTO;
    }

    private static boolean pushes(Axis a, Plan plan) {
        return !a.vertical() && Math.abs(a.x() * plan.tx + a.y() * plan.ty) >= Math.abs(a.x() * plan.ty - a.y() * plan.tx);
    }

    // thrusters with no driven wheels counted as unpowered before. the trailer ahead got asked to carry them
    private int pushJets(int body, Plan plan) {
        int n = 0;
        for (Vcc4Scanner.Prop p : host.scan().props()) {
            if (ownerOf(p.body()) != body && p.body() != body) continue;
            if (!trailerJet(p) || p.kind() == JetKind.BOOSTER) continue;
            if (!p.direct() && host.scan().governorFor(p.pos()) == null) continue;
            Axis a = axisOf(p);
            if (!pushes(a, plan)) continue;
            if (p.direct()) {
                Vcc4Jets.Reading r = host.jetReadingNow(p);
                if (r != null && (r.supply().out() || !(r.ceiling() > 0.0))) continue;
                double along = a.x() * plan.tx + a.y() * plan.ty;
                if (pushWay != 0.0 && along * pushWay < 0.0) continue;
            }
            n++;
        }
        return n;
    }

    private void planJets(ServerLevel server, DriveModel.Output out) {
        Vcc4Scanner.Scan scan = host.scan();
        Map<Integer, List<Vcc4Scanner.Prop>> push = new HashMap<>();
        Map<Integer, List<Vcc4Scanner.Prop>> side = new HashMap<>();
        for (Vcc4Scanner.Prop p : scan.props()) {
            if (!trailerJet(p)) continue;
            int owner = ownerOf(p.body()) > 0 ? ownerOf(p.body()) : p.body();
            Plan plan = owner < plans.length ? plans[owner] : null;
            jetCommands.put(p.pos().asLong(), 0.0);
            if (plan == null || !plan.command.touch() || plan.lost) continue;
            Axis a = axisOf(p);
            if (a.vertical()) continue;
            (pushes(a, plan) ? push : side).computeIfAbsent(owner, k -> new ArrayList<>()).add(p);
        }
        for (Map.Entry<Integer, List<Vcc4Scanner.Prop>> e : push.entrySet()) planPush(server, plans[e.getKey()], e.getKey(), e.getValue(), out);
        for (Map.Entry<Integer, List<Vcc4Scanner.Prop>> e : side.entrySet()) planSide(server, plans[e.getKey()], e.getKey(), e.getValue());
    }

    private void planPush(ServerLevel server, Plan plan, int owner, List<Vcc4Scanner.Prop> jets, DriveModel.Output out) {
        if (plan.mode == TrailerMode.MIRROR) {
            for (Vcc4Scanner.Prop p : jets) {
                Axis a = axisOf(p);
                double along = a.x() * plan.tx + a.y() * plan.ty;
                jetCommands.put(p.pos().asLong(), out.boosting() ? out.pushDir() * Math.signum(along) : 0.0);
            }
            return;
        }
        if (plan.mode != TrailerMode.ASSIST) return;
        int n = jets.size();
        double[] ceiling = new double[n];
        double[] clear = new double[n];
        double[] along = new double[n];
        boolean[] twoWay = new boolean[n];
        for (int i = 0; i < n; i++) {
            Vcc4Scanner.Prop p = jets.get(i);
            Axis a = axisOf(p);
            along[i] = a.x() * plan.tx + a.y() * plan.ty;
            ceiling[i] = ceilingOf(server, p, owner);
            clear[i] = clearOf(server, p);
            twoWay[i] = !p.kind().oneWay();
        }
        double[] u = ThrustShare.share(plan.command.pushForce(), ceiling, clear, along, twoWay);
        for (int i = 0; i < n; i++) jetCommands.put(jets.get(i).pos().asLong(), clamp(u[i], -1.0, 1.0));
    }

    private void planSide(ServerLevel server, Plan plan, int owner, List<Vcc4Scanner.Prop> jets) {
        if (plan.mode != TrailerMode.ASSIST || plan.command.swayAccel() == 0.0) return;
        int n = jets.size();
        double[] arms = new double[n];
        double[] ceiling = new double[n];
        double[] clear = new double[n];
        boolean[] twoWay = new boolean[n];
        for (int i = 0; i < n; i++) {
            Vcc4Scanner.Prop p = jets.get(i);
            Axis a = axisOf(p);
            Vector3d at = host.onMain(p.body(), p.centre());
            double arm = (host.lateral(at) - plan.hx) * a.y() - (host.longitudinal(at) - plan.hy) * a.x();
            arms[i] = Math.abs(arm) >= TrailerAssist.MIN_ARM ? arm : 0.0;
            ceiling[i] = ceilingOf(server, p, owner);
            clear[i] = clearOf(server, p);
            twoWay[i] = !p.kind().oneWay();
        }
        Vector3d c = centreOf(owner);
        double inertia = TrailerAssist.pivotInertia(yawInertiaOf(owner), massWithParts(owner),
                Math.hypot(host.lateral(c) - plan.hx, host.longitudinal(c) - plan.hy));
        if (!Double.isFinite(inertia)) {
            for (int i = 0; i < n; i++) {
                double u = arms[i] != 0.0 && ceiling[i] > 0.0 ? 0.5 * Math.signum(plan.command.swayAccel() * arms[i]) : 0.0;
                jetCommands.put(jets.get(i).pos().asLong(), u);
            }
            return;
        }
        double[] u = ThrustShare.share(inertia * plan.command.swayAccel(), ceiling, clear, arms, twoWay);
        for (int i = 0; i < n; i++) jetCommands.put(jets.get(i).pos().asLong(), clamp(u[i], -1.0, 1.0));
    }

    private double ceilingOf(ServerLevel server, Vcc4Scanner.Prop p, int owner) {
        if (p.kind() == JetKind.BOOSTER) return 0.0;
        double c = thrustPerCommand(server, p);
        if (c > 0.0 || p.direct()) return Math.max(0.0, c);
        double m = massWithParts(owner);
        return (m > 0.0 ? m : 100.0) * LinkTrim.LIMIT;
    }

    private double thrustPerCommand(ServerLevel server, Vcc4Scanner.Prop p) {
        if (p.direct()) {
            Vcc4Jets.Reading r = host.jetReading(server, p);
            if (r != null) return r.ceiling();
            return Double.isFinite(p.strength()) ? p.strength() : 0.0;
        }
        double gain = host.propGain(p.pos().asLong());
        return gain > 0.0 ? gain * Vcc4BE.MAX_PROP_RPM : 0.0;
    }

    private double clearOf(ServerLevel server, Vcc4Scanner.Prop p) {
        if (!p.direct()) return 1.0;
        Vcc4Jets.Reading r = host.jetReading(server, p);
        return r == null ? 1.0 : r.clear();
    }

    private double yawInertiaOf(int b) {
        if (b >= yawInertia.length) return Double.NaN;
        return yawInertia[b];
    }

    private void planGovernors() {
        Vcc4Scanner.Scan scan = host.scan();
        for (BlockPos g : scan.usedGovernors()) {
            boolean any = false;
            boolean ours = false;
            for (Vcc4Scanner.Wheel w : scan.wheels()) {
                if (!g.equals(scan.governorFor(w.pos()))) continue;
                any = true;
                ours |= !leftAlone(w.body());
            }
            for (Vcc4Scanner.Prop p : scan.props()) {
                if (!g.equals(scan.governorFor(p.pos()))) continue;
                any = true;
                ours |= !leftAlone(p.body());
            }
            if (any && !ours) handsOff.add(g);
        }
    }

    @Nullable
    JetMixer.Jet trailerJetSpec(Vcc4Scanner.Prop p, JetRole role) {
        if (p.body() <= 0 || !ownFrame(p.body())) return null;
        int owner = ownerOf(p.body()) > 0 ? ownerOf(p.body()) : p.body();
        Plan plan = placement(owner);
        Vector3d c = host.onMain(p.body(), p.centre());
        Vector3d axis = host.dirOnMain(p.body(), p.axis());
        Vector3d tc = centreOf(owner);
        double rx = host.lateral(c) - host.lateral(tc);
        double ry = host.longitudinal(c) - host.longitudinal(tc);
        double ax = axis.dot(host.right());
        double ay = axis.dot(host.nose());

        return new JetMixer.Jet(rx * plan.ty - ry * plan.tx, c.y - tc.y, rx * plan.tx + ry * plan.ty,
                ax * plan.ty - ay * plan.tx, axis.y, ax * plan.tx + ay * plan.ty, role);
    }

    boolean towedNow(long now) {
        UUID craft = host.craftId();
        Tow tow = craft == null ? null : TOWED.get(craft);
        Vcc4BE by = tow == null ? null : tow.by().get();
        boolean live = by != null && by != host && !by.isRemoved() && by.driving() && now - tow.tick() <= 2L;
        if (!live && tow != null) TOWED.remove(craft, tow);
        String name = live ? by.driverName() : "";
        if (!name.equals(towedBy)) {
            towedBy = name;
            host.publishNow();
        }
        return live;
    }

    String towedBy() {
        return towedBy;
    }

    boolean overrun(Plan plan) {
        return host.driving() && plan.mode == TrailerMode.ASSIST && plan.command.touch() && host.lastOutput().braking()
                && Math.abs(host.forwardSpeed()) > 2.0 && plan.command.brake() >= Vcc4BE.FULL_BRAKE && plan.command.made() < 0.6;
    }

    List<Vcc4Snapshot.Body> snapshotBodies(ServerLevel server, Map<Integer, Plan> shown) {
        List<Vcc4Snapshot.Body> out = new ArrayList<>();
        Vcc4Scanner.Scan scan = host.scan();
        for (Vcc4Scanner.Body body : scan.bodies()) {
            int b = body.index();
            if (b == 0) continue;
            int parent = Math.max(0, body.parent());
            Vector3d at = body.joint() == null ? Vcc4Scanner.centre(host.getBlockPos()) : host.onMain(parent, Vcc4Scanner.centre(body.joint()));
            int count = 0;
            for (Vcc4Scanner.Wheel w : scan.wheels()) if (w.body() == b) count++;

            Vector3d ahead = host.dirOnMain(b, host.nose());
            float yaw = (float) Math.toDegrees(Math.atan2(ahead.dot(host.right()), ahead.dot(host.nose())));
            BodyRole role = roleNow(b);
            Plan plan = role.takesMode() ? displayPlan(server, b) : null;
            if (plan != null) shown.put(b, plan);
            int flags = 0;
            float share = -1f;
            float drive = 0f;
            int brake = 0;
            float steer = 0f;
            if (plan != null) {
                if (plan.command.swaying()) flags |= Vcc4Snapshot.FLAG_SWAYING;
                if (plan.snapping) flags |= Vcc4Snapshot.FLAG_SNAPPING;
                if (plan.powered) flags |= Vcc4Snapshot.FLAG_POWERED;
                if (plan.docking) flags |= Vcc4Snapshot.FLAG_DOCKING;
                if (overrun(plan)) flags |= Vcc4Snapshot.FLAG_OVERRUN;
                if (plan.lost) flags |= Vcc4Snapshot.FLAG_LOST;
                if (host.driving() && plan.mode == TrailerMode.ASSIST && plan.command.touch() && !plan.lost) share = (float) plan.command.made();
                drive = (float) plan.command.mountSpeed();
                brake = plan.command.brake();
                for (long key : wheelKeys(b)) {
                    steer = plan.steer.getOrDefault(key, 0.0).floatValue();
                    break;
                }
            }
            out.add(new Vcc4Snapshot.Body(b, body.parent(), (float) host.lateral(at), (float) host.longitudinal(at),
                    body.link().rigid(), body.foreign(), host.framePlaced(b), count, Float.isFinite(yaw) ? yaw : 0f,
                    role.ordinal(), body.link().ordinal(), modeOf(b).ordinal(),
                    body.id() == null ? 0L : body.id().getLeastSignificantBits(),
                    plan == null || !Double.isFinite(plan.reading) ? Float.NaN : (float) plan.reading,
                    plan == null || !Double.isFinite(plan.force) ? 0f : (float) plan.force, drive, brake, steer, share, flags));
        }
        return out;
    }

    List<Vcc4Snapshot.Tie> snapshotTies(Map<Integer, Plan> shown) {
        List<Vcc4Snapshot.Tie> out = new ArrayList<>();
        Vcc4Scanner.Scan scan = host.scan();
        for (int i = 0; i < scan.ties().size(); i++) {
            Vcc4Scanner.Tie t = scan.ties().get(i);

            if (t.kind().style() == LinkKind.Style.NONE) continue;
            Vector3d a = host.onMain(t.a(), Vcc4Scanner.centre(t.posA()));
            Vector3d c = t.b() >= 0 ? host.onMain(t.b(), Vcc4Scanner.centre(t.posB())) : consolePoint(Vcc4Scanner.centre(t.posB()));
            if (c == null) continue;
            float reading = Float.NaN;
            int child = childOf(t);
            Plan plan = child > 0 ? shown.get(child) : null;
            if (plan != null && scan.bodies().get(child).link() == t.kind()) {
                Double own = plan.tieReadings.get(i);
                if (own != null) reading = own.floatValue();
                else if (Double.isFinite(plan.reading)) reading = (float) plan.reading;
            }
            out.add(new Vcc4Snapshot.Tie(t.kind().ordinal(), t.a(), (float) host.lateral(a), (float) host.longitudinal(a), t.b(),
                    (float) host.lateral(c), (float) host.longitudinal(c), reading));
        }
        return out;
    }

    private int childOf(Vcc4Scanner.Tie t) {
        List<Vcc4Scanner.Body> bodies = host.scan().bodies();
        if (t.b() >= 0 && t.b() < bodies.size() && bodies.get(t.b()).parent() == t.a()) return t.b();
        if (t.a() > 0 && t.b() >= 0 && t.a() < bodies.size() && bodies.get(t.a()).parent() == t.b()) return t.a();
        return -1;
    }

    void problems(List<String> out) {
        DriveModel.Output last = host.lastOutput();
        boolean reversing = last.throttle() < -1.0e-6 || host.forwardSpeed() < -DriveModel.STOPPED_SPEED;
        Vcc4Scanner.Scan scan = host.scan();
        for (Vcc4Scanner.Body body : scan.bodies()) {
            int b = body.index();
            if (!takesMode(b)) continue;
            String name = "B" + (b + 1);
            TrailerMode mode = modeOf(b);
            Plan plan = b < plans.length ? plans[b] : null;
            boolean driven = false;
            for (Vcc4Scanner.Wheel w : scan.wheels()) driven |= w.body() == b && scan.governorFor(w.pos()) != null;
            if (!body.link().pushes() && mode == TrailerMode.OFF) {
                out.add(name + " is Off on a " + body.link().noun() + ": nothing brakes it, so it runs into you when you brake. Assist brakes it first.");
            }
            if (!body.link().pushes() && reversing && !driven && mode != TrailerMode.OFF) {
                out.add(name + " hangs on a " + body.link().noun() + " and has no drive of its own: it cannot be pushed back. "
                        + "Reverse with it docked, or give it a driven axle.");
            }
            if (plan != null && plan.snapping) out.add(name + "'s " + body.link().noun() + " is near breaking: ease off, or it snaps and drops.");
            if (plan != null && overrun(plan)) out.add(name + "'s brakes cannot slow its weight as hard as you slow: it pushes while you brake.");
            if (plan != null && plan.lost) out.add(name + "'s " + body.link().noun() + " has gone: its brakes are on until the console looks again.");
            if (mode == TrailerMode.ASSIST && !(massWithParts(b) > 0.0)) {
                out.add("Sable did not give " + name + "'s mass, so Assist drives it the way Mirror would.");
            }
        }
        if (!breakaways.isEmpty()) {
            out.add(breakaways.size() == 1 ? "A trailer came loose: its breakaway brakes hold it for a few seconds."
                    : breakaways.size() + " trailers came loose: their breakaway brakes hold them for a few seconds.");
        }
    }

    String towingLine() {
        List<String> parts = new ArrayList<>();
        for (Vcc4Scanner.Body body : host.scan().bodies()) {
            int b = body.index();
            if (!takesMode(b)) continue;
            String mode = switch (modeOf(b)) {
                case ASSIST -> "Assist";
                case MIRROR -> "Mirror";
                case OFF -> "Off";
            };
            parts.add("B" + (b + 1) + (roleNow(b) == BodyRole.TOWED ? " (towed vehicle)" : "") + " " + body.link().towedAs()
                    + " (" + mode + ")");
        }
        return parts.isEmpty() ? "" : "Towing: " + String.join(", ", parts);
    }

    double yawRate() {
        return yawRate;
    }

    double bodyYawRate(int b) {
        return b > 0 && b < bodyYawRate.length && Double.isFinite(bodyYawRate[b]) ? bodyYawRate[b] : 0.0;
    }

    double massOf(int b) {
        return b >= 0 && b < masses.length ? masses[b] : Double.NaN;
    }

    double bodyYawInertia(int b) {
        return b >= 0 && b < yawInertia.length ? yawInertia[b] : Double.NaN;
    }

    double wheelCapOf(int wheel) {
        return wheel >= 0 && wheel < wheelCap.length ? wheelCap[wheel] : Double.NaN;
    }

    double articulationOf(int b, Plan plan) {
        if (b <= 0 || b >= host.scan().bodies().size() || plan == null) return Double.NaN;
        return articulationDeg(Math.max(0, host.scan().bodies().get(b).parent()), plan);
    }

    @Nullable
    String alert() {
        for (int b = 1; b < plans.length; b++) {
            Plan plan = plans[b];
            if (plan == null) continue;
            if (plan.lost) return "LINK B" + (b + 1);
            if (plan.command.swaying()) return "SWAY B" + (b + 1);
            if (plan.snapping) return "STRAIN B" + (b + 1);
        }
        return breakaways.isEmpty() ? null : "BREAKAWAY";
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static boolean finite(Vector3d v) {
        return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }
}
