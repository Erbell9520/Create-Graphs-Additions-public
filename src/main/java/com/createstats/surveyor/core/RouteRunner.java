package com.createstats.surveyor.core;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// keys + comfort ramps made a surge every 3.5s. continuous actuators now
public final class RouteRunner {
    public enum State {
        CALIBRATING("calibrating"),
        JOINING("joining"),
        RUNNING("running"),
        APPROACHING("approaching"),
        WAITING("waiting"),
        DEPARTING("departing"),
        BLOCKED("blocked"),
        STUCK("stuck"),
        LOST("lost"),
        PAUSED("paused"),
        MANUAL("manual"),
        RECORDING("recording"),
        FINISHED("finished"),
        NO_BUDGET("no_budget"),

        HAZARD("hazard");

        public final String key;

        State(String key) {
            this.key = key;
        }

        public boolean drives() {
            return switch (this) {
                case CALIBRATING, JOINING, RUNNING, APPROACHING, WAITING, DEPARTING, BLOCKED, STUCK, LOST, HAZARD -> true;
                default -> false;
            };
        }

        public boolean holdsChunks() {
            return switch (this) {
                case CALIBRATING, JOINING, RUNNING, APPROACHING, WAITING, DEPARTING, BLOCKED, STUCK, HAZARD -> true;
                default -> false;
            };
        }

        public static State byOrdinal(int i) {
            State[] v = values();
            return i >= 0 && i < v.length ? v[i] : PAUSED;
        }
    }

    public enum Mode {
        NONE("none"),
        CRUISE("cruise"),

        EASE("ease"),

        BUILD("build"),
        FOLLOW("follow"),
        APPROACH("approach"),
        STOPPING("stopping"),
        HOLD("hold"),
        REJOIN("rejoin"),
        HAZARD("hazard"),
        BLOCKED("blocked"),
        UNSTICK("unstick"),
        REVERSE("reverse"),
        LEAVE("leave");

        public final String key;

        Mode(String key) {
            this.key = key;
        }

        public static Mode byOrdinal(int i) {
            Mode[] v = values();
            return i >= 0 && i < v.length ? v[i] : NONE;
        }
    }

    public static final double SPEED_LEAD = 0.15;

    public static final double DECEL_MARGIN = 1.2;
    public static final double ACCEL_MARGIN = 1.1;

    public static final double TOP_SHARE = 0.92;

    public static final double FF_WINDOW = 0.25;

    public static final double LEAN_WARN = 18.0;
    public static final double LEAN_STOP = 28.0;

    public static final double JOIN_REACH = 32.0;
    public static final double JOIN_DY = 4.0;
    public static final double JOIN_SPEED = 5.0;

    public static final double JOINED_LATERAL = 1.0;
    public static final double JOINED_DEGREES = 20.0;

    public static final double REJOIN_LATERAL = 2.5;
    public static final double REJOIN_DEGREES = 35.0;

    public static final double LOST_HARD = 16.0;
    public static final double LOST_DEGREES = 120.0;

    public static final double ARRIVE_DISTANCE = 0.3;
    public static final double ARRIVE_SPEED = 0.12;

    public static final double DEPART_DISTANCE = 3.0;

    public static final double STANDOFF = 3.0;
    public static final double OBSTACLE_MARGIN = 1.5;
    public static final double HAZARD_MARGIN = 1.0;

    public static final double STUCK_SPEED = 0.15;
    public static final int STUCK_TICKS = 40;

    public static final int TRY_TICKS = 60;
    public static final int TRIES = 3;

    public static final double REVERSE_BLOCKS = 1.5;
    public static final double REVERSE_SPEED = 1.0;

    public static final int CLEAR_TICKS = 20;

    public static final int TOOT_AFTER = 20;
    public static final int TOOT_EVERY = 600;

    public static final int STAFF_SETTLE = 40;

    public static final int JOIN_GIVE_UP_TICKS = 1200;

    public record Sensors(long tick, double x, double y, double z, double hx, double hz, double speed, double kappaPerStick,
                          boolean skid, double obstacle, boolean obstaclePlayer, String obstacleWhat, double gapAhead,
                          double speedAhead, boolean departOk, boolean staffHeld, boolean driverKeys, boolean calibrated,
                          double appliedThrottle, boolean appliedBrake, double appliedSteer, double yawRate, double pitch,
                          double roll, double appliedBrakeLevel, double wheelDrive, boolean obstacleBlock, double hazard,
                          String hazardWhat, double unknown) {
        public Sensors(long tick, double x, double y, double z, double hx, double hz, double speed, double kappaPerStick,
                       boolean skid, double obstacle, boolean obstaclePlayer, String obstacleWhat, double gapAhead,
                       double speedAhead, boolean departOk, boolean staffHeld, boolean driverKeys, boolean calibrated,
                       double appliedThrottle, boolean appliedBrake, double appliedSteer, double yawRate, double pitch,
                       double roll, double appliedBrakeLevel, double wheelDrive, boolean obstacleBlock, double hazard,
                       String hazardWhat) {
            this(tick, x, y, z, hx, hz, speed, kappaPerStick, skid, obstacle, obstaclePlayer, obstacleWhat, gapAhead,
                    speedAhead, departOk, staffHeld, driverKeys, calibrated, appliedThrottle, appliedBrake, appliedSteer,
                    yawRate, pitch, roll, appliedBrakeLevel, wheelDrive, obstacleBlock, hazard, hazardWhat,
                    Double.POSITIVE_INFINITY);
        }

        public Sensors(long tick, double x, double y, double z, double hx, double hz, double speed, double kappaPerStick,
                       boolean skid, double obstacle, boolean obstaclePlayer, String obstacleWhat, double gapAhead,
                       double speedAhead, boolean departOk, boolean staffHeld, boolean driverKeys, boolean calibrated,
                       double appliedThrottle, boolean appliedBrake, double appliedSteer, double yawRate) {
            this(tick, x, y, z, hx, hz, speed, kappaPerStick, skid, obstacle, obstaclePlayer, obstacleWhat, gapAhead,
                    speedAhead, departOk, staffHeld, driverKeys, calibrated, appliedThrottle, appliedBrake, appliedSteer,
                    yawRate, 0.0, 0.0, appliedBrake ? 1.0 : 0.0, Double.NaN, false, Double.POSITIVE_INFINITY, "",
                    Double.POSITIVE_INFINITY);
        }

        public double grade() {
            return Double.isFinite(pitch) ? -RouteTrace.G * Math.sin(Math.toRadians(pitch)) : 0.0;
        }
    }

    public record Command(boolean drive, double throttle, double steer, double brakeLevel, boolean horn, int indicate) {
        public static final Command PARK = new Command(false, 0.0, 0.0, 1.0, false, 0);
        public static final Command HOLD = new Command(true, 0.0, 0.0, 1.0, false, 0);

        public Command(boolean drive, double throttle, double steer, boolean brake, boolean horn, int indicate) {
            this(drive, throttle, steer, brake ? 1.0 : 0.0, horn, indicate);
        }

        public boolean brake() {
            return brakeLevel > 0.0;
        }
    }

    public record Event(Kind kind, int nodeId, double value, String what) {
        public enum Kind {
            JOINED, ARRIVED, DEPARTED, LAP, BLOCKED, CLEARED, STUCK, UNSTUCK, LOST, REJOINED, FINISHED, MANUAL, PAUSED,
            RESUMED, MISSED_STOP, CANNOT_JOIN, STAFF, HAZARD, TRY
        }
    }

    public record Options(double cruiseOverride, @Nullable Comfort comfortOverride, boolean startOnAssign, boolean hornWhenBlocked) {
        public static final Options DEFAULT = new Options(0.0, null, true, true);

        public double cruise(Pathway p) {
            return cruiseOverride > 0.0 ? Math.max(Pathway.MIN_CRUISE, Math.min(PathNode.MAX_SPEED, cruiseOverride)) : p.cruise();
        }

        public Comfort comfort(Pathway p) {
            return comfortOverride != null ? comfortOverride : p.comfort();
        }
    }

    private Pathway pathway;
    private PathSpline sp = PathSpline.EMPTY;
    private SpeedPlan plan = SpeedPlan.EMPTY;

    private double[] stopS = new double[0];
    private int[] stopNode = new int[0];
    private Options options = Options.DEFAULT;
    private final PlantModel plant;

    private State state = State.PAUSED;
    private String reason = "";
    private long stateSince;
    private double s;
    private boolean placed;
    private double lateral;
    private double headingError;

    private int nextStop = -1;

    private int sendTo = -1;

    private int waitingAt = -1;
    private long waitingSince;
    private double departedFrom;

    private double lapTravel;
    private long lapStart = -1;
    private double lastLap = -1.0;
    private int laps;

    private State resumeTo = State.RUNNING;
    private int clearTicks;
    private int blockedTicks;
    private int stuckTicks;
    private int tries;
    private int tryTicks;
    private double tryFrom;
    private int joinedTicks;
    private int staffTicks = -1;
    private boolean rejoining;
    private boolean recallToStart;
    private boolean stopping;

    private final LongitudinalControl foot = new LongitudinalControl();
    private final HeadingTracker yaw = new HeadingTracker();
    private double lastSpeed = Double.NaN;

    private double lastToStop = Double.POSITIVE_INFINITY;
    private Mode mode = Mode.NONE;
    private String subject = "";

    private double lastGoal = Double.NaN;
    private LongitudinalControl.Out lastFoot = LongitudinalControl.Out.HOLD;
    private double lastKappa = Double.NaN;
    private double lastLineKappa = Double.NaN;
    private Command lastCommand = Command.PARK;
    private Sensors lastSensors;

    private double tryOffset;

    private double prevGoal = Double.NaN;
    private LongitudinalControl.Out prevFoot = LongitudinalControl.Out.HOLD;

    private final List<Event> events = new ArrayList<>();

    public RouteRunner(Pathway pathway, PlantModel plant) {
        this.plant = plant;
        setPathway(pathway);
    }

    public Pathway pathway() {
        return pathway;
    }

    public PathSpline spline() {
        return sp;
    }

    public SpeedPlan plan() {
        return plan;
    }

    public PlantModel plant() {
        return plant;
    }

    public Options options() {
        return options;
    }

    public void setOptions(Options o) {
        options = o == null ? Options.DEFAULT : o;
        rebuildPlan();
    }

    public void setPathway(Pathway p) {
        boolean first = pathway == null;
        double oldX = placed ? sp.x(s) : Double.NaN;
        double oldY = placed ? sp.y(s) : Double.NaN;
        double oldZ = placed ? sp.z(s) : Double.NaN;
        pathway = p;
        sp = PathSpline.of(p);
        rebuildPlan();
        List<double[]> stops = new ArrayList<>();
        for (int k = 0; k < p.nodes().size() && k < sp.nodeCount(); k++) {
            if (p.nodes().get(k).isStop()) stops.add(new double[]{sp.nodeS(k), p.nodes().get(k).id()});
        }
        stops.sort((a, b) -> Double.compare(a[0], b[0]));
        stopS = new double[stops.size()];
        stopNode = new int[stops.size()];
        for (int i = 0; i < stops.size(); i++) {
            stopS[i] = stops.get(i)[0];
            stopNode[i] = (int) stops.get(i)[1];
        }
        if (first || !placed || !Double.isFinite(oldX)) return;
        PathProjection.Result r = PathProjection.global(sp, oldX, oldY, oldZ, 0.0, 0.0, 6.0, JOIN_DY, 180.0);
        if (r.found()) {
            s = r.s();
            pickNextStop();
        } else if (state.drives() && state != State.CALIBRATING) {
            placed = false;
            setState(State.JOINING, "the line moved");
        }
        if (waitingAt >= 0 && p.node(waitingAt) == null) {
            waitingAt = -1;
            if (state == State.WAITING) setState(State.DEPARTING, "");
        }
    }

    private void rebuildPlan() {
        if (pathway == null) return;
        plan = SpeedPlan.build(sp, pathway, options.cruise(pathway) * throttleShare, options.comfort(pathway), terrain);
    }

    private double throttleShare = 1.0;

    public void throttle(int percent) {
        double share = Math.max(0.05, Math.min(1.0, percent / 100.0));
        if (Math.abs(share - throttleShare) < 1.0e-9) return;
        throttleShare = share;
        rebuildPlan();
    }

    @Nullable
    private SpeedPlan.Terrain terrain;

    public void terrain(@Nullable SpeedPlan.Terrain t) {
        terrain = t;
        rebuildPlan();
    }

    public void start(long tick, boolean calibrated) {
        clock(tick);
        placed = false;
        rejoining = false;
        stopping = false;
        foot.reset();
        yaw.reset();
        stuckTicks = 0;
        tries = 0;
        tryTicks = 0;
        joinedTicks = 0;
        staffTicks = -1;
        stateSince = tick;
        setState(calibrated ? State.JOINING : State.CALIBRATING, "");
        events.add(new Event(Event.Kind.RESUMED, -1, 0.0, ""));
    }

    public void pause(String why) {
        if (state == State.PAUSED && reason.equals(why)) return;
        setState(State.PAUSED, why == null ? "" : why);
        waitingAt = -1;
        events.add(new Event(Event.Kind.PAUSED, -1, 0.0, why == null ? "" : why));
    }

    public void manual() {
        if (state == State.MANUAL) return;
        setState(State.MANUAL, "");
        waitingAt = -1;
        events.add(new Event(Event.Kind.MANUAL, -1, 0.0, ""));
    }

    public void noBudget() {
        if (state != State.NO_BUDGET) setState(State.NO_BUDGET, "");
    }

    public void recording() {
        setState(State.RECORDING, "");
    }

    public void skipStop() {
        if (state == State.WAITING) {
            depart("skipped");
        } else if (nextStop >= 0) {
            nextStop = stopS.length > 1 ? (nextStop + 1) % stopS.length : -1;
            if (!sp.closed() && nextStop == 0) nextStop = -1;
            stopping = false;
            if (state == State.APPROACHING) setState(State.RUNNING, "");
        }
    }

    private boolean scheduled;

    public void scheduled(boolean on) {
        if (scheduled == on) return;
        scheduled = on;
        pickNextStop();
    }

    public boolean scheduled() {
        return scheduled;
    }

    public int sentTo() {
        return sendTo;
    }

    private int offsetNode = -1;
    private double offsetDs;
    private double capS = Double.NaN;
    private double capV;
    private boolean handingOver;
    private int handOverTicks;

    public void handOver(Pathway next) {
        setPathway(next);

        stopping = false;
        foot.reset();
        lastToStop = Double.POSITIVE_INFINITY;
        offsetNode = -1;
        if (state == State.APPROACHING) setState(State.RUNNING, "");
        handingOver = true;
        handOverTicks = 0;
    }

    public void capAt(double at, double speed) {
        capS = at;
        capV = Math.max(SpeedPlan.CREEP, speed);
    }

    public void stopOffset(int node, double ds) {
        offsetNode = node;
        offsetDs = Math.max(-6.0, Math.min(6.0, ds));
    }

    public void arriveNow(long tick) {
        if (state != State.APPROACHING && state != State.RUNNING) return;
        arrivedMiss = 0.0;
        arrive(tick);
    }

    private int redockNode = -1;
    private double redockFrom;
    private double redockBack;

    public void redock(double back) {
        if (state != State.WAITING || waitingAt < 0) return;
        redockNode = waitingAt;
        redockFrom = s;
        redockBack = Math.max(0.5, Math.min(4.0, back));
        sendTo = redockNode;
        waitingAt = -1;
        stopping = false;
        lastToStop = Double.POSITIVE_INFINITY;
        foot.reset();
        setState(State.APPROACHING, "backing off to dock again");
    }

    public boolean redocking() {
        return redockNode >= 0;
    }

    public void sendTo(int nodeId) {
        sendTo = nodeId;
        recallToStart = false;
        pickNextStop();
        if (state == State.WAITING && waitingAt != nodeId) depart("sent on");
    }

    public void recall() {
        recallToStart = true;
        sendTo = pathway.nodes().isEmpty() ? -1 : pathway.nodes().get(0).id();
        if (state == State.WAITING) depart("recalled");
    }

    public State state() {
        return state;
    }

    public String reason() {
        return reason;
    }

    public long stateSince() {
        return stateSince;
    }

    public double progress() {
        return s;
    }

    public boolean placed() {
        return placed;
    }

    public double lateral() {
        return lateral;
    }

    public double headingErrorDeg() {
        return Math.toDegrees(headingError);
    }

    public int waitingAt() {
        return waitingAt;
    }

    public long waitingSince() {
        return waitingSince;
    }

    public int laps() {
        return laps;
    }

    public double lastLapSeconds() {
        return lastLap;
    }

    public Mode mode() {
        return mode;
    }

    public String subject() {
        return subject;
    }

    public int nextStopNode() {
        if (sendTo >= 0 && (recallToStart || scheduled)) return sendTo;
        return nextStop >= 0 && nextStop < stopNode.length ? stopNode[nextStop] : -1;
    }

    public double eta() {
        double target = nextTargetS();
        if (Double.isNaN(target) || !placed) return -1.0;
        return plan.seconds(s, target);
    }

    public List<Event> drainEvents() {
        List<Event> out = new ArrayList<>(events);
        events.clear();
        return out;
    }

    public double plannedSpeed() {
        return plan.at(s);
    }

    public double yawRate() {
        return yaw.rate();
    }

    public double goalSpeed() {
        return lastGoal;
    }

    LongitudinalControl.Out foot() {
        return lastFoot;
    }

    public boolean slowing() {
        return lastFoot.brake() > 0.0 || lastFoot.aRef() < -SLOWING_LAMPS || lastFoot.mode() == LongitudinalControl.Mode.HOLD;
    }

    public static final double SLOWING_LAMPS = 0.8;

    public Command tick(Sensors in) { return tick(in, true); }

    public Command tickUnseen(Sensors in) { return tick(in, false); }

    private Command tick(Sensors in, boolean measured) {
        clock(in.tick());
        yaw.update(PathSpline.heading(in.hx(), in.hz()));
        if (measured) learn(in);
        lastSensors = in;
        prevGoal = lastGoal;
        prevFoot = lastFoot;
        lastGoal = Double.NaN;
        lastKappa = Double.NaN;
        lastFoot = LongitudinalControl.Out.HOLD;

        Command c = !measured && (state == State.STUCK || state == State.LOST || state == State.CALIBRATING)
                ? hold(Mode.HOLD, reason) : decide(in);
        lastCommand = c;
        return c;
    }

    private Command decide(Sensors in) {
        if (in.driverKeys() && state.drives()) manual();
        switch (state) {
            case PAUSED, MANUAL, RECORDING, FINISHED, NO_BUDGET -> {
                mode = Mode.NONE;
                foot.reset();
                return Command.PARK;
            }
            default -> {
            }
        }
        if (sp.isEmpty() || sp.length() < 1.0) {
            pause("the pathway is empty");
            return Command.PARK;
        }

        if (in.staffHeld()) {
            if (staffTicks < 0) events.add(new Event(Event.Kind.STAFF, nodeHere(), 0.0, ""));
            staffTicks = 0;
            reason = "held by a physics staff";
            return hold(Mode.HOLD, "");
        }
        if (staffTicks >= 0) {
            if (++staffTicks < STAFF_SETTLE) return hold(Mode.HOLD, "");
            staffTicks = -1;
            placed = false;
            setState(State.JOINING, "");
        }
        if (state == State.CALIBRATING) {
            if (in.calibrated() || in.tick() - stateSince > 60) setState(State.JOINING, "");

            mode = Mode.NONE;
            return new Command(true, 0.0, 0.0, 0.0, false, 0);
        }

        if (!place(in)) return state.drives() ? hold(Mode.HOLD, "") : Command.PARK;
        return drive(in);
    }

    private boolean place(Sensors in) {
        if (!placed) {
            PathProjection.Result r = PathProjection.global(sp, in.x(), in.y(), in.z(), in.hx(), in.hz(), JOIN_REACH, JOIN_DY, 90.0);
            if (!r.found()) {
                PathProjection.Result any = PathProjection.global(sp, in.x(), in.y(), in.z(), 0.0, 0.0, JOIN_REACH, JOIN_DY, 180.0);
                String why = any.found() ? "turn me to face the route" : "too far from the route: bring me within "
                        + (int) JOIN_REACH + " blocks";
                events.add(new Event(Event.Kind.CANNOT_JOIN, -1, 0.0, why));
                pause(why);
                return false;
            }
            s = r.s();
            placed = true;
            joinedTicks = 0;
            lapTravel = 0.0;
            if (state != State.JOINING) setState(State.JOINING, "");
            pickNextStop();
        }
        double v = Math.max(0.0, in.speed());
        double before = s;
        PathProjection.Result r = PathProjection.window(sp, in.x(), in.y(), in.z(), s, 3.0, 6.0 + v * 0.5);
        if (!r.found()) return true;
        double moved = sp.closed() ? sp.signedDelta(before, r.s()) : r.s() - before;
        s = r.s();
        lateral = r.lateral();
        double[] t = sp.tangent(s);
        headingError = PathSpline.wrapAngle(PathSpline.heading(in.hx(), in.hz()) - PathSpline.heading(t[0], t[1]));
        if (moved > 0.0) lapTravel += moved;
        if (sp.closed() && lapTravel >= sp.length()) {
            lapTravel -= sp.length();
            if (lapStart >= 0) {
                lastLap = (in.tick() - lapStart) / 20.0;
                laps++;
                events.add(new Event(Event.Kind.LAP, -1, lastLap, ""));
            }
            lapStart = in.tick();
        }
        if (lapStart < 0) lapStart = in.tick();
        return true;
    }

    private Command drive(Sensors in) {
        double v = in.speed();
        double absLat = Math.abs(lateral);
        double absHead = Math.abs(headingError);
        Comfort comfort = options.comfort(pathway);

        if (state != State.JOINING && state != State.LOST
                && (absLat > LOST_HARD || absHead > Math.toRadians(LOST_DEGREES))) {
            setState(State.LOST, String.format(Locale.ROOT, "%.0f blocks off the line", absLat));
            events.add(new Event(Event.Kind.LOST, nodeHere(), absLat, ""));
        }
        if (state == State.LOST) return slowToRest(in, comfort, Mode.HOLD, "");

        boolean wasRejoining = rejoining;
        if (handingOver) {
            if ((absHead < Math.toRadians(4.0) && absLat < 0.3) || ++handOverTicks > 80) {
                handingOver = false;
                rejoining = absLat > REJOIN_LATERAL || absHead > Math.toRadians(REJOIN_DEGREES);
            } else {
                rejoining = true;
            }
        } else {
            if (!rejoining && (absLat > REJOIN_LATERAL || absHead > Math.toRadians(REJOIN_DEGREES))) rejoining = true;
            else if (rejoining && absLat < REJOIN_LATERAL * 0.5 && absHead < Math.toRadians(REJOIN_DEGREES * 0.5)) rejoining = false;
            if (state == State.JOINING) rejoining = absLat > JOINED_LATERAL || absHead > Math.toRadians(JOINED_DEGREES);
            if (wasRejoining && !rejoining && state != State.JOINING) events.add(new Event(Event.Kind.REJOINED, nodeHere(), 0.0, ""));
        }

        if (state == State.JOINING) {
            if (absLat < JOINED_LATERAL && absHead < Math.toRadians(JOINED_DEGREES)) joinedTicks++;
            else joinedTicks = 0;
            if (joinedTicks >= 10) {
                setState(State.RUNNING, "");
                events.add(new Event(Event.Kind.JOINED, nodeHere(), 0.0, ""));
            } else if (in.tick() - stateSince > JOIN_GIVE_UP_TICKS) {
                setState(State.LOST, "could not reach the route");
                events.add(new Event(Event.Kind.LOST, nodeHere(), absLat, reason));
                return slowToRest(in, comfort, Mode.HOLD, "");
            }
        }

        if (state == State.WAITING) {
            if (in.departOk()) depart("");
            else return hold(Mode.HOLD, labelOf(waitingAt));
        }
        if (state == State.DEPARTING && sp.ahead(departedFrom, s) > DEPART_DISTANCE && sp.ahead(departedFrom, s) < sp.length() * 0.5) {
            setState(State.RUNNING, "");
            pickNextStop();
        }

        if (redockNode >= 0) {
            double backed = sp.closed() ? sp.signedDelta(s, redockFrom) : redockFrom - s;
            if (backed < redockBack && state == State.APPROACHING) {
                mode = Mode.REVERSE;
                subject = labelOf(redockNode);
                lastFoot = foot.reverse(v, REVERSE_SPEED, plant);
                return command(lastFoot, 0.0, false);
            }
            redockNode = -1;
            foot.reset();
        }

        double target = state == State.DEPARTING ? Double.NaN : nextTargetS();
        double toStop = Double.POSITIVE_INFINITY;
        if (!Double.isNaN(target)) {
            double d = distanceTo(target);
            if (d < -1.5) {
                events.add(new Event(Event.Kind.MISSED_STOP, targetNode(), -d, ""));
                advancePastTarget();
                stopping = false;
                lastToStop = Double.POSITIVE_INFINITY;
                target = nextTargetS();
                d = Double.isNaN(target) ? Double.POSITIVE_INFINITY : distanceTo(target);
            }
            toStop = d;
            lastToStop = d;
            if (state == State.RUNNING && toStop < Math.max(8.0, v * 3.0 + 4.0)) setState(State.APPROACHING, "");
        } else {
            lastToStop = Double.POSITIVE_INFINITY;
        }

        Command hazard = hazard(in, comfort, v);
        if (hazard != null) return hazard;

        Command blocked = blocked(in, comfort, v);
        if (blocked != null) return blocked;

        if (Double.isFinite(toStop) && (state == State.APPROACHING || state == State.RUNNING)) {
            if (Math.abs(toStop) <= ARRIVE_DISTANCE && Math.abs(v) < ARRIVE_SPEED) {
                arrivedMiss = -toStop;
                arrive(in.tick());
                return hold(Mode.HOLD, labelOf(waitingAt));
            }
        }

        Command stuck = stuck(in, comfort, v);
        if (stuck != null) return stuck;

        double steer = steer(in, v, comfort);

        if (Double.isFinite(toStop) && (state == State.APPROACHING || state == State.RUNNING)) {
            double need = v > 0.0 ? v * v / (2.0 * Math.max(0.05, toStop)) : 0.0;
            if (stopping || (toStop < 1.2 && v < 2.0) || need >= comfort.decel * DECEL_MARGIN) {
                stopping = true;
                if (toStop <= 0.05 && Math.abs(v) < 0.3) {
                    lastFoot = foot.hold();
                    mode = Mode.STOPPING;
                    subject = labelOf(targetNode());
                    return new Command(true, 0.0, steer, 1.0, false, indicate());
                }
                lastFoot = foot.stopWithin(Math.max(0.0, v), Math.max(0.0, toStop), comfort.decel * DECEL_MARGIN, plant, in.grade());
                lastGoal = 0.0;
                mode = Mode.STOPPING;
                subject = labelOf(targetNode());
                return command(lastFoot, steer, false);
            }
        }

        double goal = goal(in, comfort, v, toStop);

        double lean = Math.max(Math.abs(in.roll()), Math.abs(in.pitch()) * 0.7);
        if (Double.isFinite(lean) && lean > LEAN_WARN) {
            goal = Math.min(goal, lean > LEAN_STOP ? 0.0 : SpeedPlan.CREEP);
            mode = Mode.EASE;
            subject = String.format(Locale.ROOT, "a %.0f degree lean", lean);
        }
        lastGoal = goal;
        lastFoot = foot.drive(v, goal, comfort.accel * ACCEL_MARGIN, comfort.decel * DECEL_MARGIN, comfort.jerk, plant, in.grade());
        return command(lastFoot, steer, false);
    }

    private double goal(Sensors in, Comfort comfort, double v, double toStop) {
        double speed = Math.max(0.0, v);
        double lead = speed * (SPEED_LEAD + comfort.decel / (2.0 * comfort.jerk)) + 0.5;
        double goal = Double.POSITIVE_INFINITY;
        int binding = -1;
        for (double d = 0.0; d <= lead + 1.0e-9; d += sp.step()) {
            double at = s + d;
            if (!sp.closed() && at > sp.length()) break;
            double p = plan.at(at);
            if (p < goal) {
                goal = p;
                binding = plan.indexAt(at);
            }
        }
        if (!Double.isFinite(goal)) goal = plan.at(s);

        boolean capped = false;
        if (Double.isFinite(capS)) {
            double d = sp.closed() ? sp.signedDelta(s, capS) : capS - s;
            if (d > -2.0 && d < 80.0) {
                double capGoal = Math.sqrt(capV * capV + 2.0 * comfort.decel * Math.max(0.0, d));
                if (capGoal < goal) {
                    goal = capGoal;
                    capped = true;
                }
            }
        }
        Mode why = Mode.CRUISE;
        String about = "";
        double cruise = options.cruise(pathway) * throttleShare;
        if (binding >= 0 && goal < cruise - 0.05 && plan.cause(binding) != SpeedPlan.Cause.CRUISE) {
            int here = plan.indexAt(s);
            boolean behind = here >= 0 && plan.source(here) == plan.source(binding) && plan.at(s + lead) > plan.at(s) + 0.02
                    && sp.signedDelta(sp.sampleS(plan.source(binding)), s) > 0.0;
            why = behind ? Mode.BUILD : Mode.EASE;
            about = describe(plan.cause(binding), plan.source(binding));
        }
        if (capped) {
            why = Mode.EASE;
            about = "the junction ahead";
        }
        if (!plant.learned()) goal = Math.min(goal, Math.max(SpeedPlan.CREEP, goal * PlantModel.UNLEARNED_CRUISE));

        else goal = Math.min(goal, TOP_SHARE * plant.k());
        if (Double.isFinite(toStop) && toStop > 0.0) {
            double cap = LongitudinalControl.stopSpeed(toStop, comfort.decel, comfort.jerk);
            if (cap < goal) {
                goal = cap;
                why = Mode.APPROACH;
                about = labelOf(targetNode());
            }
        }
        if (state == State.DEPARTING) {
            goal = Math.max(Math.min(goal, 3.0), SpeedPlan.CREEP);
            why = Mode.LEAVE;
            about = "";
        }

        if (Double.isFinite(in.gapAhead())) {
            double cap = TrafficGap.speedCap(in.gapAhead(), STANDOFF, comfort.decel, in.speedAhead());
            if (cap < goal) {
                goal = cap;
                why = Mode.FOLLOW;
                about = "";
            }
        }

        if (Double.isFinite(in.obstacle())) {
            double cap = LongitudinalControl.stopSpeed(in.obstacle() - OBSTACLE_MARGIN, comfort.decel, comfort.jerk);
            if (cap < goal) {
                goal = cap;
                why = Mode.BLOCKED;
                about = in.obstacleWhat() == null ? "" : in.obstacleWhat();
            }
        }
        if (Double.isFinite(in.hazard())) {
            double cap = LongitudinalControl.stopSpeed(in.hazard() - HAZARD_MARGIN, comfort.decel, comfort.jerk);
            if (cap < goal) {
                goal = cap;
                why = Mode.HAZARD;
                about = in.hazardWhat() == null ? "" : in.hazardWhat();
            }
        }

        if (Double.isFinite(in.unknown())) {
            double cap = LongitudinalControl.stopSpeed(in.unknown() - HAZARD_MARGIN, comfort.decel, comfort.jerk);
            if (cap < goal) {
                goal = cap;
                why = Mode.EASE;
                about = "the stretch ahead, not surveyed yet";
            }
        }
        if (rejoining || state == State.JOINING) {
            // cap only comes down during a rejoin, or the S back onto the line surges twice

            double k = Math.abs(lastRejoinKappa);
            double cap = Math.min(JOIN_SPEED, k > 1.0e-3 ? Math.sqrt(comfort.lateral / k) : JOIN_SPEED);
            cap = Math.max(SpeedPlan.CREEP, cap);
            rejoinCap = Math.min(rejoinCap, cap);
            if (rejoinCap < goal) {
                goal = rejoinCap;
                why = Mode.REJOIN;
                about = "";
            }
        } else {
            rejoinCap = Double.POSITIVE_INFINITY;
        }
        mode = why;
        subject = about;
        return Math.max(0.0, goal);
    }

    private String describe(SpeedPlan.Cause cause, int sample) {
        int node = sp.nearestNode(sp.sampleS(Math.max(0, sample)));
        String at = node >= 0 && node < pathway.nodes().size() ? pathway.label(pathway.nodes().get(node).id()) : "";
        return switch (cause) {
            case BEND -> "the bend at " + at;
            case LIMIT -> "the limit at " + at;
            case GRADE -> "the slope at " + at;
            case STOP -> "the stop at " + at;
            case END -> "the end of the line";
            case SURFACE -> "the slippery ground at " + at;
            case BUMP -> "the bump at " + at;
            case LEAN -> "the lean at " + at;
            case CRUISE -> "";
        };
    }

    // stop short, wait, go on by itself. never honk at a block
    @Nullable
    private Command hazard(Sensors in, Comfort comfort, double v) {
        double room = Double.POSITIVE_INFINITY;
        String what = "";
        if (Double.isFinite(in.hazard())) {
            room = in.hazard() - HAZARD_MARGIN;
            what = in.hazardWhat();
        }
        double[] bend = impossibleBendAhead(in, v, comfort);
        if (bend != null && bend[0] - HAZARD_MARGIN < room) {
            room = bend[0] - HAZARD_MARGIN;
            what = String.format(Locale.ROOT, "the bend at %s, tighter than it can turn (radius %.1f, its own %.1f)",
                    pathway.label(pathway.nodes().get(Math.max(0, sp.nearestNode(bend[1]))).id()), bend[2], bend[3]);
        }
        if (in.obstacleBlock() && Double.isFinite(in.obstacle()) && in.obstacle() - OBSTACLE_MARGIN < room) {
            room = in.obstacle() - OBSTACLE_MARGIN;
            what = in.obstacleWhat();
        }
        boolean near = Double.isFinite(room) && (room < stopDistance(v, comfort) + 0.75 || room < 0.5);
        if (near) {
            clearTicks = 0;
            if (state != State.HAZARD) {
                resumeTo = state == State.STUCK || state == State.BLOCKED ? State.RUNNING : state;
                setState(State.HAZARD, what == null ? "" : what);
                events.add(new Event(Event.Kind.HAZARD, nodeHere(), room, reason));
            } else if (what != null && !what.isEmpty()) {
                reason = what;
            }
        }
        if (state != State.HAZARD) return null;
        if (!near) {
            if (++clearTicks >= CLEAR_TICKS) {
                events.add(new Event(Event.Kind.CLEARED, nodeHere(), 0.0, ""));
                setState(resumeTo, "");
                return null;
            }
        }
        double steer = steer(in, v, comfort);
        mode = Mode.HAZARD;
        subject = reason;
        if (!Double.isFinite(room) || room <= 0.05 || Math.abs(v) < 0.05) {
            lastFoot = foot.hold();
            return new Command(true, 0.0, steer, 1.0, false, 0);
        }

        lastFoot = foot.stopWithin(Math.max(0.0, v), room, Double.POSITIVE_INFINITY, plant, in.grade());
        return command(lastFoot, steer, false);
    }

    public static final double IMPOSSIBLE_CUT = TightBends.CUT;

    private double maxKappa;
    private boolean[] impossible = new boolean[0];
    private double impossibleKappa = -1.0;
    private double[] impossibleRadius = new double[0];

    @Nullable
    private double[] impossibleBendAhead(Sensors in, double v, Comfort comfort) {
        if (in.skid() || !(in.kappaPerStick() > 1.0e-6)) return null;
        maxKappa = Math.max(maxKappa, in.kappaPerStick());
        if (Math.abs(maxKappa - impossibleKappa) > impossibleKappa * 0.05 || impossible.length != sp.sampleCount()) {
            impossibleKappa = maxKappa;
            markImpossible(1.0 / maxKappa);
        }
        double speed = Math.max(0.0, v);
        double reach = Math.max(24.0, stopDistance(speed, comfort) + 12.0);
        for (double d = 0.0; d <= reach; d += 0.5) {
            double at = s + d;
            if (!sp.closed() && at > sp.length()) break;
            int i = plan.indexAt(at);
            if (i >= 0 && i < impossible.length && impossible[i]) {
                return new double[]{d, at, impossibleRadius[i], 1.0 / maxKappa};
            }
        }
        return null;
    }

    private void markImpossible(double own) {
        TightBends.Result r = TightBends.of(pathway, sp, own);
        impossible = r.impossible();
        impossibleRadius = r.radius();
    }

    @Nullable
    private Command blocked(Sensors in, Comfort comfort, double v) {
        boolean blockedNow = false;
        double room = Double.POSITIVE_INFINITY;
        if (Double.isFinite(in.obstacle()) && !in.obstacleBlock()) {
            room = in.obstacle() - OBSTACLE_MARGIN;
            blockedNow = room < stopDistance(v, comfort) + 0.5 || room < 0.5;
        }
        if (blockedNow) {
            clearTicks = 0;
            if (state != State.BLOCKED) {
                resumeTo = state == State.STUCK || state == State.HAZARD ? State.RUNNING : state;
                setState(State.BLOCKED, in.obstacleWhat() == null ? "" : in.obstacleWhat());
                events.add(new Event(Event.Kind.BLOCKED, nodeHere(), 0.0, reason));
                blockedTicks = 0;
            }
        }
        if (state != State.BLOCKED) return null;
        blockedTicks++;
        if (!blockedNow) {
            if (++clearTicks >= CLEAR_TICKS) {
                events.add(new Event(Event.Kind.CLEARED, nodeHere(), blockedTicks / 20.0, ""));
                setState(resumeTo, "");
                return null;
            }
        } else if (in.obstacleWhat() != null && !in.obstacleWhat().isEmpty()) {
            reason = in.obstacleWhat();
        }

        boolean horn = options.hornWhenBlocked() && blockedNow && blockedTicks >= TOOT_AFTER
                && (blockedTicks - TOOT_AFTER) % TOOT_EVERY == 0;
        double steer = steer(in, v, comfort);
        mode = Mode.BLOCKED;
        subject = reason;
        if (!Double.isFinite(room) || room <= 0.05 || Math.abs(v) < 0.05) {
            lastFoot = foot.hold();
            return new Command(true, 0.0, steer, 1.0, horn, 0);
        }

        double max = in.obstaclePlayer() ? Double.POSITIVE_INFINITY : comfort.decel * DECEL_MARGIN * 2.0;
        lastFoot = foot.stopWithin(Math.max(0.0, v), Math.max(0.0, room), max, plant, in.grade());
        return command(lastFoot, steer, horn);
    }

    @Nullable
    private Command stuck(Sensors in, Comfort comfort, double v) {
        if (state != State.STUCK) {
            boolean wants = prevGoal > 0.6 && prevFoot.mode() == LongitudinalControl.Mode.DRIVE;
            if (wants && Math.abs(v) < STUCK_SPEED) stuckTicks++;
            else if (!wants || Math.abs(v) > 0.6) stuckTicks = 0;
            if (stuckTicks < STUCK_TICKS) return null;
            String why = diagnose(in, v);
            stuckWhy = why;
            setState(State.STUCK, why + " at " + pathway.label(nodeIdAt(s)));
            events.add(new Event(Event.Kind.STUCK, nodeHere(), 0.0, why));
            tries = 0;
            tryTicks = 0;
            tryFrom = s;
        }

        boolean backing = tries > 0 && tryTicks < TRY_TICKS / 2 && (sp.closed() ? sp.signedDelta(s, tryFrom) : tryFrom - s) < REVERSE_BLOCKS;
        if (!backing && v > 0.8 && tryTicks > 10) {
            stuckTicks = 0;
            tryOffset = 0.0;
            setState(State.RUNNING, "");
            events.add(new Event(Event.Kind.UNSTUCK, nodeHere(), tries, ""));
            return null;
        }
        if (++tryTicks > TRY_TICKS) {
            tries++;
            tryTicks = 0;
            tryFrom = s;
            if (tries > TRIES) {
                pause("stuck at " + pathway.label(nodeIdAt(s)) + ": " + stuckWhy + "; tried more drive and backing off twice");
                tryOffset = 0.0;
                return Command.PARK;
            }
            events.add(new Event(Event.Kind.TRY, nodeHere(), tries, ""));
        }
        mode = Mode.UNSTICK;
        subject = reason;
        double steer = steer(in, v, comfort);
        switch (tries) {
            case 0 -> {
                lastFoot = new LongitudinalControl.Out(1.0, 0.0, 0.0, 0.0, 0.0, LongitudinalControl.Mode.DRIVE);
                return new Command(true, 1.0, steer, 0.0, false, 0);
            }
            default -> {
                tryOffset = (tries % 2 == 1 ? 0.6 : -0.6);
                double back = sp.closed() ? sp.signedDelta(s, tryFrom) : tryFrom - s;
                if (back < REVERSE_BLOCKS && tryTicks < TRY_TICKS / 2) {
                    mode = Mode.REVERSE;
                    lastFoot = foot.reverse(v, REVERSE_SPEED, plant);

                    return command(lastFoot, 0.0, false);
                }
                lastFoot = new LongitudinalControl.Out(0.8, 0.0, 0.0, 0.0, 0.0, LongitudinalControl.Mode.DRIVE);
                return new Command(true, 0.8, steer, 0.0, false, 0);
            }
        }
    }

    private String diagnose(Sensors in, double v) {
        if (v < -0.1) return "the slope is too steep";
        if (Double.isFinite(in.wheelDrive()) && in.wheelDrive() > 0.25) return "its wheels turn but it does not move";
        if (Double.isFinite(in.wheelDrive()) && in.wheelDrive() < 0.05) return "its wheels are not turning";
        return "caught on something";
    }

    private String stuckWhy = "";

    private static double stopDistance(double v, Comfort comfort) {
        double speed = Math.max(0.0, v);
        return speed * speed / (2.0 * comfort.decel) + speed * (comfort.decel / (2.0 * comfort.jerk) + SPEED_LEAD);
    }

    private Command slowToRest(Sensors in, Comfort comfort, Mode m, String about) {
        double v = in.speed();
        mode = m;
        subject = about;
        if (Math.abs(v) < 0.1) {
            lastFoot = foot.hold();
            return Command.HOLD;
        }
        lastGoal = 0.0;
        lastFoot = foot.drive(v, 0.0, comfort.accel, comfort.decel, comfort.jerk, plant, in.grade());
        return command(lastFoot, 0.0, false);
    }

    private Command hold(Mode m, String about) {
        mode = m;
        subject = about;
        lastFoot = foot.hold();
        return Command.HOLD;
    }

    private Command command(LongitudinalControl.Out f, double steer, boolean horn) {
        return new Command(true, f.throttle(), steer, f.brake(), horn, indicate());
    }

    private double lastRejoinKappa;

    private double rejoinCap = Double.POSITIVE_INFINITY;

    private double steer(Sensors in, double v, Comfort comfort) {
        if (in.skid()) return skidSteer(in, v);
        double speed = Math.max(0.0, v);
        double lead = speed * LateralControl.LAG;
        double w = yaw.rate();
        double k = speed > 0.5 && Double.isFinite(w) ? w / speed : 0.0;
        double phi = k * lead;
        double fwd = Math.abs(phi) < 1.0e-4 ? lead : Math.sin(phi) / k;
        double side = Math.abs(phi) < 1.0e-4 ? 0.5 * k * lead * lead : (1.0 - Math.cos(phi)) / k;

        double rx = -in.hz();
        double rz = in.hx();
        double px = in.x() + in.hx() * fwd + rx * side;
        double pz = in.z() + in.hz() * fwd + rz * side;
        double hx = in.hx() * Math.cos(phi) + rx * Math.sin(phi);
        double hz = in.hz() * Math.cos(phi) + rz * Math.sin(phi);

        double kappa;
        if (rejoining || state == State.JOINING) {
            double look = Math.max(5.0, Math.min(16.0, 2.0 * Math.abs(lateral) + 3.0 + 0.5 * speed));
            double aimS = s + lead + look;
            if (!sp.closed()) aimS = Math.min(aimS, sp.length());
            double tx = sp.x(aimS);
            double tz = sp.z(aimS);
            if (!sp.closed() && sp.length() - (s + lead) < look) {
                double[] t = sp.tangent(sp.length());
                double extra = look - (sp.length() - (s + lead));
                tx += t[0] * extra;
                tz += t[1] * extra;
            }
            kappa = PurePursuit.curvature(px, pz, hx, hz, tx, tz);
            lastRejoinKappa = kappa;
        } else {
            double sp0 = s + lead;
            double lx = sp.x(sp0);
            double lz = sp.z(sp0);
            double[] t = sp.tangent(sp0);
            double ey = (px - lx) * -t[1] + (pz - lz) * t[0] - tryOffset;
            double epsi = PathSpline.wrapAngle(PathSpline.heading(hx, hz) - PathSpline.heading(t[0], t[1]));
            double line = lineCurvature(sp0, Math.max(1.0, FF_WINDOW * speed));
            lastLineKappa = line;
            kappa = LateralControl.curvature(line, ey, epsi, speed);
            lastRejoinKappa = 0.0;
        }
        lastKappa = kappa;
        if (!(in.kappaPerStick() > 1.0e-6)) return 0.0;
        return clamp(kappa / in.kappaPerStick(), -1.0, 1.0);
    }

    private double skidSteer(Sensors in, double v) {
        double speed = Math.max(0.0, v);
        double lead = speed * 0.30;
        double w = yaw.rate();
        double k = speed > 0.5 && Double.isFinite(w) ? w / speed : 0.0;
        double phi = k * lead;
        double fwd = Math.abs(phi) < 1.0e-4 ? lead : Math.sin(phi) / k;
        double side = Math.abs(phi) < 1.0e-4 ? 0.5 * k * lead * lead : (1.0 - Math.cos(phi)) / k;
        double rx = -in.hz();
        double rz = in.hx();
        double px = in.x() + in.hx() * fwd + rx * side;
        double pz = in.z() + in.hz() * fwd + rz * side;
        double hx = in.hx() * Math.cos(phi) + rx * Math.sin(phi);
        double hz = in.hz() * Math.cos(phi) + rz * Math.sin(phi);
        double look = Math.max(3.0, Math.min(12.0, 2.0 + 0.65 * speed));
        double aimS = s + lead + look;
        if (!sp.closed()) aimS = Math.min(aimS, sp.length());
        double tx = sp.x(aimS);
        double tz = sp.z(aimS);
        if (!sp.closed() && sp.length() - (s + lead) < look) {
            double[] t = sp.tangent(sp.length());
            double extra = look - (sp.length() - (s + lead));
            tx += t[0] * extra;
            tz += t[1] * extra;
        }
        double kappa = PurePursuit.curvature(px, pz, hx, hz, tx, tz);
        lastKappa = kappa;
        lastRejoinKappa = rejoining || state == State.JOINING ? kappa : 0.0;
        double gain = plant.skidGain() > 0.0 ? plant.skidGain() : 1.0;
        return clamp(kappa * Math.max(speed, 1.0) / gain, -1.0, 1.0);
    }

    private double lineCurvature(double at) {
        return lineCurvature(at, 0.5);
    }

    private double lineCurvature(double at, double half) {
        double sum = 0.0;
        int n = 0;
        for (double d = -half; d <= half + 1.0e-9; d += sp.step()) {
            double q = at + d;
            if (!sp.closed() && (q < 0.0 || q > sp.length())) continue;
            sum += sp.curvatureAt(q);
            n++;
        }
        return n == 0 ? 0.0 : sum / n;
    }

    private void learn(Sensors in) {
        double v = in.speed();
        if (Double.isFinite(lastSpeed) && state.drives()) {
            plant.observe(in.appliedThrottle(), lastSpeed, v, in.appliedBrakeLevel(), in.appliedSteer(), in.grade());
            if (in.skid()) plant.observeSkid(in.appliedSteer(), yaw.rate(), v);
        }
        lastSpeed = v;
    }

    private void arrive(long tick) {
        int node = targetNode();
        stopping = false;
        foot.hold();
        boolean terminal = !sp.closed() && isEnd(node);
        boolean recalled = recallToStart && node == sendTo;
        if (sendTo == node) sendTo = -1;
        if (recalled) {
            recallToStart = false;
            waitingAt = -1;
            events.add(new Event(Event.Kind.ARRIVED, node, 0.0, "recalled"));
            pause("recalled to the start");
            return;
        }
        if (terminal && !hasStop(node) && !scheduled) {
            setState(State.FINISHED, "");
            events.add(new Event(Event.Kind.FINISHED, node, 0.0, ""));
            return;
        }
        waitingAt = node;
        waitingSince = tick;
        setState(State.WAITING, "");

        events.add(new Event(Event.Kind.ARRIVED, node, arrivedMiss, ""));
    }

    private double arrivedMiss;

    private void depart(String why) {
        int node = waitingAt;
        waitingAt = -1;
        offsetNode = -1;
        redockNode = -1;
        departedFrom = s;
        stopping = false;
        lastToStop = Double.POSITIVE_INFINITY;
        foot.reset();
        if (!sp.closed() && isEnd(node)) {
            setState(State.FINISHED, "");
            events.add(new Event(Event.Kind.FINISHED, node, 0.0, ""));
            return;
        }
        setState(State.DEPARTING, "");
        advancePastTarget();
        events.add(new Event(Event.Kind.DEPARTED, node, 0.0, why == null ? "" : why));
    }

    private double distanceTo(double target) {
        if (!sp.closed()) return target - s;
        double d = sp.ahead(s, target);
        if (d > sp.length() - 3.0 && lastToStop < 3.0) return d - sp.length();
        return d;
    }

    private double nextTargetS() {
        double at = baseTargetS();
        if (Double.isNaN(at) || offsetNode < 0 || targetNode() != offsetNode) return at;
        return sp.closed() ? sp.wrap(at + offsetDs) : Math.max(0.0, Math.min(sp.length(), at + offsetDs));
    }

    private double baseTargetS() {
        if (sendTo >= 0) {
            int idx = pathway.indexOf(sendTo);
            if (idx >= 0 && idx < sp.nodeCount()) return sp.nodeS(idx);
            sendTo = -1;
        }
        if (nextStop >= 0 && nextStop < stopS.length) return stopS[nextStop];
        return sp.closed() ? Double.NaN : sp.length();
    }

    private int targetNode() {
        if (sendTo >= 0) return sendTo;
        if (nextStop >= 0 && nextStop < stopNode.length) return stopNode[nextStop];
        return pathway.nodes().isEmpty() ? -1 : pathway.nodes().get(pathway.nodes().size() - 1).id();
    }

    private void advancePastTarget() {
        if (sendTo >= 0) {
            sendTo = -1;
        } else if (nextStop >= 0) {
            nextStop = nextStop + 1 < stopS.length ? nextStop + 1 : (sp.closed() && stopS.length > 0 ? 0 : -1);
        }
    }

    private void pickNextStop() {
        nextStop = -1;
        if (stopS.length == 0 || scheduled) return;
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i < stopS.length; i++) {
            double d = sp.closed() ? sp.ahead(s, stopS[i]) : stopS[i] - s;

            if (d < -ARRIVE_DISTANCE) continue;
            if (sp.closed() && d > sp.length() - ARRIVE_DISTANCE) d -= sp.length();
            if (state == State.DEPARTING && d < DEPART_DISTANCE) continue;
            if (d < best) {
                best = d;
                nextStop = i;
            }
        }
        if (nextStop < 0 && sp.closed()) nextStop = 0;
    }

    private boolean isEnd(int nodeId) {
        return !pathway.nodes().isEmpty() && pathway.nodes().get(pathway.nodes().size() - 1).id() == nodeId;
    }

    private boolean hasStop(int nodeId) {
        PathNode n = pathway.node(nodeId);
        return n != null && n.isStop();
    }

    private String labelOf(int nodeId) {
        return nodeId >= 0 && pathway.node(nodeId) != null ? pathway.label(nodeId) : "";
    }

    private int indicate() {
        if (state != State.DEPARTING) return 0;
        PathNode n = null;
        for (int i = 0; i < stopNode.length; i++) {
            if (Math.abs(sp.signedDelta(stopS[i], departedFrom)) < 1.0) n = pathway.node(stopNode[i]);
        }
        if (n == null || n.stop() == null || !n.stop().indicate()) return 0;
        double k = 0.0;
        for (double d = 0.0; d < 12.0; d += 1.0) k += sp.curvatureAt(s + d);
        return Math.abs(k) < 0.05 ? 0 : (k > 0 ? 1 : -1);
    }

    private int nodeIdAt(double at) {
        int idx = sp.segmentAt(at);
        return idx >= 0 && idx < pathway.nodes().size() ? pathway.nodes().get(idx).id() : -1;
    }

    private int nodeHere() {
        return nodeIdAt(s);
    }

    private void setState(State next, String why) {
        if (next != state) stateSince = lastTickHint;
        state = next;
        reason = why == null ? "" : why;
        if (next != State.APPROACHING && next != State.RUNNING) stopping = false;
    }

    private long lastTickHint;

    public void clock(long tick) {
        lastTickHint = tick;
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public void traceInto(RouteTrace t) {
        t.put(RouteTrace.Ch.STATE, state.ordinal());
        t.put(RouteTrace.Ch.MODE, mode.ordinal());
        t.put(RouteTrace.Ch.S, s);
        t.put(RouteTrace.Ch.LATERAL, lateral);
        t.put(RouteTrace.Ch.HEADING, Math.toDegrees(headingError));
        t.put(RouteTrace.Ch.YAW, yaw.rate());
        if (placed) {
            t.put(RouteTrace.Ch.PLAN, plan.at(s));
            t.put(RouteTrace.Ch.PATH_KAPPA, lineCurvature(s));
        }
        t.put(RouteTrace.Ch.TARGET, lastGoal);
        t.put(RouteTrace.Ch.TARGET_ACCEL, lastFoot.aRef());
        t.put(RouteTrace.Ch.CMD_THROTTLE, lastCommand.throttle());
        t.put(RouteTrace.Ch.CMD_BRAKE, lastCommand.brakeLevel());
        t.put(RouteTrace.Ch.CMD_STEER, lastCommand.steer());
        t.put(RouteTrace.Ch.KAPPA, lastKappa);
        Sensors in = lastSensors;
        if (in != null) {
            t.put(RouteTrace.Ch.OBSTACLE, Double.isFinite(in.obstacle()) ? in.obstacle() : 327.0);
            t.put(RouteTrace.Ch.GAP, Double.isFinite(in.gapAhead()) ? in.gapAhead() : 327.0);
            t.put(RouteTrace.Ch.PITCH, in.pitch());
            t.put(RouteTrace.Ch.ROLL, in.roll());
            t.put(RouteTrace.Ch.YAW_RAW, in.yawRate());
        }
    }

    public record Saved(State state, String reason, double s, int nextStop, int waitingAt, long waitingSince, int sendTo,
                        boolean recallToStart, int laps, double lastLap) {
    }

    public Saved save() {
        return new Saved(state, reason, s, nextStop, waitingAt, waitingSince, sendTo, recallToStart, laps, lastLap);
    }

    public record Travel(State resumeTo, long stateSince, double lapTravel, long lapStart, double departedFrom,
                         double lastToStop, int joinedTicks, int blockedTicks, int clearTicks, int stuckTicks,
                         int tries, int tryTicks, double tryFrom, double tryOffset, String stuckWhy) {}

    public Travel travel() {
        return new Travel(resumeTo, stateSince, lapTravel, lapStart, departedFrom, lastToStop, joinedTicks,
                blockedTicks, clearTicks, stuckTicks, tries, tryTicks, tryFrom, tryOffset, stuckWhy);
    }

    public void restoreTarget(int node) {
        for (int i = 0; i < stopNode.length; i++) if (stopNode[i] == node) { nextStop = i; return; }
        pickNextStop();
    }

    public void restoreTravel(Saved saved, Travel travel, long tick) {
        if (saved == null) return;
        restore(saved, tick);
        placed = true;
        nextStop = saved.nextStop(); waitingAt = saved.waitingAt(); waitingSince = saved.waitingSince();
        setState(saved.state(), saved.reason());
        if (saved.state() == State.WAITING && (waitingAt < 0 || pathway.node(waitingAt) == null)) {
            waitingAt = -1; setState(State.RUNNING, ""); pickNextStop();
        }
        if (travel != null) {
            resumeTo = travel.resumeTo(); stateSince = travel.stateSince(); lapTravel = travel.lapTravel();
            lapStart = travel.lapStart(); departedFrom = travel.departedFrom(); lastToStop = travel.lastToStop();
            joinedTicks = travel.joinedTicks(); blockedTicks = travel.blockedTicks(); clearTicks = travel.clearTicks();
            stuckTicks = travel.stuckTicks(); tries = travel.tries(); tryTicks = travel.tryTicks();
            tryFrom = travel.tryFrom(); tryOffset = travel.tryOffset(); stuckWhy = travel.stuckWhy();
        } else if (saved.state() != State.WAITING && nextStop < 0) pickNextStop();
        foot.reset();
    }

    public void restore(Saved saved, long tick) {
        if (saved == null) return;
        laps = saved.laps();
        lastLap = saved.lastLap();
        sendTo = saved.sendTo();
        recallToStart = saved.recallToStart();
        s = sp.wrap(saved.s());
        placed = false;
        State st = saved.state();
        clock(tick);
        if (st == State.WAITING && saved.waitingAt() >= 0 && pathway.node(saved.waitingAt()) != null) {
            placed = true;
            waitingAt = saved.waitingAt();
            waitingSince = saved.waitingSince();
            nextStop = saved.nextStop();
            setState(State.WAITING, "");
        } else if (st.drives()) {
            setState(State.JOINING, "");
        } else {
            setState(st, saved.reason());
        }
    }
}
