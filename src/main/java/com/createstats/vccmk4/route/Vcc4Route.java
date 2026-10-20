package com.createstats.vccmk4.route;

import com.createstats.flight.bridge.SableBridge;
import com.createstats.registry.ModSounds;
import com.createstats.surveyor.PathwayStore;
import com.createstats.surveyor.PathwaySurveyor;
import com.createstats.surveyor.PathwaySync;
import com.createstats.surveyor.PathwayNetworks;
import com.createstats.surveyor.PathwayTraffic;
import com.createstats.surveyor.RouteChunkTickets;
import com.createstats.surveyor.RouteEvents;
import com.createstats.surveyor.RouteTrack;
import com.createstats.surveyor.RouteWords;
import com.createstats.surveyor.UnseenRoutes;
import com.createstats.surveyor.core.Comfort;
import com.createstats.surveyor.core.PathKind;
import com.createstats.surveyor.core.PathNetwork;
import com.createstats.surveyor.core.PathProjection;
import com.createstats.surveyor.core.PathNode;
import com.createstats.surveyor.core.PathSpline;
import com.createstats.surveyor.core.PathSurvey;
import com.createstats.surveyor.core.Pathway;
import com.createstats.surveyor.core.PlantModel;
import com.createstats.surveyor.core.RecordTrack;
import com.createstats.surveyor.core.RouteRunner;
import com.createstats.surveyor.core.RouteSchedule;
import com.createstats.surveyor.core.RouteTrace;
import com.createstats.surveyor.core.ScheduleRun;
import com.createstats.surveyor.core.Stop;
import com.createstats.surveyor.core.StopRules;
import com.createstats.surveyor.core.TrafficGap;
import com.createstats.vccmk4.core.Control;
import com.createstats.vccmk4.core.DriveModel;
import com.createstats.vccmk4.core.HeldKeys;
import com.createstats.vccmk4.core.Keymap;
import com.createstats.vccmk4.core.RouteDrive;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class Vcc4Route {
    static final int SWEEP_TICKS = 2;

    static final int HOLD_TICKS = 20;
    static final int BUDGET_RETRY_TICKS = 100;

    static final double HOLD_AHEAD = 48.0;

    static final int HORN_TICKS = 10;

    private final RouteHost host;
    @Nullable private UUID pathwayId;
    @Nullable private RouteRunner runner;
    private PlantModel plant;
    private RouteRunner.Options options = RouteRunner.Options.DEFAULT;
    @Nullable private UUID assignedBy;
    @Nullable private RouteRunner.Saved pendingRestore;
    private long lastVersion = -1;

    private RouteRunner.Command command = RouteRunner.Command.PARK;
    private boolean driving;
    private com.createstats.surveyor.core.ReturnStartup startup;
    private boolean travelLoad;
    private RouteRunner.Travel pendingRuntime;
    private int pendingTargetNode = Integer.MIN_VALUE;
    private com.createstats.surveyor.core.CargoSnapshot cargoSnapshot = com.createstats.surveyor.core.CargoSnapshot.unavailable(0);

    public RouteRunner.Command command() { return command; }

    public boolean awaitingVehicle() { return host.present() && (pendingLoad || startup != null); }

    private final StopLinks stopLinks = new StopLinks();
    private final RouteSensors.CargoWatch cargo = new RouteSensors.CargoWatch();
    private RouteSensors.Obstacle obstacle = RouteSensors.Obstacle.NONE;
    private StopRules.Verdict verdict;
    private long lastHold = Long.MIN_VALUE;
    private long budgetWait = Long.MIN_VALUE;
    private boolean sableHeld;
    private double lastHeading = Double.NaN;
    private double yawRate;
    private double front = 2.0;
    private double rear = 2.0;
    private double half = 1.5;
    private double height = 3.0;

    private boolean checkOnStart;
    private long extentsTick = Long.MIN_VALUE;
    private List<double[]> ownBounds = List.of();
    private int hornTicks;
    private int hornAux = -1;
    private boolean indicatedDeparture;

    private double pitch;
    private double roll;

    private final RouteDrive routeDrive = new RouteDrive();
    private RouteTrace trace = new RouteTrace();
    private double lastTraceSpeed = Double.NaN;

    private long lastAutoKeep = Long.MIN_VALUE;

    @Nullable private StopRules.Progress stopProgress;
    private int stopProgressNode = -1;

    @Nullable private ScheduleRun schedule;
    private ScheduleRun.Step scheduleStep;

    private final DockApproach dock = new DockApproach();

    private int pendingSend = -1;

    @Nullable private PathNetwork.Route netRoute;
    private int netLeg;
    private int netKey = -1;
    private int netCounter;
    static final int NETWORK_KEYS = 1_000_000;

    private final List<PathNetwork.Zone> heldZones = new ArrayList<>();
    private String zoneWait = "";
    private boolean zoneBinds;

    private int turnSignal;

    private boolean scheduleHeld;

    private long terrainAt = Long.MIN_VALUE / 2;
    private long terrainChanges = -1;
    private int terrainSurveyed = -1;
    private long terrainVersion = -1;

    private boolean wasHeld = true;

    @Nullable private RecordTrack recording;
    private boolean recordArmed;
    @Nullable private UUID recordFor;
    private boolean nearStartSaid;

    public Vcc4Route(RouteHost host) {
        this.host = host;
        this.plant = new PlantModel(128);
    }

    public RouteHost host() {
        return host;
    }

    public boolean assigned() {
        return runner != null;
    }

    @Nullable
    public UUID pathwayId() {
        return pathwayId;
    }

    @Nullable
    public RouteRunner runner() {
        return runner;
    }

    public RouteRunner.State state() {
        if (recording != null) return RouteRunner.State.RECORDING;
        return runner == null ? RouteRunner.State.PAUSED : runner.state();
    }

    public boolean drives() {
        return driving;
    }

    public RouteRunner.Options options() {
        return options;
    }

    public PlantModel plant() {
        return plant;
    }

    public boolean recording() {
        return recording != null;
    }

    public boolean recordArmed() {
        return recordArmed;
    }

    @Nullable
    public RecordTrack recordTrack() {
        return recording;
    }

    @Nullable
    public UUID assignedBy() {
        return assignedBy;
    }

    @Nullable
    public StopRules.Verdict verdict() {
        return verdict;
    }

    public String statusLine() {
        if (recording != null) return "Recording: " + recording.size() + " blocks";
        if (runner == null) return recordArmed ? "Record armed: drive off to start" : "";
        StringBuilder b = new StringBuilder(runner.pathway().name()).append(": ").append(RouteWords.state(runner.state()));
        String say = saying();
        if (!say.isEmpty()) b.append(" (").append(say).append(')');
        int next = runner.nextStopNode();
        if (runner.state() == RouteRunner.State.WAITING && verdict != null) {
            List<String> bits = new ArrayList<>();
            for (List<StopRules.Bar> alt : verdict.bars()) for (StopRules.Bar bar : alt) bits.add(bar.now());
            if (!bits.isEmpty()) b.append(", ").append(String.join(", ", bits));
        } else if (next >= 0) {
            b.append(", next ").append(runner.pathway().label(next));
            double eta = runner.eta();
            if (eta >= 0) b.append(" in ").append(RouteWords.seconds((int) Math.round(eta)));
        }
        return b.toString();
    }

    public String gogglesLine() {
        if (recording != null || recordArmed || runner == null) return statusLine().isEmpty() ? "" : "Route: " + statusLine();
        return "Route: " + statusLine() + (plant.learned() ? "" : " (learning its speeds)");
    }

    public String saying() {
        if (runner == null) return "";
        if (!runner.reason().isEmpty() && runner.state() != RouteRunner.State.HAZARD) return runner.reason();
        if (!dock.status().isEmpty()) return dock.status();
        if (zoneBinds && !zoneWait.isEmpty()) return zoneWait;
        return RouteWords.doing(runner.mode(), runner.subject());
    }

    public DriveModel.Output output(DriveModel.Options options, double forwardSpeed) {
        RouteRunner.Command c = command;
        boolean hold = c.brakeLevel() >= 1.0 && c.throttle() == 0.0;
        return routeDrive.step(options, c.throttle(), c.steer(), c.brakeLevel(), forwardSpeed, hold);
    }

    public void takeOver(DriveModel.Output from) {
        routeDrive.follow(from);
    }

    public RouteTrace trace() {
        return trace;
    }

    public int pulsedAux() {
        return host.present() && hornTicks > 0 ? hornAux : -1;
    }

    public int indicate() {
        if (!driving) return 0;
        return turnSignal != 0 ? turnSignal : command.indicate();
    }

    public boolean slowing() {
        return driving && runner != null && runner.slowing();
    }

    public void tick(ServerLevel level, long now, HeldKeys keys, Keymap keymap) {
        RouteHosts.register(host.consoleId(), this);
        if (hornTicks > 0) hornTicks--;
        if (recording != null || recordArmed) recordTick(level, now);
        if (runner == null) {
            driving = false;
            return;
        }
        PathwayStore store = PathwayStore.get(level);
        Pathway p = store.get(pathwayId);
        if (p == null) {
            unassign(level, "its pathway was deleted");
            return;
        }
        if (p.version() != lastVersion) {
            runner.setPathway(p);
            lastVersion = p.version();
            if (pendingRestore != null) {
                runner.restore(pendingRestore, now);
                pendingRestore = null;
            }
        }
        if (startup != null) {
            if (startup.waiting(host.returnReady())) {
                driving = false;
                command = RouteRunner.Command.PARK;
                return;
            }
            startup = null;
            travelLoad = false;
        }
        String cannot = host.cannotFollow();
        if (!cannot.isEmpty() && runner.state().drives()) runner.pause(cannot);

        if (runner.state() == RouteRunner.State.NO_BUDGET) runner.start(now, host.calibrated());

        Pose pose = pose();
        if (pose == null) {
            driving = false;
            command = RouteRunner.Command.PARK;
            return;
        }
        measureYaw(pose);
        if (now - extentsTick >= 20 || extentsTick == Long.MIN_VALUE) measureExtents(level, now);
        PathSpline sp = runner.spline();
        double v = host.measuredSpeed();

        if (host.present() && runner.state().drives() && runner.placed() && now % SWEEP_TICKS == 0) {
            double reach = plant.brakeDistance(Math.max(0.0, v)) + RouteSensors.MARGIN + 2.0;
            obstacle = RouteSensors.sweep(level, sp, runner.progress(), front, half, reach, host.ownBodies(), ownBounds);
        } else if (!host.present() || !runner.state().drives()) {
            obstacle = host.present() ? RouteSensors.Obstacle.NONE : host.unseenObstacle();
        }
        TrafficGap.Room room = room(level, sp);
        double speedAhead = 0.0;
        if (room.ahead() >= 0) speedAhead = aheadSpeed(level);
        double zone = zoneGap(level, sp, now);
        zoneBinds = zone < room.gap();
        double gapAhead = Math.min(room.gap(), zone);
        if (zoneBinds) speedAhead = 0.0;
        boolean departOk = false;
        if (host.present() && (runner.state() == RouteRunner.State.WAITING || now % 100 == 0 || !cargoSnapshot.known())) {
            cargo.tick(level, host.cargoBlocks(), now);
            if (cargo.readAt() == now) cargoSnapshot = cargo.snapshot(now);
        }
        StopRules.Cargo readings = host.present() ? cargo : host.frozenCargo();
        if (readings == null) readings = StopRules.EMPTY;
        if (runner.state() == RouteRunner.State.WAITING) {
            PathNode n = runner.pathway().node(runner.waitingAt());
            if (host.present()) cargo.readings(level.getDayTime(), seatedPlayers(level), n != null && stopPowered(level, n), dock.docked(level) || host.docked());
            Stop stop = n == null ? null : n.stop();
            if (schedule != null) {
                verdict = scheduleStep == null || scheduleStep.bars().isEmpty() ? null
                        : new StopRules.Verdict(false, false, scheduleStep.bars());
            } else if (stop != null) {
                if (stopProgress == null || stopProgressNode != runner.waitingAt() || stopProgress.arrived() != runner.waitingSince()
                        || !stopProgress.stop().equals(stop)) {
                    stopProgress = new StopRules.Progress(stop, runner.waitingSince());
                    stopProgressNode = runner.waitingAt();
                }
                verdict = stopProgress.tick(now, readings);
                departOk = verdict.go();
            } else {
                departOk = true;
            }

            if (host.present() && departOk && dock.holding(level)) departOk = false;
        } else {
            verdict = null;
        }
        boolean staff = host.present() && RouteReflect.staffHeld(level, host.serverSubLevel());
        boolean driverKeys = host.present() && host.seatedDriver() && driveKeysHeld(keys, keymap);
        double lock = host.lockAt(v);
        DriveModel.Output applied = host.appliedOutput();
        if (host.skidSteer() && plant.skidGain() <= 0.0) plant.seedSkid(2.0 * plant.k() / Math.max(0.5, 2.0 * host.halfTrack()));
        double wheelDrive = runner.state() == RouteRunner.State.STUCK || Math.abs(v) < 0.3 ? host.wheelDrive() : Double.NaN;

        double hazard = Double.POSITIVE_INFINITY;
        String hazardWhat = "";
        double unknown = Double.POSITIVE_INFINITY;
        if (runner.state().drives() && runner.placed()) {
            double speed = Math.max(0.0, v);
            double horizon = Math.max(32.0, speed * speed / 2.0 + speed + 16.0);
            PathSurvey survey = PathwaySurveyor.survey(level, p);
            double from = sp.closed() ? runner.progress() + front : Math.min(sp.length(), runner.progress() + front);
            PathSurvey.Wall w = survey.firstWall(from, horizon, p.amphibious(), height, p);
            if (w != null) {
                hazard = w.ahead();
                hazardWhat = w.what();
            }

            if (host.present()) unknown = survey.firstUnknown(from, Math.min(horizon, Double.isFinite(hazard) ? hazard : horizon));
            if (host.present() && (now % 20 == 0 || Double.isFinite(unknown))) PathwaySurveyor.ahead(level, p, runner.progress(), horizon + front);

            long changes = PathwaySurveyor.changes(level, p);
            if (now - terrainAt >= 40 && (changes != terrainChanges || survey.surveyedCount() != terrainSurveyed
                    || p.version() != terrainVersion)) {
                runner.terrain(survey.terrain());
                terrainAt = now;
                terrainChanges = changes;
                terrainSurveyed = survey.surveyedCount();
                terrainVersion = p.version();
            }
        }
        tickSchedule(level, p, sp, now);
        boolean leaving = pendingSend >= 0 || (verdict != null && verdict.go());
        if (host.present()) dock.tick(level, runner, p, sp, host, dockStop(p), leaving, now);
        if (pendingSend >= 0 && (!host.present() || !dock.holding(level))) {
            send(pendingSend);
            pendingSend = -1;
        }
        tickNetwork(level);
        RouteRunner.Sensors s = new RouteRunner.Sensors(now, pose.x, pose.y, pose.z, pose.hx, pose.hz, v,
                host.kappaPerStick(lock), host.skidSteer(), obstacle.distance(), obstacle.player(), obstacle.what(),
                gapAhead, speedAhead, departOk, staff, driverKeys, host.calibrated(), applied.throttle(), applied.braking(),
                applied.steer(), yawRate, pitch, roll, applied.brake(), wheelDrive, obstacle.block(), hazard, hazardWhat,
                unknown);
        RouteRunner.State before = runner.state();
        if (before == RouteRunner.State.CALIBRATING && !host.calibrated()) host.armCalibration();
        command = host.present() ? runner.tick(s) : runner.tickUnseen(s);
        driving = command.drive();
        // wake the body before pulling away, a braked sleeping body never wakes from its own wheels. sable tax #18

        boolean held = !driving || (command.brakeLevel() >= 1.0 && command.throttle() == 0.0);
        if (host.present() && driving && !held && (wasHeld || Math.abs(v) < 0.3)) host.wake();
        wasHeld = held;
        startCheck(level, now);
        if (host.present() && command.horn() && hornTicks == 0) {
            hornTicks = HORN_TICKS;
            hornAux = options.hornWhenBlocked() ? hornAuxFor(runner.pathway()) : -1;
        }
        dockedSignal(level);
        holdChunks(level, now, pose);
        events(level, now);
        report(level, pose);
        if (before != runner.state()) host.routeChanged();
    }

    private void startCheck(ServerLevel level, long now) {
        if (!checkOnStart || runner == null || !runner.placed() || !runner.state().drives()) return;
        checkOnStart = false;
        Pathway p = runner.pathway();
        PathSpline sp = runner.spline();
        double from = sp.closed() ? runner.progress() + front : Math.min(sp.length(), runner.progress() + front);
        double reach = sp.closed() ? sp.length() : Math.max(0.0, sp.length() - from);
        PathSurvey.Wall w = PathwaySurveyor.survey(level, p).firstWall(from, reach, p.amphibious(), height, p);
        if (w == null) return;
        String why = w.what() + ", " + RouteWords.distance(w.ahead()) + " ahead: fix it on the Board, then Start";
        runner.pause(why);
        tellOwner(level, host.vehicleName() + " will not set off on " + p.name() + ": " + why, false);
        host.routeChanged();
    }

    private record Pose(double x, double y, double z, double hx, double hz) {
    }

    @Nullable
    private Pose pose() {
        Object craft = host.craft();
        Vector3d local = host.referenceLocal();
        Vector3d world = craft == null ? new Vector3d(local) : SableBridge.localToWorld(craft, local);
        if (world == null || !Double.isFinite(world.x)) return null;
        Vector3d nose = new Vector3d(host.noseLocal());
        Vector3d right = new Vector3d(host.rightLocal());
        Quaterniond q = craft == null ? null : SableBridge.bodyOrientation(craft);
        if (q != null) {
            q.transform(nose);
            q.transform(right);
        }
        double len = Math.sqrt(nose.x * nose.x + nose.z * nose.z);
        if (len < 1.0e-6) return null;

        pitch = Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, nose.y / Math.max(1.0e-9, nose.length())))));
        roll = Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, -right.y / Math.max(1.0e-9, right.length())))));
        double hx = nose.x / len;
        double hz = nose.z / len;
        Pose p = new Pose(world.x, world.y, world.z, hx, hz);
        lastPose = p;
        return p;
    }

    private void measureYaw(Pose pose) {
        double heading = PathSpline.heading(pose.hx, pose.hz);
        yawRate = Double.isNaN(lastHeading) ? 0.0 : PathSpline.wrapAngle(heading - lastHeading) * 20.0;
        lastHeading = heading;
    }

    @Nullable
    private Pose lastPose;

    private void measureExtents(ServerLevel level, long now) {
        extentsTick = now;
        double[] saved = host.extents();
        if (!host.present() && saved != null && saved.length >= 4) {
            front = saved[0]; rear = saved[1]; half = saved[2]; height = saved[3]; ownBounds = List.of();
            return;
        }
        Object craft = host.craft();
        int[] b = RouteReflect.plotBounds(craft);
        Vector3d ref = host.referenceLocal();
        Vector3d nose = host.noseLocal();
        Vector3d right = host.rightLocal();
        if (b != null) {
            double f = 0.0;
            double r = 0.0;
            double w = 0.0;
            for (int i = 0; i < 8; i++) {
                double x = ((i & 1) == 0 ? b[0] : b[3] + 1) - ref.x;
                double y = ((i & 2) == 0 ? b[1] : b[4] + 1) - ref.y;
                double z = ((i & 4) == 0 ? b[2] : b[5] + 1) - ref.z;
                double along = x * nose.x + y * nose.y + z * nose.z;
                double across = x * right.x + y * right.y + z * right.z;
                f = Math.max(f, along);
                r = Math.max(r, -along);
                w = Math.max(w, Math.abs(across));
            }
            front = Math.max(0.5, f);
            rear = Math.max(0.5, r);
            half = Math.max(0.75, Math.min(4.0, w));

            height = Math.max(1.0, Math.min(PathSurvey.HEADROOM_MAX, b[4] + 1 - b[1]));
        }
        List<double[]> bounds = new ArrayList<>();
        double[] own = RouteReflect.worldBounds(craft);
        if (own != null) bounds.add(own);
        Pose pose = pose();
        for (UUID id : host.ownBodies()) {
            Object body = SableBridge.findSubLevelById(level, id);
            if (body == null || body == craft) continue;
            double[] wb = RouteReflect.worldBounds(body);
            if (wb == null) continue;
            bounds.add(wb);
            if (pose != null) {
                for (int i = 0; i < 4; i++) {
                    double x = ((i & 1) == 0 ? wb[0] : wb[3]) - pose.x;
                    double z = ((i & 2) == 0 ? wb[2] : wb[5]) - pose.z;
                    rear = Math.max(rear, -(x * pose.hx + z * pose.hz));
                }
            }
        }
        ownBounds = bounds;
    }

    private TrafficGap.Room room(ServerLevel level, PathSpline sp) {
        if (runner == null || !runner.placed()) return TrafficGap.Room.CLEAR;
        List<RouteTrack> others = PathwayTraffic.on(level, pathwayId);
        List<TrafficGap.Car> cars = new ArrayList<>();
        cars.add(new TrafficGap.Car(runner.progress(), front, rear, host.measuredSpeed()));
        aheadTracks.clear();
        for (RouteTrack t : others) {
            if (t.console().equals(host.consoleId())) continue;
            RouteRunner.State st = RouteRunner.State.byOrdinal(t.state());

            double progress = t.s();
            if (!st.drives()) {
                PathProjection.Result on = PathProjection.global(sp, t.x(), t.y(), t.z(), 0, 0, 3, 3, 180);
                if (!on.found() || Math.abs(on.lateral()) > 1.5) continue;
                progress = on.s();
            }
            cars.add(new TrafficGap.Car(progress, t.front(), t.rear(), t.speed()));
            aheadTracks.add(t);
        }

        for (RouteTrack t : PathwayTraffic.all(level)) {
            if (t.pathway().equals(pathwayId) || t.console().equals(host.consoleId())) continue;
            boolean moving = RouteRunner.State.byOrdinal(t.state()).drives();
            if (Math.abs(t.x() - sp.x(runner.progress())) > 96.0 || Math.abs(t.z() - sp.z(runner.progress())) > 96.0) continue;
            double hx = moving ? Math.sin(t.heading()) : 0;
            double hz = moving ? -Math.cos(t.heading()) : 0;
            PathProjection.Result on = PathProjection.global(sp, t.x(), t.y(), t.z(), hx, hz, 1.5, 3.0, moving ? 60.0 : 180.0);
            if (!on.found() || Math.abs(on.lateral()) > 1.5) continue;
            cars.add(new TrafficGap.Car(on.s(), t.front(), t.rear(), t.speed()));
            aheadTracks.add(t);
        }
        if (cars.size() < 2) return TrafficGap.Room.CLEAR;
        TrafficGap.Room r = TrafficGap.roomOf(sp, cars.toArray(new TrafficGap.Car[0]), 0);
        aheadIndex = r.ahead() - 1;
        return r;
    }

    private final List<RouteTrack> aheadTracks = new ArrayList<>();
    private int aheadIndex = -1;

    private double aheadSpeed(ServerLevel level) {
        return aheadIndex >= 0 && aheadIndex < aheadTracks.size() ? aheadTracks.get(aheadIndex).speed() : 0.0;
    }

    private static boolean driveKeysHeld(HeldKeys keys, Keymap km) {
        if (keys == null || keys.isEmpty()) return false;
        return km.axis(Control.THROTTLE, keys) != 0.0 || km.axis(Control.STEER, keys) != 0.0 || km.held(Control.BRAKE, keys);
    }

    private int hornAuxFor(Pathway p) {
        for (PathNode n : p.nodes()) if (n.isStop() && n.stop().hornAux() >= 0) return n.stop().hornAux();
        return -1;
    }

    private void dockedSignal(ServerLevel level) {
        if (!host.present()) {
            indicatedDeparture = runner != null && runner.state() == RouteRunner.State.DEPARTING;
            return;
        }
        if (runner == null || runner.state() != RouteRunner.State.WAITING) {
            if (runner != null && runner.state() == RouteRunner.State.DEPARTING && !indicatedDeparture) departurePulse();
            if (runner != null && runner.state() != RouteRunner.State.DEPARTING) indicatedDeparture = false;
            stopLinks.clear(level);
            return;
        }
        PathNode n = runner.pathway().node(runner.waitingAt());
        if (n == null || n.stop() == null || !n.stop().docks()) {
            stopLinks.clear(level);
            return;
        }
        stopLinks.set(level, BlockPos.containing(n.x(), n.y() + 0.5, n.z()), n.stop().dockFirst(), n.stop().dockSecond());
    }

    private void departurePulse() {
        indicatedDeparture = true;
        if (runner == null) return;
        for (PathNode n : runner.pathway().nodes()) {
            if (!n.isStop() || n.stop().hornAux() < 0) continue;
            double s = runner.spline().nodeS(runner.pathway().indexOf(n.id()));
            if (Math.abs(runner.spline().signedDelta(s, runner.progress())) < 4.0) {
                hornAux = n.stop().hornAux();
                hornTicks = HORN_TICKS;
                return;
            }
        }
    }

    // no chunks of our own. route vehicles used to be free chunk loaders, oops
    private void holdChunks(ServerLevel level, long now, Pose pose) {
        if (!host.present()) return;
        if (sableHeld || RouteChunkTickets.holding(host.consoleId()) || PathwayStore.get(level).isRunning(host.consoleId())) {
            releaseHolds(level);
        }
        reportUnseen(level, now, pose);
        // let go of old 1.15.2 holds

        if (!legacyReleased) {
            legacyReleased = true;
            RouteReflect.forceLoad(level, host.serverSubLevel(), host.consoleId(), false);
        }
    }

    private boolean legacyReleased;

    private void reportUnseen(ServerLevel level, long now, Pose pose) {
        UUID craft = host.craftId();
        if (runner == null || pathwayId == null || craft == null || !runner.placed()) return;
        if (now % 20 == 0 || !Double.isFinite(rise)) {
            double ground = UnseenRoutes.groundTop(level, pose.x, pose.y + 1.0, pose.z);
            if (Double.isFinite(ground)) rise = pose.y - ground;
        }
        PathSpline sp = runner.spline();
        Vector3d ref = host.referenceLocal();
        Vector3d nose = host.noseLocal();

        if (now % 20 == 0 || bodyPositions.isEmpty()) {
            List<double[]> positions = new ArrayList<>();
            for (UUID id : host.ownBodies()) {
                Object body = id.equals(craft) ? host.craft() : SableBridge.findSubLevelById(level, id);
                Vector3d at = body == null ? null : SableBridge.originWorld(body);
                if (at != null) positions.add(new double[]{at.x, at.y, at.z});
            }
            bodyPositions = positions;
        }
        UnseenRoutes.report(level, new UnseenRoutes.Report(host.consoleId(), craft, pathwayId, host.vehicleName(), assignedBy,
                host.ownBodies(), runner.state().ordinal(), runner.progress(), runner.plannedSpeed(), host.measuredSpeed(),
                new double[]{ref.x, ref.y, ref.z}, new double[]{nose.x, nose.y, nose.z}, pose.x, pose.y, pose.z, rise,
                pose.y - sp.y(runner.progress()), front, rear, half, bodyPositions, driverSnapshot(), cargoSnapshot));
    }

    private CompoundTag driverSnapshot() {
        CompoundTag t = new CompoundTag();
        t.putDouble("Kappa", host.kappaPerStick(host.lockAt(0))); t.putDouble("Lock", host.lockAt(0));
        t.putBoolean("Skid", host.skidSteer()); t.putDouble("HalfTrack", host.halfTrack());
        t.putInt("MaxRpm", host.maxRpm()); t.putDouble("Height", height); return t;
    }

    private double rise = Double.NaN;
    private List<double[]> bodyPositions = List.of();

    private void releaseHolds(ServerLevel level) {
        if (sableHeld) {
            RouteReflect.forceLoad(level, host.serverSubLevel(), host.consoleId(), false);
            sableHeld = false;
        }
        if (RouteChunkTickets.holding(host.consoleId())) RouteChunkTickets.release(level, host.consoleId());
        PathwayStore.get(level).setRunning(host.consoleId(), null);
        lastHold = Long.MIN_VALUE;
    }

    private void events(ServerLevel level, long now) {
        if (runner == null) return;
        Pathway p = runner.pathway();
        String me = host.vehicleName();
        for (RouteRunner.Event e : runner.drainEvents()) {
            String at = e.nodeId() >= 0 ? " at " + p.label(e.nodeId()) : "";
            String line = switch (e.kind()) {
                case JOINED -> me + " joined " + p.name() + at;
                case ARRIVED -> me + " arrived" + at + (e.what().isEmpty() && Math.abs(e.value()) >= 0.005
                        ? String.format(java.util.Locale.ROOT, " (%.2f b %s the line)", Math.abs(e.value()), e.value() > 0 ? "past" : "short of")
                        : "");

                case DEPARTED -> me + " waited " + RouteWords.seconds((int) Math.max(0, (now - runner.waitingSince()) / 20)) + at
                        + (e.what().isEmpty() ? "" : " (" + e.what() + ")");
                case LAP -> me + " lapped " + p.name() + " in " + RouteWords.seconds((int) Math.round(e.value()));
                case BLOCKED -> me + " blocked by " + e.what() + at;
                case CLEARED -> me + " clear after " + RouteWords.seconds((int) Math.round(e.value())) + at;
                case STUCK -> me + " stuck" + at;
                case UNSTUCK -> me + " moving again" + at;
                case LOST -> me + " lost" + at + (e.what().isEmpty() ? "" : ": " + e.what());
                case REJOINED -> me + " rejoined" + at;
                case FINISHED -> me + " finished " + p.name() + at;
                case MANUAL -> me + ": a driver took the wheel";
                case PAUSED -> me + " paused" + (e.what().isEmpty() ? "" : ": " + e.what());
                case RESUMED -> me + " set off on " + p.name();
                case MISSED_STOP -> me + " missed the stop" + at;
                case CANNOT_JOIN -> me + " cannot join " + p.name() + ": " + e.what();
                case STAFF -> me + " held by a physics staff" + at;
                case HAZARD -> me + " stopped short" + at + (e.what().isEmpty() ? "" : " of " + e.what());
                case TRY -> me + " trying something else to get free (" + (int) e.value() + ")" + at;
            };
            RouteEvents.add(level, p.id(), line);
            trace.note(now, line);
            switch (e.kind()) {
                case STUCK, LOST, CANNOT_JOIN, FINISHED, HAZARD -> tellOwner(level, line, e.kind() == RouteRunner.Event.Kind.FINISHED);
                default -> {
                }
            }

            switch (e.kind()) {
                case STUCK, LOST, HAZARD, MISSED_STOP -> autoKeep(level, now, e.kind().name().toLowerCase(java.util.Locale.ROOT));
                default -> {
                }
            }
        }
    }

    public void afterDrive(ServerLevel level, long now, DriveModel.Output out, double forwardSpeed, double wheelWant,
                           double wheelSent) {
        if (runner == null) return;
        trace.begin(now);
        runner.traceInto(trace);
        Pose pose = lastPose;
        if (pose != null) {
            trace.put(RouteTrace.Ch.X, pose.x);
            trace.put(RouteTrace.Ch.Z, pose.z);
        }
        trace.put(RouteTrace.Ch.SPEED, forwardSpeed);
        double accel = Double.isNaN(lastTraceSpeed) ? 0.0 : (forwardSpeed - lastTraceSpeed) * 20.0;
        trace.put(RouteTrace.Ch.ACCEL, accel);
        lastTraceSpeed = forwardSpeed;
        if (driving && out != null) {
            trace.put(RouteTrace.Ch.OUT_THROTTLE, out.throttle());
            trace.put(RouteTrace.Ch.OUT_BRAKE, out.brake());
            trace.put(RouteTrace.Ch.OUT_STEER, out.steer());
        }
        trace.put(RouteTrace.Ch.WHEEL_WANT, wheelWant);
        trace.put(RouteTrace.Ch.WHEEL_SENT, wheelSent);
        trace.end();
        RouteRunner.State st = runner.state();
        boolean normal = st == RouteRunner.State.RUNNING || st == RouteRunner.State.APPROACHING
                || st == RouteRunner.State.DEPARTING || st == RouteRunner.State.JOINING;
        if (normal && Math.abs(accel) >= RouteTrace.JOLT_G * RouteTrace.G) {
            trace.note(now, String.format(java.util.Locale.ROOT, "jolt %.1f g at %.1f b/s", Math.abs(accel) / RouteTrace.G, forwardSpeed));
            autoKeep(level, now, "jolt");
        }
    }

    static final int AUTO_KEEP_MAX = 12;

    static final int AUTO_KEEP_TICKS = 1200;

    private void autoKeep(ServerLevel level, long now, String why) {
        if (lastAutoKeep != Long.MIN_VALUE && now - lastAutoKeep < AUTO_KEEP_TICKS) return;
        lastAutoKeep = now;
        keepTrace(level, now, "auto_" + why, null);
    }

    public String keepTrace(ServerLevel level, long now, String why, @Nullable ServerPlayer by) {
        if (trace.size() == 0) return "";
        try {
            java.nio.file.Path dir = level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .resolve("create_stats").resolve("route_traces");
            java.nio.file.Files.createDirectories(dir);
            String vehicle = host.vehicleName().replaceAll("[^A-Za-z0-9_-]+", "_");
            if (vehicle.isEmpty()) vehicle = "vehicle";
            String base = vehicle + "_" + now + "_" + why.replaceAll("[^A-Za-z0-9_-]+", "_");
            java.nio.file.Files.write(dir.resolve(base + ".trace"), trace.save());
            String table = "# " + statusLine() + "\n# " + trace.measures() + "\n# plant K " + plant.k() + " tau " + plant.tau()
                    + " tauBrake " + plant.tauBrake() + "\n" + trace.csv();
            java.nio.file.Files.writeString(dir.resolve(base + ".csv"), table);
            if (why.startsWith("auto_")) rotate(dir, vehicle + "_");
            String name = base + ".trace";
            if (by != null) by.displayClientMessage(Component.translatable("create_stats.surveyor.trace_kept", name)
                    .withStyle(ChatFormatting.GREEN), false);
            return name;
        } catch (java.io.IOException | RuntimeException e) {
            if (by != null) by.displayClientMessage(Component.translatable("create_stats.surveyor.trace_failed")
                    .withStyle(ChatFormatting.GOLD), false);
            return "";
        }
    }

    private static void rotate(java.nio.file.Path dir, String prefix) throws java.io.IOException {
        List<java.nio.file.Path> auto = new ArrayList<>();
        try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.list(dir)) {
            files.filter(f -> {
                String n = f.getFileName().toString();
                return n.startsWith(prefix) && n.contains("_auto_") && n.endsWith(".trace");
            }).forEach(auto::add);
        }
        if (auto.size() <= AUTO_KEEP_MAX) return;
        auto.sort(java.util.Comparator.comparingLong(f -> {
            try {
                return java.nio.file.Files.getLastModifiedTime(f).toMillis();
            } catch (java.io.IOException e) {
                return 0L;
            }
        }));
        for (int i = 0; i < auto.size() - AUTO_KEEP_MAX; i++) {
            java.nio.file.Path t = auto.get(i);
            java.nio.file.Files.deleteIfExists(t);
            String n = t.getFileName().toString();
            java.nio.file.Files.deleteIfExists(t.resolveSibling(n.substring(0, n.length() - ".trace".length()) + ".csv"));
        }
    }

    private void tellOwner(ServerLevel level, String line, boolean good) {
        if (assignedBy == null) return;
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(assignedBy);
        if (owner != null) owner.displayClientMessage(Component.literal(line).withStyle(good ? ChatFormatting.GREEN : ChatFormatting.GOLD), false);
    }

    private void report(ServerLevel level, Pose pose) {
        if (runner == null) return;
        RouteRunner r = runner;
        float wait = -1.0f;
        String waitText = "";
        if (r.state() == RouteRunner.State.WAITING && verdict != null) {
            double best = 0.0;
            List<String> bits = new ArrayList<>();
            for (List<StopRules.Bar> alt : verdict.bars()) {
                double f = 1.0;
                for (StopRules.Bar b : alt) {
                    f = Math.min(f, b.fraction());
                    bits.add(b.now());
                }
                best = Math.max(best, f);
            }
            wait = (float) best;
            waitText = String.join(", ", bits);
        }
        double kp = host.kappaPerStick(host.lockAt(0.0));
        RouteTrack t = new RouteTrack(host.consoleId(), pathwayId, cut(host.vehicleName(), 48), r.state().ordinal(), cut(host.present() ? r.reason() : "Travelling unseen" + (r.reason().isEmpty() ? "" : ": " + r.reason()), 96),
                pose.x, pose.y, pose.z, (float) PathSpline.heading(pose.hx, pose.hz), (float) host.measuredSpeed(),
                (float) r.progress(), r.nextStopNode(), (float) r.eta(), wait, cut(waitText, 96), r.laps(), (float) r.lastLapSeconds(),
                (float) front, (float) rear, host.consolePos().asLong(), cargo.totalItems(),
                assignedBy == null ? new UUID(0, 0) : assignedBy, kp > 1.0e-6 ? (float) (1.0 / kp) : 0f, (float) half);
        PathwayTraffic.report(level, t);
    }

    private static String cut(String s, int max) {
        return s == null ? "" : (s.length() > max ? s.substring(0, max) : s);
    }

    public void assign(ServerLevel level, Pathway p, @Nullable ServerPlayer by) {
        if (runner != null) releaseHolds(level);
        pathwayId = p.id();
        assignedBy = by == null ? assignedBy : by.getUUID();
        runner = new RouteRunner(p, plant);
        runner.setOptions(options);
        runner.scheduled(schedule != null);
        scheduleHeld = false;
        lastVersion = p.version();
        recordArmed = false;
        String cannot = host.cannotFollow();
        long now = level.getGameTime();
        runner.clock(now);
        if (!cannot.isEmpty()) {
            runner.pause(cannot);
        } else if (options.startOnAssign()) {
            runner.start(now, host.calibrated());
            checkOnStart = true;
        } else {
            runner.pause("waiting for Start");
        }
        if (host.jointSteered()) {
            RouteEvents.add(level, p.id(), host.vehicleName() + " is articulated: routes are not yet driven on joint steering, watch it closely");
        }
        RouteEvents.add(level, p.id(), host.vehicleName() + " assigned to " + p.name());
        host.routeChanged();
    }

    public void unassign(ServerLevel level, String why) {
        UUID was = pathwayId;
        releaseHolds(level);
        stopLinks.clear(level);
        dock.reset(level);
        pendingSend = -1;
        netRoute = null;
        PathwayNetworks.releaseAll(level, host.consoleId());
        heldZones.clear();
        runner = null;
        pathwayId = null;
        driving = false;
        command = RouteRunner.Command.PARK;
        PathwayTraffic.forget(level, host.consoleId());
        if (host.present()) UnseenRoutes.forget(level, host.consoleId());
        if (was != null) RouteEvents.add(level, was, host.vehicleName() + " unassigned" + (why == null || why.isEmpty() ? "" : ": " + why));
        host.routeChanged();
    }

    public void start(ServerLevel level) {
        if (runner == null) return;
        String cannot = host.cannotFollow();
        if (!cannot.isEmpty()) {
            runner.pause(cannot);
            return;
        }
        runner.start(level.getGameTime(), host.calibrated());
        checkOnStart = true;
        host.routeChanged();
    }

    public void pause(ServerLevel level, String why) {
        if (runner == null) return;
        runner.clock(level.getGameTime());
        runner.pause(why);
        host.routeChanged();
    }

    public void skipStop() {
        if (runner == null) return;
        if (schedule != null) schedule.skip();
        else runner.skipStop();
    }

    public void sendTo(int nodeId) {
        if (runner != null && schedule == null) runner.sendTo(nodeId);
    }

    public void recall() {
        if (runner != null && schedule == null) runner.recall();
    }

    @Nullable
    public ScheduleRun schedule() {
        return schedule;
    }

    public void installSchedule(ServerLevel level, RouteSchedule s, int progress, @Nullable ServerPlayer by) {
        schedule = new ScheduleRun(s, progress);
        scheduleStep = null;
        if (runner != null) {
            runner.scheduled(true);
            runner.throttle(100);
            if (scheduleHeld) {
                scheduleHeld = false;
                start(level);
            }
        }
        if (pathwayId != null) RouteEvents.add(level, pathwayId, host.vehicleName() + " took a schedule of " + s.entries().size()
                + " entries" + (s.cyclic() ? ", repeating" : ""));
        if (by != null) {
            by.displayClientMessage(Component.translatable("create_stats.surveyor.schedule_taken", host.vehicleName(), s.entries().size())
                    .withStyle(ChatFormatting.GREEN), false);
            for (int i = 0; i < s.entries().size(); i++) {
                RouteSchedule.Entry e = s.entries().get(i);
                if (e.instruction() instanceof RouteSchedule.Unsupported u) {
                    by.displayClientMessage(Component.translatable("create_stats.surveyor.schedule_skips", i + 1, u.id(), u.why())
                            .withStyle(ChatFormatting.GOLD), false);
                }
                for (String d : e.declined()) {
                    by.displayClientMessage(Component.translatable("create_stats.surveyor.schedule_declines", i + 1, d)
                            .withStyle(ChatFormatting.GOLD), false);
                }
            }
            if (runner == null) {
                by.displayClientMessage(Component.translatable("create_stats.surveyor.schedule_no_route").withStyle(ChatFormatting.GOLD), false);
            }
        }
        host.routeChanged();
    }

    public void removeSchedule(ServerLevel level) {
        if (schedule == null) return;
        schedule = null;
        scheduleStep = null;
        if (runner != null) {
            runner.scheduled(false);
            runner.throttle(100);
            if (scheduleHeld) {
                scheduleHeld = false;
                start(level);
            }
        }
        host.routeChanged();
    }

    private void tickSchedule(ServerLevel level, Pathway p, PathSpline sp, long now) {
        if (schedule == null || runner == null) return;
        runner.scheduled(true);
        RouteRunner.State st = runner.state();
        boolean playerPaused = st == RouteRunner.State.PAUSED && !scheduleHeld;
        if (st == RouteRunner.State.MANUAL || playerPaused || st == RouteRunner.State.CALIBRATING) return;
        ScheduleRun.Host h = new ScheduleRun.Host() {
            @Override
            public int pick(String glob) {
                return pickStation(level, p, sp, glob);
            }

            @Override
            public int waitingAt() {
                if (runner.state() != RouteRunner.State.WAITING) return -1;
                if (netRoute != null && netLeg == netRoute.legs().size() - 1 && netRoute.station().equals(pathwayId)
                        && runner.waitingAt() == netRoute.stationNode()) return netKey;
                return runner.waitingAt();
            }

            @Override
            public String name(int node) {
                if (netRoute != null && node == netKey) {
                    Pathway sp2 = PathwayStore.get(level).get(netRoute.station());
                    return sp2 == null ? "?" : sp2.label(netRoute.stationNode());
                }
                PathNode n = p.node(node);
                return n == null ? "?" : p.label(node);
            }
        };
        ScheduleRun.Step step = schedule.tick(now, h, host.present() ? cargo : host.frozenCargo());
        scheduleStep = step;
        runner.throttle(schedule.throttle());
        if (step.sendTo() >= 0) {
            if (host.present() && dock.holding(level)) pendingSend = step.sendTo();
            else send(step.sendTo());
        }
        if (step.pause()) {
            runner.clock(now);
            runner.pause(step.status());
            scheduleHeld = true;
            if (schedule.completed()) RouteEvents.add(level, p.id(), host.vehicleName() + ": schedule completed");
            host.routeChanged();
        } else if (step.resume() && scheduleHeld) {
            scheduleHeld = false;
            start(level);
        }
    }

    private int pickStation(ServerLevel level, Pathway p, PathSpline sp, String glob) {
        java.util.regex.Pattern pattern;
        try {
            pattern = java.util.regex.Pattern.compile(net.createmod.catnip.data.Glob.toRegexPattern(glob, ""));
        } catch (RuntimeException e) {
            return ScheduleRun.NO_MATCH;
        }
        PathNetwork net = PathwayNetworks.of(level);
        double from = runner.progress();
        if (runner.state() == RouteRunner.State.WAITING) {
            int k = p.indexOf(runner.waitingAt());
            if (k >= 0 && k < sp.nodeCount()) from = sp.nodeS(k);
        }
        List<RouteTrack> tracks = new ArrayList<>(PathwayTraffic.all(level));
        UUID me = host.consoleId();
        java.util.function.BiFunction<UUID, Integer, Boolean> occupied = (line, node) -> {
            for (RouteTrack t : tracks) {
                if (!t.console().equals(me) && t.pathway().equals(line) && t.state() == RouteRunner.State.WAITING.ordinal()
                        && t.nextStop() == node) return true;
            }
            return false;
        };
        PathNetwork.Route r = net.route(p.id(), from, name -> pattern.matcher(name).matches(), occupied);
        if (r == null) {
            for (Pathway q : net.lines()) {
                for (PathNode n : q.nodes()) if (!n.name().isEmpty() && pattern.matcher(n.name()).matches()) return ScheduleRun.NO_ROUTE;
            }
            return ScheduleRun.NO_MATCH;
        }
        if (r.legs().size() == 1) {
            netRoute = null;
            return r.stationNode();
        }
        netRoute = r;
        netLeg = 0;
        netKey = NETWORK_KEYS + (netCounter++ % 100_000);
        return netKey;
    }

    private void send(int target) {
        if (runner == null) return;
        if (netRoute != null && target == netKey) {
            netLeg = 0;
            runner.sendTo(netRoute.legs().size() == 1 ? netRoute.stationNode() : -1);
        } else {
            netRoute = null;
            runner.sendTo(target);
        }
    }

    private void tickNetwork(ServerLevel level) {
        turnSignal = 0;
        if (runner == null) return;
        if (netRoute == null) {
            runner.capAt(Double.NaN, 0.0);
            return;
        }
        List<PathNetwork.Leg> legs = netRoute.legs();
        if (netLeg >= legs.size() - 1) {
            runner.capAt(Double.NaN, 0.0);
            return;
        }
        if (!legs.get(netLeg).pathway().equals(pathwayId)) {
            netRoute = null;
            runner.capAt(Double.NaN, 0.0);
            return;
        }
        PathNetwork.Transfer t = netRoute.transfers().get(netLeg);
        PathSpline sp = runner.spline();
        double d;
        if (sp.closed()) {
            double ahead = sp.ahead(runner.progress(), t.fromS());
            d = ahead > sp.length() - 3.0 ? ahead - sp.length() : ahead;
        } else {
            d = t.fromS() - runner.progress();
        }
        runner.capAt(t.fromS(), junctionSpeed(t.turn()));
        double v = Math.max(0.0, host.measuredSpeed());
        if (Math.abs(t.turn()) > 10.0 && d < Math.max(8.0, v * 3.0 + 4.0)) turnSignal = t.turn() > 0.0 ? 1 : -1;

        double kp = host.kappaPerStick(host.lockAt(0.0));
        double own = kp > 1.0e-6 ? 1.0 / kp : 6.0;
        double lead = t.leadIn(own * 1.1) + v * 0.25;
        if (d <= Math.max(0.3, lead) && d > -3.0) {
            Pathway np = PathwayStore.get(level).get(t.to());
            if (np == null) {
                netRoute = null;
                return;
            }
            pathwayId = np.id();
            runner.handOver(np);
            lastVersion = np.version();
            terrainVersion = -1;
            netLeg++;
            runner.capAt(Double.NaN, 0.0);
            RouteEvents.add(level, np.id(), host.vehicleName() + " took the junction onto " + np.name());
            if (netLeg == legs.size() - 1) runner.sendTo(netRoute.stationNode());
        }
    }

    private double junctionSpeed(double turnDeg) {
        double turn = Math.toRadians(Math.abs(turnDeg));
        if (turn < 0.05) return 40.0;
        double radius = 4.0 / turn;
        com.createstats.surveyor.core.Comfort c = options.comfort(runner.pathway());
        return Math.sqrt(c.lateral * radius);
    }

    private double zoneGap(ServerLevel level, PathSpline sp, long now) {
        zoneWait = "";
        List<PathNetwork.Zone> keep = new ArrayList<>();
        double gap = Double.POSITIVE_INFINITY;
        if (runner != null && runner.placed() && runner.state().drives()) {
            PathNetwork net = PathwayNetworks.of(level);
            double at = runner.progress();
            double v = Math.max(0.0, host.measuredSpeed());
            double reach = v * v / 2.0 + v * 2.0 + 8.0 + PathNetwork.ZONE_RADIUS;
            for (PathNetwork.Zone z : net.zones()) {
                if (z.at().size() < 2) continue;
                double[] on = z.on(pathwayId, sp);
                if (on == null) continue;
                double entry = sp.closed() ? sp.signedDelta(at, on[0]) : on[0] - at;
                double exit = sp.closed() ? sp.signedDelta(at, on[1]) : on[1] - at;
                boolean inside = entry <= 0.0 && exit + rear >= 0.0;
                boolean near = entry > 0.0 && entry < reach;
                if (!inside && !near) continue;
                String holder = PathwayNetworks.reserve(level, z, host.consoleId(), host.vehicleName(), now);
                if (holder == null) {
                    keep.add(z);
                } else if (!inside && entry - 0.5 < gap) {
                    gap = entry - 0.5;
                    zoneWait = "waiting for " + holder + " to clear the junction";
                }
            }
        }
        for (PathNetwork.Zone z : heldZones) {
            boolean still = false;
            for (PathNetwork.Zone k : keep) still |= Math.abs(k.x() - z.x()) < 0.5 && Math.abs(k.z() - z.z()) < 0.5;
            if (!still) PathwayNetworks.release(level, z, host.consoleId());
        }
        heldZones.clear();
        heldZones.addAll(keep);
        return gap;
    }

    private int dockStop(Pathway p) {
        if (runner == null) return -1;
        int node = runner.state() == RouteRunner.State.WAITING ? runner.waitingAt() : runner.nextStopNode();
        if (node < 0) return -1;
        if (schedule != null) {
            List<RouteSchedule.Entry> entries = schedule.schedule().entries();
            int target = schedule.target();

            boolean there = target == node || (netRoute != null && target == netKey && netRoute.station().equals(pathwayId)
                    && netRoute.stationNode() == node);
            if (!there || entries.isEmpty()) return -1;
            RouteSchedule.Entry e = entries.get(Math.min(schedule.entry(), entries.size() - 1));
            return DockApproach.waitsForDock(e.conditions()) ? node : -1;
        }
        PathNode n = p.node(node);
        return n != null && n.stop() != null && DockApproach.waitsForDock(n.stop().rules()) ? node : -1;
    }

    public String dockStatus() {
        return dock.status();
    }

    private int seatedPlayers(ServerLevel level) {
        List<int[]> boxes = new ArrayList<>();
        int[] own = RouteReflect.plotBounds(host.craft());
        if (own != null) boxes.add(own);
        for (UUID id : host.ownBodies()) {
            Object body = SableBridge.findSubLevelById(level, id);
            int[] b = body == null ? null : RouteReflect.plotBounds(body);
            if (b != null && body != host.craft()) boxes.add(b);
        }
        int n = 0;
        for (ServerPlayer pl : level.players()) {
            net.minecraft.world.entity.Entity seat = pl.getVehicle();
            if (seat == null) continue;
            double x = seat.getX();
            double y = seat.getY();
            double z = seat.getZ();
            for (int[] b : boxes) {
                if (x >= b[0] - 1 && x <= b[3] + 2 && y >= b[1] - 1 && y <= b[4] + 2 && z >= b[2] - 1 && z <= b[5] + 2) {
                    n++;
                    break;
                }
            }
        }
        return n;
    }

    private static boolean stopPowered(ServerLevel level, PathNode n) {
        net.minecraft.core.BlockPos at = net.minecraft.core.BlockPos.containing(n.x(), n.y() + 0.01, n.z());
        return RouteSensors.stopPowered(level, at);
    }

    public void setOptions(RouteRunner.Options o) {
        options = o == null ? RouteRunner.Options.DEFAULT : o;
        if (runner != null) runner.setOptions(options);
        host.routeChanged();
    }

    public void armRecord(ServerLevel level, ServerPlayer by) {
        if (runner != null && runner.state().drives()) runner.pause("recording a new route");
        recordArmed = true;
        recordFor = by.getUUID();
        nearStartSaid = false;
        by.displayClientMessage(Component.translatable("create_stats.surveyor.record_armed").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        host.routeChanged();
    }

    private void recordTick(ServerLevel level, long now) {
        Pose pose = pose();
        if (pose == null) return;
        double v = host.measuredSpeed();
        if (recording == null) {
            if (!recordArmed || !host.seatedDriver() || Math.abs(v) < 0.5) return;
            recording = new RecordTrack();
            recordArmed = false;
            tell(level, Component.translatable("create_stats.surveyor.record_started").withStyle(ChatFormatting.LIGHT_PURPLE));

            level.playSound(null, pose.x, pose.y, pose.z, ModSounds.SURVEYOR_RECORD_START.get(), SoundSource.BLOCKS, 0.8f, 1.0f);
            host.routeChanged();
        }
        recording.sample(pose.x, pose.y, pose.z, pose.hx, pose.hz, v);
        if (!nearStartSaid && recording.nearStart(pose.x, pose.z, pose.hx, pose.hz)) {
            nearStartSaid = true;
            tell(level, Component.translatable("create_stats.surveyor.record_near_start").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        if (recording.full()) stopRecord(level, false);
    }

    public void stopRecord(ServerLevel level, boolean closeIfNearStart) {
        RecordTrack rec = recording;
        recording = null;
        recordArmed = false;
        host.routeChanged();
        if (rec == null || rec.size() < 4) {
            tell(level, Component.translatable("create_stats.surveyor.record_too_short").withStyle(ChatFormatting.GOLD));
            return;
        }
        Pose pose = pose();
        boolean loop = closeIfNearStart && pose != null && rec.nearStart(pose.x, pose.z, pose.hx, pose.hz);
        List<PathNode> nodes = rec.finish(loop);
        PathwayStore store = PathwayStore.get(level);
        UUID owner = recordFor != null ? recordFor : (assignedBy != null ? assignedBy : new UUID(0, 0));
        String name = host.vehicleName() + " recorded";
        int n = 2;
        while (nameTaken(store, name)) name = host.vehicleName() + " recorded " + (n++);
        Pathway p = Pathway.create(UUID.randomUUID(), name.length() > Pathway.MAX_NAME ? name.substring(0, Pathway.MAX_NAME) : name,
                store.nextColour(owner), owner).appendNodes(nodes).withCruise(rec.cruise());
        if (loop && p.nodes().size() >= 3) p = p.withKind(PathKind.LOOP);
        store.put(p);
        PathwaySync.changed(level, p);
        RouteEvents.add(level, p.id(), host.vehicleName() + " recorded " + p.name() + ": " + p.nodes().size() + " nodes, "
                + p.stopCount() + " stops" + (loop ? ", a loop" : ""));
        tell(level, Component.translatable("create_stats.surveyor.record_saved", p.name(), p.nodes().size()).withStyle(ChatFormatting.GREEN));
        if (pose != null) {
            level.playSound(null, pose.x, pose.y, pose.z, ModSounds.SURVEYOR_RECORD_STOP.get(), SoundSource.BLOCKS, 0.8f, 1.0f);
        }
    }

    public void cancelRecord(ServerLevel level) {
        recording = null;
        recordArmed = false;
        host.routeChanged();
    }

    private static boolean nameTaken(PathwayStore store, String name) {
        for (Pathway p : store.all()) if (p.name().equalsIgnoreCase(name)) return true;
        return false;
    }

    private void tell(ServerLevel level, Component c) {
        UUID who = recordFor != null ? recordFor : assignedBy;
        ServerPlayer p = who == null ? null : level.getServer().getPlayerList().getPlayer(who);
        if (p != null) p.displayClientMessage(c, true);
    }

    public void removed(ServerLevel level) {
        releaseHolds(level);
        stopLinks.clear(level);
        dock.reset(level);
        PathwayNetworks.releaseAll(level, host.consoleId());
        PathwayTraffic.forget(level, host.consoleId());
        if (host.present()) UnseenRoutes.forget(level, host.consoleId());
        RouteHosts.forget(host.consoleId(), this);
    }

    public void unloaded(ServerLevel level) {
        stopLinks.clear(level);
        dock.reset(level);
        PathwayNetworks.releaseAll(level, host.consoleId());
        PathwayTraffic.forget(level, host.consoleId());
        RouteHosts.forget(host.consoleId(), this);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", RouteNbt.VERSION);
        if (pathwayId != null) tag.putUUID("Pathway", pathwayId);
        if (assignedBy != null) tag.putUUID("AssignedBy", assignedBy);
        if (runner != null) {
            tag.put("Runner", RouteNbt.writeSaved(runner.save()));
            tag.put("Runtime", TravelNbt.runtime(runner.travel()));
            tag.putInt("NextStopNode", runner.nextStopNode());
        } else if (pendingRestore != null) {
            tag.put("Runner", RouteNbt.writeSaved(pendingRestore));
            if (pendingRuntime != null) tag.put("Runtime", TravelNbt.runtime(pendingRuntime));
            if (pendingTargetNode != Integer.MIN_VALUE) tag.putInt("NextStopNode", pendingTargetNode);
        }
        if (travelLoad) tag.putBoolean("ReturnResume", true);
        if (stopProgress != null) {
            tag.put("StopProgress", TravelNbt.progress(stopProgress.save())); tag.putInt("StopNode", stopProgressNode);
        } else if (pendingStopProgress != null) {
            tag.put("StopProgress", pendingStopProgress.copy()); tag.putInt("StopNode", stopProgressNode);
        }
        tag.putBoolean("IndicatedDeparture", indicatedDeparture);
        tag.putInt("PendingSend", pendingSend);
        if (netRoute != null) {
            tag.put("NetworkRoute", TravelNbt.network(netRoute)); tag.putInt("NetworkLeg", netLeg);
            tag.putInt("NetworkKey", netKey); tag.putInt("NetworkCounter", netCounter);
        }
        tag.put("Plant", RouteNbt.writePlant(plant));
        tag.put("Options", RouteNbt.writeOptions(options));
        if (recordArmed) tag.putBoolean("RecordArmed", true);
        if (recordFor != null) tag.putUUID("RecordFor", recordFor);

        if (runner != null && trace.size() > 0) tag.putByteArray("Trace", trace.save());
        if (schedule != null) {
            tag.putByteArray("Schedule", com.createstats.surveyor.core.PathwayCodec.encodeSchedule(schedule.schedule()));
            tag.putInt("ScheduleEntry", schedule.entry());
            tag.put("ScheduleRun", TravelNbt.schedule(schedule.save()));
            if (scheduleHeld) tag.putBoolean("ScheduleHeld", true);
        }
        return tag;
    }

    public void load(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return;
        plant = RouteNbt.readPlant(tag.getCompound("Plant"), plant);
        options = RouteNbt.readOptions(tag.getCompound("Options"));
        assignedBy = tag.hasUUID("AssignedBy") ? tag.getUUID("AssignedBy") : null;
        travelLoad = tag.getBoolean("ReturnResume");
        pendingRuntime = tag.contains("Runtime") ? TravelNbt.runtime(tag.getCompound("Runtime")) : null;
        pendingTargetNode = tag.contains("NextStopNode") ? tag.getInt("NextStopNode") : Integer.MIN_VALUE;
        pendingStopProgress = tag.contains("StopProgress") ? tag.getCompound("StopProgress").copy() : null;
        stopProgressNode = tag.getInt("StopNode");
        indicatedDeparture = tag.getBoolean("IndicatedDeparture");
        pendingSend = tag.contains("PendingSend") ? tag.getInt("PendingSend") : -1;
        netRoute = tag.contains("NetworkRoute") ? TravelNbt.network(tag.getCompound("NetworkRoute")) : null;
        netLeg = tag.getInt("NetworkLeg"); netKey = tag.contains("NetworkKey") ? tag.getInt("NetworkKey") : -1;
        netCounter = tag.getInt("NetworkCounter");
        recordArmed = tag.getBoolean("RecordArmed");
        recordFor = tag.hasUUID("RecordFor") ? tag.getUUID("RecordFor") : null;
        if (tag.contains("Trace")) trace = RouteTrace.load(tag.getByteArray("Trace"));
        if (tag.contains("Schedule")) {
            try {
                RouteSchedule sch = com.createstats.surveyor.core.PathwayCodec.decodeSchedule(tag.getByteArray("Schedule"));
                schedule = new ScheduleRun(sch, tag.getInt("ScheduleEntry"));
                if (tag.contains("ScheduleRun")) schedule.restore(TravelNbt.schedule(tag.getCompound("ScheduleRun")));
                scheduleHeld = tag.getBoolean("ScheduleHeld");
            } catch (java.io.IOException | RuntimeException e) {
                schedule = null;
            }
        }
        if (tag.hasUUID("Pathway")) {
            pathwayId = tag.getUUID("Pathway");
            pendingRestore = tag.contains("Runner") ? RouteNbt.readSaved(tag.getCompound("Runner")) : null;
            pendingLoad = true;
        }
    }

    private boolean pendingLoad;
    private CompoundTag pendingStopProgress;

    public void loadTravel(CompoundTag tag) { load(tag); travelLoad = true; }

    public void resume(ServerLevel level) {
        if (!pendingLoad || pathwayId == null) return;
        pendingLoad = false;
        Pathway p = PathwayStore.get(level).get(pathwayId);
        if (p == null) {
            pathwayId = null;
            pendingRestore = null;
            return;
        }
        runner = new RouteRunner(p, plant);
        runner.setOptions(options);
        runner.scheduled(schedule != null);
        lastVersion = p.version();
        if (pendingRestore != null) {
            if (travelLoad || !host.present()) {
                runner.restoreTravel(pendingRestore, pendingRuntime, level.getGameTime());
                if (pendingTargetNode != Integer.MIN_VALUE) runner.restoreTarget(pendingTargetNode);
            } else runner.restore(pendingRestore, level.getGameTime());
            pendingRestore = null;
        }
        if (pendingStopProgress != null && runner.pathway().node(stopProgressNode) != null) {
            Stop stop = runner.pathway().node(stopProgressNode).stop();
            if (stop != null) stopProgress = StopRules.Progress.restore(stop, TravelNbt.progress(pendingStopProgress));
        }
        pendingStopProgress = null;

        if (host.present() && runner.state().drives()) startup = new com.createstats.surveyor.core.ReturnStartup();
    }

    public record View(boolean assigned, UUID pathway, String pathwayName, int colour, int state, String reason, double progress,
                       double length, int nextStop, String nextStopName, double eta, String rules, float waitFraction, String waitText,
                       int laps, double lastLap, boolean recording, boolean recordArmed, int recordedPoints, boolean learned,
                       double cruiseOverride, int comfort, boolean startOnAssign, boolean horn, List<double[]> stops,
                       boolean closed, double plannedSpeed, double pathwayCruise, boolean hasSchedule, String schedule) {
    }

    public View view(ServerLevel level) {
        List<double[]> stops = new ArrayList<>();
        if (runner == null) {
            return new View(false, new UUID(0, 0), "", 0, state().ordinal(), "", 0, 0, -1, "", -1, "", -1f, "",
                    0, -1, recording != null, recordArmed, recording == null ? 0 : recording.size(), plant.learned(),
                    options.cruiseOverride(), options.comfortOverride() == null ? -1 : options.comfortOverride().ordinal(),
                    options.startOnAssign(), options.hornWhenBlocked(), stops, false, 0, Pathway.DEFAULT_CRUISE, schedule != null,
                    scheduleLine());
        }
        Pathway p = runner.pathway();
        PathSpline sp = runner.spline();
        for (int k = 0; k < p.nodes().size() && k < sp.nodeCount(); k++) {
            if (p.nodes().get(k).isStop()) stops.add(new double[]{sp.nodeS(k), p.nodes().get(k).id()});
        }
        int next = runner.nextStopNode();
        PathNode nn = next < 0 ? null : p.node(next);
        String rules = nn == null || nn.stop() == null ? "" : RouteWords.rules(nn.stop());
        float wait = -1f;
        String waitText = "";
        if (verdict != null) {
            List<String> bits = new ArrayList<>();
            double best = 0.0;
            for (List<StopRules.Bar> alt : verdict.bars()) {
                double f = 1.0;
                for (StopRules.Bar b : alt) {
                    f = Math.min(f, b.fraction());
                    bits.add(b.now());
                }
                best = Math.max(best, f);
            }
            wait = (float) best;
            waitText = String.join(", ", bits);
        }
        return new View(true, p.id(), p.name(), p.colour(), state().ordinal(), saying(), runner.progress(), sp.length(), next,
                nn == null ? "" : p.label(nn.id()), runner.eta(), rules, wait, waitText, runner.laps(), runner.lastLapSeconds(),
                recording != null, recordArmed, recording == null ? 0 : recording.size(), plant.learned(), options.cruiseOverride(),
                options.comfortOverride() == null ? -1 : options.comfortOverride().ordinal(), options.startOnAssign(),
                options.hornWhenBlocked(), stops, sp.closed(), runner.plannedSpeed(), p.cruise(), schedule != null, scheduleLine());
    }

    private String scheduleLine() {
        if (schedule == null) return "";
        int n = schedule.schedule().entries().size();
        String title = schedule.title().isEmpty() ? "Schedule" : schedule.title();
        String at = n == 0 ? "" : ", " + (Math.min(schedule.entry(), n - 1) + 1) + " of " + n;
        String doing = schedule.status().isEmpty() ? "" : ": " + schedule.status();
        return title + at + doing;
    }

    public static RouteRunner.Options withComfort(RouteRunner.Options o, @Nullable Comfort c) {
        return new RouteRunner.Options(o.cruiseOverride(), c, o.startOnAssign(), o.hornWhenBlocked());
    }
}
