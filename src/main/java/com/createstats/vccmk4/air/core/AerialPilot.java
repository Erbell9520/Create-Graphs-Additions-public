package com.createstats.vccmk4.air.core;

import com.createstats.vcu.core.Axis6;

import java.util.ArrayList;
import java.util.List;

public final class AerialPilot {
    public static final double DT = 0.05;

    public static final int LAND_TICKS = 20;
    public static final double LAND_HEIGHT = 0.6;
    public static final double LAND_SPEED = 0.2;

    public static final double AIRBORNE = 1.0;

    public static final double TAKEOFF_HOP = 3.0;

    public static final String NO_ANCHOR_ENGINE =
            "No engine to hold the spot: the ship holds its height and heading and drifts with the wind.";

    public static final double TARGET_LEAD = 16.0;

    public static final double ARRIVE = 8.0;

    static final double CAPTURE = 3.0 * ARRIVE;

    static final int STAFF_SETTLE = 40;

    public static final double ANCHOR_SPEED = 0.3;

    public static final double GO_TO_SPEED = 4.0;

    public static final double DEFAULT_TOP_SPEED = 8.0;

    public static final double WORLD_TOP_MARGIN = 48.0;

    public static final double SPOOL_PER_TICK = 1.0 / 20.0;

    public static final double TRIM_RATE = 0.5;

    static final double FAST_BAND_SHARE = 0.05;

    static final double ACROSS_SHARE = 0.1;

    static final double ASTERN_FLOW = 0.5;
    static final double ASTERN_CLEAR = -0.2;

    static final double RUDDER_WAY = 1.0;

    static final double AHEAD_ACCEL = 2.0;

    static final double GUARD_BAND = 1.0;

    static final double WEIGH_CLIMB = 0.5;

    static final double WEIGH_STILL = 0.05;

    static final int WEIGH_TICKS = 10;

    static final double WEIGH_GATE = 0.015;

    static final double WEIGH_MARGIN = 0.02;

    public enum Mode {
        OFF("Off"), HELM("Helm"), ANCHOR("Anchor"), KEEP_COURSE("Keep course"), GO_TO("Go to"), MOORED("Moored");

        public final String label;

        Mode(String label) {
            this.label = label;
        }

        public boolean flying() {
            return this == HELM || this == ANCHOR || this == KEEP_COURSE || this == GO_TO;
        }

        public static Mode byOrdinal(int i) {
            Mode[] v = values();
            return i >= 0 && i < v.length ? v[i] : OFF;
        }
    }

    public record Thrust(double maxForward, double maxReverse, double f0, double fade, double maxSide,
                         double maxUp, double maxDown, double maxYaw) {
        public static final Thrust NONE = new Thrust(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
    }

    public record Sense(double y, double vy, double x, double z, double vx, double vz, double airForward,
                        double airSide, double heading, double yawRate, double mass, double g, double yawInertia,
                        PressureModel air, double pressure, List<AltitudeLaw.Cell> cells, double passiveLift,
                        double keelHeight, double worldTop, Thrust thrust) {
        public double airspeed() {
            return Math.hypot(airForward, airSide);
        }
    }

    public record Keys(boolean piloted, double throttle, double steer, double lift, double slide, boolean boost,
                       double trim) {
        public static final Keys NONE = new Keys(false, 0.0, 0.0, 0.0, 0.0, false, 0.0);

        public Keys(boolean piloted, double throttle, double steer, double lift, double slide, boolean boost) {
            this(piloted, throttle, steer, lift, slide, boost, 0.0);
        }
    }

    public record Command(Mode mode, boolean handsOff, double[] gasTargets, double[] wrench, double[] care,
                          double targetAltitude, double targetSpeed, double targetHeading, double ceiling,
                          double liftUse, boolean liftShort, List<String> notes, double order, double engine,
                          boolean braking, double turn, double flow, boolean weighing) {
    }

    public record State(int mode, int leave, int feel, double telegraph, double targetAltitude, double heading,
                        double speedOrder, double anchorX, double anchorZ, double[] vertical, double drag,
                        double turnDrag) {
    }

    private final AerialSetup setup;
    private Mode mode = Mode.OFF;
    private final Telegraph telegraph = new Telegraph();
    private final HeadingLaw heading = new HeadingLaw();
    private final CruiseLaw cruise = new CruiseLaw();
    private final VerticalObserver vertical = new VerticalObserver();
    private double targetAltitude = Double.NaN;
    private double speedOrder;
    private double anchorX = Double.NaN;
    private double anchorZ = Double.NaN;
    private double engine;
    private boolean wasBraking;
    private int groundTicks;
    private boolean initialised;
    private double[] restoredVertical;
    private Sense last;
    private boolean lastHandsOff = true;
    private double lastForward;
    private double lastUp;
    private double lastYaw;

    private boolean astern;

    private int rudders;

    private double dragPrior = Double.NaN;

    private int frozen;

    private int heldTicks;
    private double heldFromY = Double.NaN;

    private boolean staff;

    private int sinceStaff = STAFF_SETTLE;

    private TerrainGuard.Profile terrainProfile;

    private int terrainAge;

    private TerrainGuard.Result guard = TerrainGuard.Result.NONE;

    private double flown = Double.NaN;

    public AerialPilot() {
        this(new AerialSetup());
    }

    public AerialPilot(AerialSetup setup) {
        this.setup = setup == null ? new AerialSetup() : setup;
    }

    public AerialSetup setup() {
        return setup;
    }

    public Mode mode() {
        return mode;
    }

    public LeaveMode leaveMode() {
        return setup.leave;
    }

    public LeaveMode cycleLeaveMode() {
        setLeaveMode(setup.leave.next());
        return setup.leave;
    }

    public void setLeaveMode(LeaveMode m) {
        setup.leave = m == null ? LeaveMode.ANCHOR : m;
        if (mode.flying() && mode != Mode.HELM && mode != modeFor(setup.leave)) {
            unpiloted(last == null ? 0.0 : last.airForward());
        }
    }

    public void rudders(int n) {
        rudders = Math.max(0, n);
    }

    public void dragPrior(double drag) {
        if (!(drag > 0.0) || !Double.isFinite(drag)) return;
        boolean moved = !Double.isFinite(dragPrior) || Math.abs(drag - dragPrior) > 0.01 * dragPrior;
        dragPrior = drag;
        if (moved && initialised) vertical.setDragPrior(drag);
    }

    public int frozen() {
        return frozen;
    }

    public void staff(boolean held) {
        staff = held;
    }

    public boolean staffHeld() {
        return staff;
    }

    public void terrain(TerrainGuard.Profile profile) {
        terrainProfile = profile;
        terrainAge = 0;
    }

    public TerrainGuard.Result guard() {
        return guard;
    }

    public double flownAltitude() {
        return flown;
    }

    public Feel feel() {
        return setup.feel;
    }

    public void setFeel(Feel feel) {
        setup.feel = feel == null ? Feel.STANDARD : feel;
    }

    public Telegraph telegraph() {
        return telegraph;
    }

    public VerticalObserver vertical() {
        return vertical;
    }

    public CruiseLaw cruise() {
        return cruise;
    }

    public HeadingLaw heading() {
        return heading;
    }

    public double targetAltitude() {
        return targetAltitude;
    }

    public double engine() {
        return engine;
    }

    public boolean grounded() {
        return groundTicks >= LAND_TICKS;
    }

    public void nudgeAltitude(double blocks, double y) {
        if (!Double.isFinite(blocks)) return;
        double from = Double.isFinite(targetAltitude) ? targetAltitude : y;
        targetAltitude = Math.max(y - TARGET_LEAD * 4.0, Math.min(y + TARGET_LEAD * 4.0, from + blocks));
    }

    public void holdHeading(double degrees) {
        heading.holdHeading(degrees);
    }

    public void forget(double mass, double g) {
        vertical.reset(mass > 0.0 ? mass : 1.0, g > 0.0 ? g : 11.0, dragPrior);
        cruise.reset(mass > 0.0 ? mass : 1.0);
        heading.restoreDamping(0.0);
        restoredVertical = null;
    }

    public void applied(double forward, double up, double yaw) {
        lastForward = Double.isFinite(forward) ? forward : 0.0;
        lastUp = Double.isFinite(up) ? up : 0.0;
        lastYaw = Double.isFinite(yaw) ? yaw : 0.0;
    }

    public Command tick(Sense s, Keys keys, double[] destination) {
        Keys k = keys == null ? Keys.NONE : keys;
        List<String> notes = new ArrayList<>();
        Feel feel = setup.feel;
        double passive = Double.isFinite(s.passiveLift()) ? s.passiveLift() : 0.0;

        double loadRoom = AltitudeLaw.maxLift(s.cells(), s.g()) + Math.max(0.0, s.thrust().maxUp()) + passive;
        if (!initialised && s.mass() > 0.0) {
            vertical.reset(s.mass(), s.g(), dragPrior);
            vertical.setLoadRoom(loadRoom);
            if (restoredVertical != null) vertical.restore(restoredVertical);
            if (!Double.isFinite(cruise.drag())) cruise.reset(s.mass());
            initialised = true;
        }
        vertical.setLoadRoom(loadRoom);
        if (staff) sinceStaff = 0;
        else if (sinceStaff < STAFF_SETTLE) sinceStaff++;
        if (terrainProfile != null && ++terrainAge > TerrainGuard.STALE_TICKS) terrainProfile = null;
        guard = TerrainGuard.Result.NONE;
        flown = Double.NaN;
        learn(s);
        boolean grounded = ground(s);
        double hiddenNow = vertical.predict(VerticalObserver.regressors(0.0, s.airspeed(), 0.0, s.pressure())) + passive;
        double ceiling = ceiling(s, hiddenNow);

        if (k.piloted()) {
            if (mode != Mode.HELM) takeHelm(s, grounded);
        } else if (mode == Mode.HELM) {
            leaveHelm(s, grounded);
        } else if (mode.flying() && grounded) {
            mode = Mode.MOORED;
        }
        if (!mode.flying()) {
            lastHandsOff = true;
            engine = 0.0;
            wasBraking = false;
            astern = false;
            heldTicks = 0;
            return new Command(mode, true, new double[s.cells().size()], new double[Axis6.COUNT],
                    new double[Axis6.COUNT], Double.NaN, Double.NaN, Double.NaN, ceiling, Double.NaN, false, notes,
                    telegraph.position(), 0.0, false, 0.0, 1.0, false);
        }
        lastHandsOff = false;

        double carriedMass = s.mass() + loadMass(s.g());
        if (staff) return staffHold(s, notes, feel, passive, carriedMass, ceiling);

        guard = assessTerrain(s, feel, ceiling);
        boolean guarded = mode != Mode.HELM;
        double speedCap = guarded ? guard.speedCap() : Double.POSITIVE_INFINITY;

        double speedTarget = Double.NaN;
        double yaw;
        double side = 0.0;
        double forward;
        double turn;
        boolean anchoring = false;
        boolean ordered = false;
        boolean steering = false;

        if (mode == Mode.HELM) {
            telegraph.update(k.throttle());
            if (Math.abs(k.lift()) > 0.05) {
                double climb = feel.climb * (k.boost() ? 2.0 : 1.0);
                targetAltitude += Math.max(-1.0, Math.min(1.0, k.lift())) * climb * DT;
                targetAltitude = Math.max(s.y() - TARGET_LEAD, Math.min(s.y() + TARGET_LEAD, targetAltitude));
            }
            if (Math.abs(k.trim()) > 0.05) {
                setup.trim = Math.max(-1.0, Math.min(1.0, setup.trim + Math.signum(k.trim()) * TRIM_RATE * DT));
            }
            side = Math.max(-1.0, Math.min(1.0, k.slide())) * s.thrust().maxSide();
            steering = Math.abs(k.steer()) > HeadingLaw.DEADBAND;
            double rate = feel.turn * (k.boost() ? 1.5 : 1.0);
            yaw = heading.moment(k.steer(), s.heading(), s.yawRate(), s.yawInertia(), rate, DT);

            turn = heading.rudder(s.yawRate(), rate);
            ordered = true;
        } else if (mode == Mode.KEEP_COURSE) {
            yaw = heading.moment(0.0, s.heading(), s.yawRate(), s.yawInertia(), feel.turn, DT);
            turn = heading.rudder(s.yawRate(), feel.turn);
            ordered = true;
        } else if (mode == Mode.GO_TO && destination != null && destination.length >= 2
                && Double.isFinite(destination[0]) && Double.isFinite(destination[1])) {
            double dx = destination[0] - s.x();
            double dz = destination[1] - s.z();
            double dist = Math.hypot(dx, dz);
            if (dist < ARRIVE) {
                mode = Mode.ANCHOR;
                anchorX = destination[0];
                anchorZ = destination[1];
                anchoring = true;
            } else if (rudderOnly(s) && dist < CAPTURE && dx * s.vx() + dz * s.vz() < 0.0) {
                // rudder only ships can't turn tighter than the rudder, anchor and close along the nose

                mode = Mode.ANCHOR;
                anchorX = destination[0];
                anchorZ = destination[1];
                anchoring = true;
            } else {
                steering = true;
                double bearing = HeadingLaw.bearing(dx, dz);
                heading.holdHeading(bearing);

                double brake = Math.sqrt(2.0 * feel.accel * 0.5 * Math.max(0.0, dist - ARRIVE));
                double cruiseSpeed = speedOrder > 0.1 ? speedOrder : GO_TO_SPEED;
                speedTarget = Math.min(cruiseSpeed, brake) * approach(HeadingLaw.wrap(bearing - s.heading()));

                if (rudderOnly(s)) speedTarget = Math.max(speedTarget, Math.min(cruiseSpeed, RUDDER_WAY * 1.5));

                speedTarget = Math.min(speedTarget, speedCap);
            }
            yaw = heading.moment(0.0, s.heading(), s.yawRate(), s.yawInertia(), feel.turn, DT);
            turn = heading.rudder(s.yawRate(), feel.turn);
        } else {
            if (mode == Mode.GO_TO) notes.add("No Navigation Table target to go to: holding this spot.");
            anchoring = true;
            yaw = heading.moment(0.0, s.heading(), s.yawRate(), s.yawInertia(), feel.turn, DT);
            turn = heading.rudder(s.yawRate(), feel.turn);
        }

        boolean braking = false;
        if (anchoring && (!Double.isFinite(anchorX) || !Double.isFinite(anchorZ))) {
            if (Math.min(Math.hypot(s.vx(), s.vz()), s.airspeed()) < ANCHOR_SPEED) {
                anchorX = s.x();
                anchorZ = s.z();
            } else {
                anchoring = false;
                speedTarget = 0.0;
                braking = true;
            }
        }
        if (anchoring) {
            double[] f = AnchorLaw.force(anchorX - s.x(), anchorZ - s.z(), s.vx(), s.vz(), carriedMass, feel.accel,
                    cruise.drag(), s.pressure());
            double h = Math.toRadians(s.heading());
            double nx = Math.sin(h);
            double nz = -Math.cos(h);
            forward = f[0] * nx + f[1] * nz;
            side = f[0] * -nz + f[1] * nx;
            forward = Math.max(-s.thrust().maxReverse(), Math.min(s.thrust().maxForward(), forward));
            cruise.clearIntegral();
            engine = 0.0;
            braking = true;
            speedTarget = 0.0;
        } else if (ordered && !telegraph.stopped() && !(guarded && speedCap < s.airForward() + GUARD_BAND)) {
            double order = telegraph.position();
            engine += Math.max(-SPOOL_PER_TICK, Math.min(SPOOL_PER_TICK, order - engine));
            forward = engine >= 0.0 ? engine * s.thrust().maxForward() : engine * s.thrust().maxReverse();
            cruise.clearIntegral();
        } else {
            if (ordered && telegraph.stopped()) {
                speedTarget = 0.0;
                braking = true;
            } else if (ordered) {
                speedTarget = Math.max(0.0, speedCap);
                braking = speedTarget < 0.1;
            }
            if (braking && !wasBraking) cruise.clearIntegral();
            engine = 0.0;
            forward = cruise.force(s.airForward(), Double.isFinite(speedTarget) ? speedTarget : 0.0, carriedMass,
                    s.pressure(), feel.accel, s.thrust().maxForward(), s.thrust().maxReverse(), DT);
        }
        wasBraking = braking;

        if (s.airForward() < -ASTERN_FLOW) astern = true;
        else if (s.airForward() > ASTERN_CLEAR) astern = false;

        double maxF = s.thrust().maxForward() + s.thrust().maxReverse();

        if (mode == Mode.ANCHOR && !(maxF + s.thrust().maxSide() > 0.0)) notes.add(NO_ANCHOR_ENGINE);
        if (ordered && !telegraph.stopped()) {
            if (maxF < ACROSS_SHARE * s.thrust().maxSide()) {
                notes.add("The engines push across the Aerial's nose: turn the nose on the console's Ship page, or with a wrench.");
            } else if (!(maxF > 0.0)) {
                notes.add("No engine pushes along the nose: the telegraph has nothing to drive.");
            }
        }

        if (!Double.isFinite(targetAltitude)) targetAltitude = s.y();
        boolean touching = touching(s);

        boolean heldDown = touching && vertical.load() > 0.0
                && (ceiling == Double.NEGATIVE_INFINITY || (Double.isFinite(ceiling) && ceiling < s.y() + WEIGH_CLIMB));
        if (heldDown) {
            notes.add("Held down: the balloons at their fullest, a tenth kept back, cannot lift the ship clear. Something heavy rests"
                    + " on it, or it is tied down.");
        }
        if (ceiling == Double.NEGATIVE_INFINITY) {
            targetAltitude = Double.NEGATIVE_INFINITY;
            if (!heldDown) notes.add("Too heavy for its balloons at any height: lighten it or add burners.");
        } else if (Double.isFinite(ceiling) && targetAltitude > ceiling) {
            targetAltitude = ceiling;
            boolean capped = Double.isFinite(setup.altitudeCap) && setup.altitudeCap <= ceiling + 1.0e-9;
            if (capped) notes.add(String.format("Held under your cap at y %.0f (the Lift page).", ceiling));
            else if (!heldDown) notes.add(String.format("Ceiling y %.0f: higher, the balloons could not hold it with a tenth to spare.", ceiling));
        }
        double keelFloor = Double.isFinite(s.keelHeight()) ? s.y() - s.keelHeight() - 1.0 : Double.NEGATIVE_INFINITY;

        boolean guardFlies = guarded && terrainProfile != null;
        if ((!guardFlies || !Double.isFinite(targetAltitude)) && targetAltitude < keelFloor) targetAltitude = keelFloor;
        if (!Double.isFinite(targetAltitude)) targetAltitude = s.y();

        flown = targetAltitude;
        if (guardFlies) {
            if (guard.floor() > flown) {
                flown = Double.isFinite(ceiling) ? Math.max(targetAltitude, Math.min(guard.floor(), ceiling)) : guard.floor();
            }
            if (keelFloor > flown) flown = keelFloor;
        }
        String terrainNote = guarded ? guard.note() : guard.helmNote();
        if (terrainNote != null) notes.add(0, terrainNote);
        boolean weighing = weighOff(s, passive, touching);
        if (weighing) {
            notes.add("Weighing off: the ship is heavier than its own blocks (a body resting on it?), so the balloons take up lift"
                    + " until it rises.");
        }

        double ahead;
        if (anchoring) {
            ahead = s.airForward();
        } else if (Double.isFinite(speedTarget)) {
            ahead = s.airForward() + clamp(speedTarget - s.airForward(), -feel.accel * AltitudeLaw.LOOKAHEAD_S,
                    feel.accel * AltitudeLaw.LOOKAHEAD_S);
        } else {
            double drag = Double.isFinite(cruise.drag()) ? cruise.drag() : 0.0;
            double a = carriedMass > 0.0 ? (forward - drag * s.airForward() * s.pressure()) / carriedMass : 0.0;
            ahead = s.airForward() + clamp(a * AltitudeLaw.LOOKAHEAD_S, -AHEAD_ACCEL * AltitudeLaw.LOOKAHEAD_S,
                    AHEAD_ACCEL * AltitudeLaw.LOOKAHEAD_S);
        }
        double[] phi = VerticalObserver.regressors(s.vy(), Math.hypot(ahead, s.airSide()), forward, s.pressure());

        double mass = carriedMass;
        double hidden = vertical.predict(phi) + passive + (carriedMass - s.mass()) * s.g();
        double dragRate = mass > 0.0 ? vertical.estimate(VerticalObserver.DRAG) * s.pressure() / mass : 0.0;
        AltitudeLaw.Plan plan = AltitudeLaw.plan(s.y(), s.vy(), flown, mass, s.g(), hidden, s.cells(),
                feel.altitude(), setup.trim, dragRate);

        double weight = mass * s.g();
        double up = 0.0;
        double upCare = 0.0;
        if (s.thrust().maxUp() > FAST_BAND_SHARE * weight) {
            up = Math.max(-s.thrust().maxDown(), Math.min(s.thrust().maxUp(), plan.force() - AltitudeLaw.lift(s.cells(), s.g())));
            upCare = 1.0;
        }
        double maxLift = AltitudeLaw.maxLift(s.cells(), s.g());
        if (s.cells().isEmpty()) {
            notes.add(passive > 0.0
                    ? "Every balloon is on hand control: the Aerial holds no altitude."
                    : "No balloon with a burner or vent under it: nothing to hold the ship up with.");
        } else if (plan.short_() && maxLift + s.thrust().maxUp() < plan.force()) {
            notes.add("Lift short: even full burners cannot hold this. Lighten the ship or add burners.");
        }

        if (s.thrust().maxYaw() <= 0.0 && steering) {
            if (rudders <= 0) {
                notes.add("Nothing can turn the ship: put engines either side of the centre, a sideways prop, or a rudder at the tail.");
            } else if (Math.abs(s.airForward()) < RUDDER_WAY) {
                notes.add("Rudders steer only under way: ring the telegraph ahead to turn.");
            }
        }

        double[] wrench = new double[Axis6.COUNT];
        wrench[Axis6.FORWARD.index()] = forward;
        wrench[Axis6.UP.index()] = up;
        wrench[Axis6.RIGHT.index()] = side;
        wrench[Axis6.YAW.index()] = yaw;
        double[] care = new double[Axis6.COUNT];
        care[Axis6.FORWARD.index()] = 1.0;
        care[Axis6.UP.index()] = upCare;
        care[Axis6.RIGHT.index()] = 1.0;
        care[Axis6.YAW.index()] = 1.0;
        return new Command(mode, false, plan.targets(), wrench, care, targetAltitude, speedTarget, heading.hold(),
                ceiling, maxLift > 0.0 ? plan.force() / maxLift : Double.NaN, plan.short_(), notes,
                telegraph.position(), engine, braking, turn, astern ? -1.0 : 1.0, weighing);
    }

    static double approach(double offDegrees) {
        double c = Math.cos(Math.toRadians(Math.min(90.0, Math.abs(offDegrees))));
        return c * c;
    }

    private TerrainGuard.Result assessTerrain(Sense s, Feel feel, double ceiling) {
        TerrainGuard.Profile p = terrainProfile;
        if (p == null) return TerrainGuard.Result.NONE;
        double held = Double.isFinite(targetAltitude) ? targetAltitude : s.y();
        return TerrainGuard.assess(p.ahead(s.x(), s.z()), s.y(), held, p.keelDrop(), p.speedAlong(s.vx(), s.vz()), feel,
                ceiling);
    }

    private boolean rudderOnly(Sense s) {
        return rudders > 0 && !(s.thrust().maxYaw() > 0.0);
    }

    private Command staffHold(Sense s, List<String> notes, Feel feel, double passive, double carriedMass, double ceiling) {
        targetAltitude = s.y();
        flown = targetAltitude;
        anchorX = Double.NaN;
        anchorZ = Double.NaN;
        heading.reset();
        cruise.clearIntegral();
        engine = 0.0;
        wasBraking = false;
        heldTicks = 0;
        heldFromY = Double.NaN;
        double[] phi = VerticalObserver.regressors(0.0, s.airspeed(), 0.0, s.pressure());
        double hidden = vertical.predict(phi) + passive + (carriedMass - s.mass()) * s.g();
        double dragRate = carriedMass > 0.0 ? vertical.estimate(VerticalObserver.DRAG) * s.pressure() / carriedMass : 0.0;
        AltitudeLaw.Plan plan = AltitudeLaw.plan(s.y(), 0.0, s.y(), carriedMass, s.g(), hidden, s.cells(), feel.altitude(),
                setup.trim, dragRate);
        notes.add("A physics staff holds the ship: the engines idle, the gas holds a hover and nothing is learned. Let go and it"
                + " stays where you put it.");
        double maxLift = AltitudeLaw.maxLift(s.cells(), s.g());
        double[] care = new double[Axis6.COUNT];
        care[Axis6.FORWARD.index()] = 1.0;
        care[Axis6.RIGHT.index()] = 1.0;
        care[Axis6.YAW.index()] = 1.0;
        return new Command(mode, false, plan.targets(), new double[Axis6.COUNT], care, targetAltitude, Double.NaN, Double.NaN,
                ceiling, maxLift > 0.0 ? plan.force() / maxLift : Double.NaN, plan.short_(), notes, telegraph.position(), 0.0,
                false, 0.0, astern ? -1.0 : 1.0, false);
    }

    public double loadMass(double g) {
        return g > 0.0 ? vertical.load() / g : 0.0;
    }

    private static boolean touching(Sense s) {
        return Double.isFinite(s.keelHeight()) && s.keelHeight() < AIRBORNE;
    }

    private boolean weighOff(Sense s, double passive, boolean touching) {
        double own = s.mass() * s.g();

        double counted = own + vertical.load();
        double lift = AltitudeLaw.lift(s.cells(), s.g()) + passive + Math.max(0.0, lastUp);
        double upNow = lift + Math.max(0.0, vertical.estimate(VerticalObserver.CONSTANT));
        boolean wantsUp = targetAltitude - s.y() > WEIGH_CLIMB;
        if (!touching || !wantsUp || !(own > 0.0) || s.vy() > WEIGH_STILL || upNow < (1.0 + WEIGH_GATE) * counted) {
            heldTicks = 0;
            heldFromY = Double.NaN;
            return false;
        }
        if (heldTicks == 0 || !Double.isFinite(heldFromY)) heldFromY = s.y();
        heldTicks++;

        if (s.y() - heldFromY > WEIGH_STILL) {
            heldTicks = 0;
            heldFromY = Double.NaN;
            return false;
        }
        if (heldTicks < WEIGH_TICKS) return false;
        vertical.atMost(own - lift - WEIGH_MARGIN * counted);
        return true;
    }

    private void takeHelm(Sense s, boolean grounded) {
        Mode was = mode;
        mode = Mode.HELM;
        heading.reset();
        heading.holdHeading(s.heading());
        cruise.clearIntegral();
        if (was == Mode.OFF || was == Mode.MOORED || grounded || !Double.isFinite(targetAltitude)) {
            targetAltitude = grounded ? s.y() + TAKEOFF_HOP : s.y();
            telegraph.set(0.0);
            engine = 0.0;

            if (grounded || touching(s)) vertical.dropLoad();
            heldTicks = 0;
        } else if (was == Mode.ANCHOR || was == Mode.GO_TO) {
            telegraph.set(0.0);
        }

        anchorX = Double.NaN;
        anchorZ = Double.NaN;
    }

    private void leaveHelm(Sense s, boolean grounded) {
        if (grounded) {
            mode = Mode.MOORED;
            return;
        }
        unpiloted(s.airForward());
    }

    private void unpiloted(double airForward) {
        anchorX = Double.NaN;
        anchorZ = Double.NaN;
        mode = modeFor(setup.leave);
        if (mode == Mode.GO_TO) speedOrder = Math.max(3.0, Double.isFinite(airForward) ? airForward : 0.0);
    }

    private static Mode modeFor(LeaveMode leave) {
        return switch (leave) {
            case ANCHOR -> Mode.ANCHOR;
            case KEEP_COURSE -> Mode.KEEP_COURSE;
            case GO_TO -> Mode.GO_TO;
        };
    }

    private boolean ground(Sense s) {
        boolean touching = Double.isFinite(s.keelHeight()) && s.keelHeight() < LAND_HEIGHT
                && Math.abs(s.vy()) < LAND_SPEED;
        groundTicks = touching ? Math.min(groundTicks + 1, LAND_TICKS) : 0;
        return groundTicks >= LAND_TICKS;
    }

    private void learn(Sense s) {
        Sense l = last;
        last = s;
        // sable only wakes a body on force change, a hovering ship falls asleep. asleep reads as hidden lift. sable tax #16

        frozen = l != null && s.vx() == l.vx() && s.vy() == l.vy() && s.vz() == l.vz() ? frozen + 1 : 0;

        if (l == null || lastHandsOff || !(l.mass() > 0.0) || frozen > 0 || sinceStaff < STAFF_SETTLE) return;
        boolean airborne = Double.isFinite(l.keelHeight()) ? l.keelHeight() > AIRBORNE : groundTicks == 0;
        if (!airborne || s.cells().size() != l.cells().size()) return;
        double ay = (s.vy() - l.vy()) / DT;

        double lift = 0.0;
        for (int i = 0; i < s.cells().size(); i++) {
            AltitudeLaw.Cell was = l.cells().get(i);
            double gas = 0.5 * (Math.max(0.0, was.gas()) + Math.max(0.0, s.cells().get(i).gas()));
            lift += gas * was.forcePerGas(l.g());
        }
        double passive = Double.isFinite(l.passiveLift()) ? l.passiveLift() : 0.0;
        double known = lift + passive + lastUp - l.mass() * l.g();

        double carried = l.mass() + loadMass(l.g());
        vertical.observe(carried * ay - known,
                VerticalObserver.regressors(l.vy(), l.airspeed(), lastForward, l.pressure()));
        cruise.observe(l.airForward(), (s.airForward() - l.airForward()) / DT, lastForward, carried, l.pressure());
        heading.observe(l.yawRate(), (s.yawRate() - l.yawRate()) / DT, lastYaw, l.yawInertia());
    }

    private double ceiling(Sense s, double hidden) {
        double cap = Double.isFinite(setup.altitudeCap) ? setup.altitudeCap : Double.POSITIVE_INFINITY;
        if (Double.isFinite(s.worldTop())) cap = Math.min(cap, s.worldTop() - WORLD_TOP_MARGIN);
        if (s.cells().isEmpty()) return Double.isFinite(cap) ? cap : Double.NaN;
        double c = AltitudeLaw.ceiling(s.mass(), s.g(), hidden, s.cells(), s.air());
        if (c == Double.NEGATIVE_INFINITY) return c;
        if (Double.isNaN(c)) return Double.isFinite(cap) ? cap : Double.NaN;
        return Math.min(c, cap);
    }

    public double balloonCeiling(Sense s) {
        if (s.cells().isEmpty()) return Double.NaN;
        double passive = Double.isFinite(s.passiveLift()) ? s.passiveLift() : 0.0;
        double hidden = vertical.predict(VerticalObserver.regressors(0.0, s.airspeed(), 0.0, s.pressure())) + passive;
        return AltitudeLaw.ceiling(s.mass(), s.g(), hidden, s.cells(), s.air());
    }

    public double topSpeed(Sense s) {
        double v = cruise.topSpeed(s.thrust().f0(), s.thrust().fade(), s.pressure());
        if (!Double.isFinite(v)) return DEFAULT_TOP_SPEED;
        return Math.max(1.0, Math.min(40.0, v));
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    public static String headline(String note) {
        if (note == null) return "";
        int colon = note.indexOf(": ");
        int bracket = note.indexOf(" (");
        int cut = colon < 0 ? bracket : bracket < 0 ? colon : Math.min(colon, bracket);
        return cut > 0 ? note.substring(0, cut) : note;
    }

    public State state() {
        return new State(mode.ordinal(), setup.leave.ordinal(), setup.feel.ordinal(), telegraph.position(),
                targetAltitude, heading.hold(), speedOrder, anchorX, anchorZ, vertical.state(), cruise.drag(),
                heading.damping());
    }

    public void restore(State st, boolean setupToo) {
        if (st == null) return;
        mode = Mode.byOrdinal(st.mode());
        if (setupToo) {
            setup.leave = LeaveMode.byOrdinal(st.leave());
            setup.feel = Feel.byOrdinal(st.feel());
        }
        telegraph.set(st.telegraph());
        targetAltitude = st.targetAltitude();
        heading.reset();
        heading.holdHeading(st.heading());
        speedOrder = Double.isFinite(st.speedOrder()) ? st.speedOrder() : 0.0;
        anchorX = st.anchorX();
        anchorZ = st.anchorZ();
        restoredVertical = st.vertical() == null ? null : st.vertical().clone();
        cruise.restore(st.drag());
        heading.restoreDamping(st.turnDrag());

        if (mode == Mode.HELM) mode = modeFor(setup.leave);
        initialised = false;
    }

    public void restore(State st) {
        restore(st, true);
    }
}
