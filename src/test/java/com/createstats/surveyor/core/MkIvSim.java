package com.createstats.surveyor.core;

import com.createstats.vccmk4.core.AxleLayout;
import com.createstats.vccmk4.core.AxleRole;
import com.createstats.vccmk4.core.DriveModel;
import com.createstats.vccmk4.core.GroundSteer;
import com.createstats.vccmk4.core.RouteDrive;
import com.createstats.vccmk4.core.SteerPreset;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

final class MkIvSim {
    static final double DT = 0.05;

    static final double G = 11.0;

    final double k;
    final double tau;

    final double beta;
    final DriveModel.Options options;
    final List<AxleLayout.WheelSpec> wheels = new ArrayList<>();
    final List<AxleLayout.Axle> axles;
    final AxleRole[] roles;
    final boolean[] driven;
    final SteerPreset preset;

    final double pivot;
    final double track;

    double x;
    double y = 64.0;
    double z;

    double theta;
    double v;
    double yawRate;
    final double[] mountYaw;
    final DriveModel drive = new DriveModel();
    DriveModel.Output out = DriveModel.Output.idle(30.0);
    int governorRpm;
    int[] sideRpm = new int[2];

    int brakeSignal;
    long tick;
    double lastThrottle;
    boolean lastBrake;
    double lastSteer;

    double wheelWant;
    double wheelSent;

    double accel;

    interface Terrain {
        double height(double x, double z);
    }

    Terrain terrain = (px, pz) -> Double.NaN;

    double headingNoiseDeg;
    double speedNoise;
    private final Random random = new Random(20260929L);

    double measuredSpeed;
    double measuredYawRate;
    private double lastMeasuredHeading = Double.NaN;

    Trailer trailer;

    interface Wall {
        boolean blocks(MkIvSim sim);
    }

    Wall wall = sim -> false;

    static final class Trailer {
        final double massRatio;

        final double rope;
        final double stiffness;
        final double damping;

        double behind;
        double v;

        double lastTractorAccel;
        double tension;

        Trailer(double massRatio, double rope) {
            this.massRatio = massRatio;
            this.rope = rope;
            this.stiffness = 40.0;
            this.damping = 4.0;
            this.behind = rope;
        }
    }

    private MkIvSim(double k, double tau, double beta, double wheelbase, double halfTrack, SteerPreset preset,
                    int axlesCount, DriveModel.Options options) {
        this.k = k;
        this.tau = tau;
        this.beta = beta;
        this.preset = preset;
        this.track = halfTrack * 2.0;
        this.options = options;
        double front = wheelbase / 2.0;
        for (int a = 0; a < axlesCount; a++) {
            double along = axlesCount == 1 ? 0.0 : front - a * (wheelbase / (axlesCount - 1));
            wheels.add(new AxleLayout.WheelSpec(-halfTrack, along));
            wheels.add(new AxleLayout.WheelSpec(halfTrack, along));
        }
        this.axles = AxleLayout.cluster(wheels);
        this.roles = preset.roles(axles.size());
        this.driven = new boolean[axles.size()];
        java.util.Arrays.fill(driven, true);
        this.pivot = GroundSteer.pivot(axles, roles);
        this.mountYaw = new double[wheels.size()];
        this.out = DriveModel.Output.idle(options.lockDeg());
    }

    static MkIvSim car() {
        return new MkIvSim(12.0, 1.6, 4.0, 3.0, 1.0, SteerPreset.FRONT, 2, DriveModel.Options.DEFAULT);
    }

    static MkIvSim truck() {
        return new MkIvSim(10.0, 2.4, 4.0, 5.0, 1.5, SteerPreset.FRONT, 3, DriveModel.Options.DEFAULT);
    }

    static MkIvSim tank() {
        return new MkIvSim(8.0, 2.0, 4.0, 4.0, 1.5, SteerPreset.SKID, 3, DriveModel.Options.DEFAULT);
    }

    static final DriveModel.Options OWNER = new DriveModel.Options(true, true, 20, 30, 256, 50, 60,
            DriveModel.Options.AS_RAMP, true, true);

    static MkIvSim carrier() {
        MkIvSim sim = new MkIvSim(12.03, 0.665, 0.665 / 0.127 - 1.0, 5.0, 2.0, SteerPreset.ALL, 2, OWNER);
        sim.trailer = new Trailer(1.8, 3.0);
        return sim;
    }

    static MkIvSim carrierHeavy() {
        MkIvSim sim = new MkIvSim(11.18, 1.66, 1.66 / 0.32 - 1.0, 5.0, 2.0, SteerPreset.ALL, 2, OWNER);
        sim.trailer = new Trailer(1.8, 3.0);
        return sim;
    }

    static MkIvSim of(SteerPreset preset, int axlesCount, double wheelbase, double halfTrack, DriveModel.Options options,
                      double k, double tau, boolean trailer) {
        MkIvSim sim = new MkIvSim(k, tau, 4.2, wheelbase, halfTrack, preset, axlesCount, options);
        if (trailer) sim.trailer = new Trailer(1.8, 3.0);
        return sim;
    }

    MkIvSim at(double px, double pz, double headingDeg) {
        x = px;
        z = pz;
        theta = Math.toRadians(headingDeg);
        return this;
    }

    MkIvSim atY(double py) {
        y = py;
        return this;
    }

    MkIvSim noisy(double headingDeg, double speed) {
        headingNoiseDeg = headingDeg;
        speedNoise = speed;
        return this;
    }

    double hx() {
        return Math.sin(theta);
    }

    double hz() {
        return -Math.cos(theta);
    }

    double kappaPerStick() {
        if (preset == SteerPreset.SKID) return 0.0;
        double[] c = GroundSteer.turnCentre(wheels, axles, roles, preset, 1.0, lockAt(v));
        return c == null ? 0.0 : 1.0 / Math.abs(c[0]);
    }

    double lockAt(double speed) {
        DriveModel.Options o = options.sane();
        double lock = o.lockDeg();
        if (o.speedSteer()) {
            double f = 1.0 - (Math.abs(speed) - DriveModel.SPEED_STEER_FROM) / (DriveModel.SPEED_STEER_TO - DriveModel.SPEED_STEER_FROM);
            lock *= Math.max(DriveModel.SPEED_STEER_FLOOR, Math.min(1.0, f));
        }
        return lock;
    }

    double minRadius() {
        if (preset == SteerPreset.SKID) return SegmentCheck.DEFAULT_RADIUS;
        double[] c = GroundSteer.turnCentre(wheels, axles, roles, preset, 1.0, options.sane().lockDeg());
        return c == null ? SegmentCheck.DEFAULT_RADIUS : Math.abs(c[0]);
    }

    RouteRunner.Sensors sensors(double obstacle, boolean player, double gap, double speedAhead, boolean departOk) {
        return sensors(obstacle, player, false, gap, speedAhead, departOk, Double.POSITIVE_INFINITY, "");
    }

    RouteRunner.Sensors sensors(double obstacle, boolean player, boolean block, double gap, double speedAhead,
                                boolean departOk, double hazard, String hazardWhat) {
        double pitch = Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, -slopeAccel() / G))));
        String what = player ? "a player" : (block ? "stone at the line" : "");
        return new RouteRunner.Sensors(tick, x, y, z, hx(), hz(), measured(), kappaPerStick(), preset == SteerPreset.SKID,
                obstacle, player, what, gap, speedAhead, departOk, false, false, true, lastThrottle,
                lastBrake, lastSteer, measuredYaw(), pitch, 0.0, brakeSignal / 15.0,
                governorRpm / (double) options.maxRpm(), block, hazard, hazardWhat);
    }

    boolean continuous = true;
    final RouteDrive routeDrive = new RouteDrive();

    void step(RouteRunner.Command c) {
        if (!c.drive()) {
            routeDrive.reset();
            out = DriveModel.Output.idle(lockAt(v));
            apply(0.0, 0.0, 1.0, lockAt(v));
            return;
        }
        boolean hold = c.brakeLevel() >= 1.0 && c.throttle() == 0.0;
        out = routeDrive.step(options, c.throttle(), c.steer(), c.brakeLevel(), v, hold);
        apply(out.throttle(), out.steer(), out.brake(), out.lockDeg());
    }

    private double measured() {
        return tick == 0 ? v : measuredSpeed;
    }

    private double measuredYaw() {
        return tick == 0 ? 0.0 : measuredYawRate;
    }

    void stepKeys(RouteRunner.Command c) {
        double throttle = c.drive() ? c.throttle() : 0.0;
        double steer = c.drive() ? c.steer() : 0.0;
        boolean brake = !c.drive() || c.brake();
        out = drive.step(options, new DriveModel.Inputs(throttle, steer, brake, false, v, false, true));
        apply(out.throttle(), out.steer(), out.braking() ? 1.0 : 0.0, out.lockDeg());
    }

    void apply(DriveModel.Output o, double brakeLevel) {
        out = o;
        apply(o.throttle(), o.steer(), brakeLevel, o.lockDeg());
    }

    private void apply(double throttle, double steer, double brakeLevel, double lockDeg) {
        tick++;
        lastThrottle = throttle;
        lastBrake = brakeLevel > 0.0;
        lastSteer = steer;

        List<GroundSteer.Command> cmds = GroundSteer.solve(wheels, axles, roles, driven, preset, steer, throttle, lockDeg);

        if (tick % 2 == 0) {
            if (preset == SteerPreset.SKID) {
                double left = 0;
                double right = 0;
                for (int i = 0; i < cmds.size(); i++) {
                    if (wheels.get(i).lateral() < 0) left = cmds.get(i).drive();
                    else right = cmds.get(i).drive();
                }
                sideRpm[0] = (int) Math.round(left * options.maxRpm());
                sideRpm[1] = (int) Math.round(right * options.maxRpm());
            } else {
                double maxDrive = 0;
                for (GroundSteer.Command cmd : cmds) maxDrive = Math.max(maxDrive, cmd.drive());
                governorRpm = (int) Math.round(maxDrive * options.maxRpm());
            }
        }
        brakeSignal = (int) Math.max(0, Math.min(15, Math.round(brakeLevel * 15.0)));
        double b = brakeSignal / 15.0;

        double kappaSum = 0.0;
        int kappaN = 0;
        wheelWant = Double.NaN;
        wheelSent = Double.NaN;
        for (int i = 0; i < wheels.size(); i++) {
            double target = cmds.get(i).angleDeg();
            int steps = GroundSteer.signalSteps(target);
            double quantized = continuous ? Math.max(-GroundSteer.MOUNT_LOCK_DEG, Math.min(GroundSteer.MOUNT_LOCK_DEG, target))
                    : Math.signum(target) * steps * GroundSteer.STEP_DEG;
            if (Double.isNaN(wheelWant) && roles[axleOf(i)] == AxleRole.STEER) {
                wheelWant = target;
                wheelSent = quantized;
            }
            mountYaw[i] += (quantized - mountYaw[i]) * 0.4;
            AxleLayout.WheelSpec w = wheels.get(i);
            double along = w.longitudinal() - pivot;
            if (Math.abs(along) > 1.0e-6 && Math.abs(mountYaw[i]) > 1.0e-6) {
                double r = along / Math.tan(Math.toRadians(mountYaw[i])) + w.lateral();
                kappaSum += Math.abs(r) < 1.0e-6 ? 0.0 : 1.0 / r;
                kappaN++;
            } else if (roles[axleOf(i)] == AxleRole.STEER) {
                kappaN++;
            }
        }
        double kappa = kappaN == 0 ? 0.0 : kappaSum / kappaN;

        double u = preset == SteerPreset.SKID ? (sideRpm[0] + sideRpm[1]) * 0.5 / options.maxRpm() : governorRpm / (double) options.maxRpm();
        double slope = slopeAccel();
        double rope = 0.0;
        if (trailer != null) {
            double stretch = trailer.behind - trailer.rope;
            trailer.tension = stretch > 0.0 ? trailer.stiffness * stretch + trailer.damping * Math.max(0.0, v - trailer.v) : 0.0;
            rope = -trailer.tension;
        }
        double drag = 1.0 + beta * b;
        double tauEff = tau / drag;
        double vInf = (k * u * (1.0 - b)) / drag + (slope + rope) * tauEff;
        double v0 = v;
        v = vInf + (v - vInf) * Math.exp(-DT / tauEff);
        accel = (v - v0) / DT;
        if (trailer != null) {
            double own = accel - rope;
            double assist = trailer.lastTractorAccel * (trailer.lastTractorAccel < 0.0 ? 1.1 : 1.0);
            double gap = trailer.behind - trailer.rope;
            double trim = Math.max(-6.0, Math.min(6.0, 4.0 * gap + 3.2 * (v0 - trailer.v)));
            double a2 = assist + trim + trailer.tension / trailer.massRatio;
            trailer.v += a2 * DT;
            trailer.behind += (v0 - trailer.v) * DT;
            trailer.lastTractorAccel = own;
        }
        if (preset == SteerPreset.SKID) {
            double vl = k * sideRpm[0] / options.maxRpm();
            double vr = k * sideRpm[1] / options.maxRpm();
            yawRate += ((vl - vr) / track - yawRate) * (DT / (tau * 0.5));
        } else {
            yawRate = v * kappa;
        }
        double dx = v * Math.sin(theta) * DT;
        double dz = -v * Math.cos(theta) * DT;
        x += dx;
        z += dz;
        if (v > 0.0 && wall.blocks(this)) {
            x -= dx;
            z -= dz;
            v = 0.0;
            accel = (v - v0) / DT;
            yawRate = 0.0;
        }
        theta += yawRate * DT;
        double h = terrain.height(x, z);
        if (!Double.isNaN(h)) y = h;

        double measuredHeading = theta + Math.toRadians(headingNoiseDeg) * random.nextGaussian();
        measuredYawRate = Double.isNaN(lastMeasuredHeading) ? yawRate
                : PathSpline.wrapAngle(measuredHeading - lastMeasuredHeading) / DT;
        lastMeasuredHeading = measuredHeading;
        measuredSpeed = v + speedNoise * random.nextGaussian();
    }

    private double slopeAccel() {
        double ahead = terrain.height(x + Math.sin(theta), z - Math.cos(theta));
        double behind = terrain.height(x - Math.sin(theta), z + Math.cos(theta));
        if (Double.isNaN(ahead) || Double.isNaN(behind)) return 0.0;
        double grade = (ahead - behind) / 2.0;
        return -G * grade / Math.sqrt(1.0 + grade * grade);
    }

    private int axleOf(int wheel) {
        int[] of = AxleLayout.axleOf(wheels.size(), axles);
        return of[wheel] < 0 ? 0 : of[wheel];
    }

    void traceInto(RouteTrace t) {
        t.put(RouteTrace.Ch.X, x);
        t.put(RouteTrace.Ch.Z, z);
        t.put(RouteTrace.Ch.SPEED, v);
        t.put(RouteTrace.Ch.ACCEL, accel);
        t.put(RouteTrace.Ch.YAW_RAW, measuredYawRate);
        t.put(RouteTrace.Ch.OUT_THROTTLE, lastThrottle);
        t.put(RouteTrace.Ch.OUT_BRAKE, brakeSignal / 15.0);
        t.put(RouteTrace.Ch.OUT_STEER, lastSteer);
        t.put(RouteTrace.Ch.WHEEL_WANT, wheelWant);
        t.put(RouteTrace.Ch.WHEEL_SENT, wheelSent);
    }
}
