package com.createstats.ground;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.createstats.aerial.Quat;
import com.createstats.aerial.V3;
import com.createstats.driverhelmet.TelemetryMath;
import com.createstats.ground.GroundSnapshot.Body;
import com.createstats.ground.GroundSnapshot.Drive;
import com.createstats.ground.GroundSnapshot.Governor;
import com.createstats.ground.GroundSnapshot.Identity;
import com.createstats.ground.GroundSnapshot.Jet;
import com.createstats.ground.GroundSnapshot.Lamp;
import com.createstats.ground.GroundSnapshot.Lights;
import com.createstats.ground.GroundSnapshot.Log;
import com.createstats.ground.GroundSnapshot.Machine;
import com.createstats.ground.GroundSnapshot.Mass;
import com.createstats.ground.GroundSnapshot.Setup;
import com.createstats.ground.GroundSnapshot.Shape;
import com.createstats.ground.GroundSnapshot.Supply;
import com.createstats.ground.GroundSnapshot.Terrain;
import com.createstats.ground.GroundSnapshot.Tie;
import com.createstats.ground.GroundSnapshot.Unit;
import com.createstats.ground.GroundSnapshot.Wheel;
import com.createstats.vccmk4.core.BodyRole;
import com.createstats.vccmk4.core.DriveModel;
import com.createstats.vccmk4.core.JetKind;
import com.createstats.vccmk4.core.JetSupply;
import com.createstats.vccmk4.core.LinkKind;
import com.createstats.vccmk4.core.LinkSource;
import com.createstats.vccmk4.core.SteerPreset;
import com.createstats.vccmk4.core.TrailerMode;
import com.createstats.vccmk4.core.WheelForce;

public final class GroundFixtures {
    public static final V3 ORIGIN = new V3(-418.0, 71.0, 1260.0);
    public static final UUID CAR_ID = UUID.fromString("1f0c3e7a-2b4d-4f61-9a55-0d7c2e9b4a11");
    static final double G = 11.0;
    static final double RADIUS = 0.5;

    private GroundFixtures() {
    }

    public static GroundSnapshot car() {
        final Car c = Car.hatch();
        final double v = cruise(c.driven, c.wheels, 128);
        return drive(c, v, 0.18, 1.0, false, 12_000L, 38.0).build();
    }

    public static GroundSnapshot parked() {
        final Car c = Car.hatch();
        final Scene s = drive(c, 0.0, 0.0, 0.0, false, 16_000L, 38.0);
        s.driving = false;
        s.parked = true;
        s.driver = "";
        s.gear = 'P';
        return s.build();
    }

    public static GroundSnapshot braking() {
        final Car c = Car.hatch();
        final Scene s = drive(c, 9.0, 0.35, 0.0, true, 12_400L, 52.0);
        s.icyWheel = 0;
        return s.build();
    }

    public static GroundSnapshot airborne() {
        final Car c = Car.hatch();
        final Scene s = drive(c, 12.0, 0.0, 1.0, false, 12_800L, 38.0);
        s.airborne = true;
        s.airNow = 0.65;
        return s.build();
    }

    public static GroundSnapshot impact() {
        final Car c = Car.hatch();
        final Scene s = drive(c, 0.4, 0.0, 0.0, true, 13_200L, 38.0);
        s.impact = 7.8;
        s.impactAge = 20;
        return s.build();
    }

    public static GroundSnapshot runaway() {
        final Car c = Car.hatch();
        final Scene s = drive(c, -3.2, 0.0, 0.0, false, 16_400L, 38.0);
        s.driving = false;
        s.driver = "";
        s.gear = '-';
        s.pitch = -9.0;
        return s.build();
    }

    public static GroundSnapshot roadTrain() {
        return roadTrain(false, false);
    }

    public static GroundSnapshot lostLink() {
        return roadTrain(true, false);
    }

    public static GroundSnapshot snapping() {
        return roadTrain(false, true);
    }

    static GroundSnapshot roadTrain(boolean lost, boolean springs) {
        final Car c = Car.tractor();
        final double v = 5.6;
        final Scene s = drive(c, v, 0.22, 0.8, false, 20_000L, 211.0);
        s.name = "Hauler 2";
        s.mass = 610.0;
        s.trailers.add(new TrailerSpec(1, 0, LinkKind.HITCH, 12.0, new double[]{-5.0, -6.4}, 2, 2.6, 380.0, Float.NaN,
                Float.NaN, 0, "B2"));
        final LinkKind second = springs ? LinkKind.SPRING : LinkKind.ROPE;
        final float reading = springs ? 5.1f : -0.04f;
        final float share = springs ? 0.92f : Float.NaN;
        int flags = springs ? Unit.FLAG_SNAPPING : 0;
        if (lost) {
            flags |= Unit.FLAG_LOST;
        }
        s.trailers.add(new TrailerSpec(2, 1, second, 20.0, new double[]{-4.6, -5.6}, 2, 2.2, 240.0, reading, share,
                flags, "B3"));
        if (lost) {
            s.alert = "LINK B3";
            s.problems.add("B3's rope has gone: its brakes are on until the console looks again.");
        } else if (springs) {
            s.alert = "STRAIN B3";
            s.problems.add("B3's spring is near breaking: ease off, or it snaps and drops.");
        }
        return s.build();
    }

    public static GroundSnapshot tank() {
        final Car c = Car.tank();
        final Scene s = drive(c, 0.2, 0.0, 0.0, false, 30_000L, 305.0);
        s.name = "Bulldog";
        s.preset = SteerPreset.SKID;
        s.yawRate = 0.62;
        s.steer = -0.8;
        s.leftRpm = -64;
        s.rightRpm = 64;
        s.mass = 740.0;
        return s.build();
    }

    public static GroundSnapshot forklift() {
        final Car c = Car.forklift();
        final Scene s = drive(c, -1.4, -0.4, -0.5, false, 40_000L, 90.0);
        s.name = "Lifty";
        s.preset = SteerPreset.REAR;
        s.gear = 'R';
        s.machines.add(new Machine("M1", 0, 0.0f, 2.2f, "block.create.mechanical_piston", false, 0, 0.8f, 48, false));
        return s.build();
    }

    public static GroundSnapshot articulated() {
        final Car c = Car.loaderFront();
        final Scene s = drive(c, 3.0, 0.5, 0.6, false, 50_000L, 120.0);
        s.name = "Loader";
        s.preset = SteerPreset.NONE;
        s.articulated = true;
        s.trailers.add(new TrailerSpec(1, 0, LinkKind.JOINT, 18.0, new double[]{-2.0, -3.0}, 1, 2.4, 300.0, Float.NaN,
                Float.NaN, Unit.FLAG_POWERED, "B2"));
        return s.build();
    }

    public static GroundSnapshot hovercraft() {
        final Car c = Car.hover();
        final Scene s = drive(c, 6.5, 0.2, 0.7, false, 60_000L, 0.0);
        s.name = "Skimmer";
        s.jetDrive = true;
        s.lift = 0.6;
        s.jets.add(new Jet("J1", 0, -1.5f, 0.2f, -3.0f, new V3(0, 0, 1), JetKind.PROPELLER, 0, 0.7f, 180f, 260f, 1f,
                JetSupply.NONE, true, false, 179f));
        s.jets.add(new Jet("J2", 0, 1.5f, 0.2f, -3.0f, new V3(0, 0, 1), JetKind.PROPELLER, 0, 0.7f, 180f, 260f, 1f,
                JetSupply.NONE, true, false, 179f));
        s.jets.add(new Jet("J3", 0, 0.0f, -0.4f, 0.0f, new V3(0, 1, 0), JetKind.PROPELLER, 2, 0.6f, 410f, 600f, 1f,
                JetSupply.NONE, true, false, 154f));
        s.jets.add(new Jet("J4", 0, 0.0f, 0.3f, 3.0f, new V3(1, 0, 0), JetKind.THRUSTER, 1, 0.0f, 0f, 120f, 1f,
                JetSupply.LOW, false, false, Float.NaN));
        return s.build();
    }

    public static GroundSnapshot crane() {
        final Car c = Car.truck(3);
        final Scene s = drive(c, 0.0, 0.0, 0.0, false, 70_000L, 180.0);
        s.name = "Crane 1";
        s.driving = false;
        s.parked = true;
        s.driver = "";
        s.gear = 'P';
        s.machines.add(new Machine("M1", 0, 0.0f, -1.0f, "block.create.rope_pulley", false, 4, 0.0f, 0, false));
        s.machines.add(new Machine("M2", 0, 0.0f, 0.5f, "block.create.mechanical_bearing", false, 5, 0.0f, 0, false));
        return s.build();
    }

    public static GroundSnapshot truck16() {
        final Car c = Car.truck(8);
        final double v = cruise(c.driven, c.wheels, 128);
        final Scene s = drive(c, v * 0.97, 0.06, 1.0, false, 80_000L, 90.0);
        s.name = "Big Rig";
        s.preset = SteerPreset.TWIN_FRONT;
        s.mass = 1450.0;
        return s.build();
    }

    public static GroundSnapshot towed() {
        final Car c = Car.hatch();
        final Scene s = drive(c, 5.5, 0.0, 0.0, false, 90_000L, 38.0);
        s.driving = false;
        s.driver = "";
        s.towed = true;
        s.towedBy = "Steve";
        s.gear = '-';
        return s.build();
    }

    public static GroundSnapshot realistic() {
        final Car c = Car.hatch();
        final Scene s = drive(c, 11.4, 0.0, 0.62, false, 7_400L, 12.0);
        s.name = "Car 3";
        s.mass = 164.0;
        s.recordedMinutes = 3;
        s.problems.clear();
        return s.build();
    }

    record WheelSpec(int unit, int axle, double x, double y, double z, boolean driven, boolean steered, int governor) {
    }

    record TrailerSpec(int index, int parent, LinkKind link, double articulation, double[] axles, int wheelsPerAxle,
                       double track, double mass, float reading, float snapShare, int flags, String name) {
    }

    static final class Car {
        final List<WheelSpec> wheels = new ArrayList<>();
        final List<double[]> boxes = new ArrayList<>();
        int driven;
        int governors = 1;
        double wheelbase;

        static Car hatch() {
            final Car c = new Car();
            c.axle(0, 2.0, 2.0, true, true, 0);
            c.axle(1, -2.0, 2.0, true, false, 0);
            c.box(-1.5, 1.5, -0.5, 0.5, -3.0, 3.0);
            c.box(-1.5, 1.5, 0.5, 1.5, -1.5, 1.5);
            c.box(-0.5, 0.5, 0.5, 1.0, 1.5, 2.5);
            c.wheelbase = 4.0;
            return c;
        }

        static Car tractor() {
            final Car c = new Car();
            c.axle(0, 3.0, 2.2, false, true, -1);
            c.axle(1, -1.5, 2.2, true, false, 0);
            c.axle(2, -3.0, 2.2, true, false, 0);
            c.box(-1.6, 1.6, -0.5, 0.5, -4.0, 4.5);
            c.box(-1.6, 1.6, 0.5, 2.5, 2.0, 4.5);
            c.box(-0.5, 0.5, 0.5, 1.0, -3.0, 1.5);
            c.wheelbase = 6.0;
            return c;
        }

        static Car tank() {
            final Car c = new Car();
            c.governors = 2;
            for (int a = 0; a < 4; a++) {
                final double z = 3.0 - a * 2.0;
                c.wheel(a, -2.2, z, true, false, 0);
                c.wheel(a, 2.2, z, true, false, 1);
            }
            c.box(-2.0, 2.0, -0.5, 1.0, -4.0, 4.0);
            c.box(-1.5, 1.5, 1.0, 2.0, -1.5, 1.5);

            c.box(-0.5, 0.5, 1.0, 2.0, 1.5, 5.5);
            c.wheelbase = 6.0;
            return c;
        }

        static Car forklift() {
            final Car c = new Car();
            c.axle(0, 1.5, 1.8, true, false, 0);
            c.axle(1, -1.5, 1.8, false, true, -1);
            c.box(-1.2, 1.2, -0.5, 0.5, -2.5, 2.0);
            c.box(-1.0, 1.0, 0.5, 2.5, -1.0, 1.0);
            c.box(-1.0, 1.0, -0.5, 3.5, 2.0, 2.5);
            c.wheelbase = 3.0;
            return c;
        }

        static Car loaderFront() {
            final Car c = new Car();
            c.axle(0, 0.5, 2.4, true, false, 0);
            c.box(-1.6, 1.6, -0.5, 1.0, -1.5, 2.0);
            c.box(-1.8, 1.8, -0.5, 0.5, 2.0, 3.5);
            c.wheelbase = 0.0;
            return c;
        }

        static Car hover() {
            final Car c = new Car();
            c.box(-2.0, 2.0, -0.5, 0.5, -3.5, 3.5);
            c.box(-1.2, 1.2, 0.5, 1.5, -1.0, 2.0);
            return c;
        }

        static Car truck(int axles) {
            final Car c = new Car();
            for (int a = 0; a < axles; a++) {
                final double z = 5.0 - a * (axles > 4 ? 1.6 : 3.0);
                final boolean steers = a == 0 || (axles >= 4 && a == 1);
                c.axle(a, z, 2.2, !steers, steers, steers ? -1 : 0);
            }
            final double back = 5.0 - (axles - 1) * (axles > 4 ? 1.6 : 3.0) - 1.5;
            c.box(-1.6, 1.6, -0.5, 0.5, back, 6.5);
            c.box(-1.6, 1.6, 0.5, 2.5, 4.0, 6.5);
            c.box(-1.6, 1.6, 0.5, 2.0, back, 3.5);
            c.wheelbase = 5.0 - back - 1.5;
            return c;
        }

        void axle(int axle, double z, double halfTrack, boolean driven, boolean steered, int governor) {
            wheel(axle, -halfTrack, z, driven, steered, governor);
            wheel(axle, halfTrack, z, driven, steered, governor);
        }

        void wheel(int axle, double x, double z, boolean driven, boolean steered, int governor) {
            this.wheels.add(new WheelSpec(0, axle, x, -0.6, z, driven, steered, driven ? governor : -1));
            if (driven) {
                this.driven++;
            }
        }

        void box(double x0, double x1, double y0, double y1, double z0, double z1) {
            this.boxes.add(new double[]{x0, x1, y0, y1, z0, z1});
        }

        Shape shape(int unit) {
            final List<V3> centres = new ArrayList<>();
            for (final double[] b : this.boxes) {
                for (double x = b[0] + 0.5; x < b[1]; x += 1.0) {
                    for (double y = b[2] + 0.5; y < b[3]; y += 1.0) {
                        for (double z = b[4] + 0.5; z < b[5]; z += 1.0) {
                            centres.add(new V3(x, y, z));
                        }
                    }
                }
            }
            return GroundSilhouette.of(unit, centres);
        }
    }

    static double cruise(int driven, List<WheelSpec> wheels, int maxRpm) {
        final double drive = driven * WheelForce.DRIVE_PER_SPEED * maxRpm;
        double drag = 0.0;
        for (int i = 0; i < wheels.size(); i++) {
            drag += WheelForce.dragPerSpeed(WheelForce.Wheel.typical(true), 0.0);
        }
        return drive / drag;
    }

    static Scene drive(Car car, double speed, double steer, double throttle, boolean braking, long tick, double heading) {
        final Scene s = new Scene();
        s.car = car;
        s.speed = speed;
        s.steer = steer;
        s.throttle = throttle;
        s.braking = braking;
        s.tick = tick;
        s.heading = heading;
        s.gear = braking ? 'B' : (throttle > 1.0e-6 ? 'D' : (throttle < -1.0e-6 ? 'R' : 'N'));
        return s;
    }

    static final class Scene {
        Car car;
        String name = "Rosie";
        String driver = "Bolat";
        double speed;
        double steer;
        double throttle;
        boolean braking;
        long tick;
        double heading;
        char gear;
        boolean driving = true;
        boolean parked;
        boolean towed;
        String towedBy = "";
        double mass = 180.0;
        SteerPreset preset = SteerPreset.FRONT;
        boolean articulated;
        boolean jetDrive;
        double lift;
        double yawRate = Double.NaN;
        double pitch;
        int icyWheel = -1;
        boolean airborne;
        double airNow;
        double impact;
        int impactAge = -1;
        double leftRpm = Double.NaN;
        double rightRpm = Double.NaN;
        String alert = "";
        int recordedMinutes = 6;
        final List<TrailerSpec> trailers = new ArrayList<>();
        final List<Jet> jets = new ArrayList<>();
        final List<Machine> machines = new ArrayList<>();
        final List<String> problems = new ArrayList<>();

        GroundSnapshot build() {
            final int maxRpm = 128;
            final DriveModel.Options options = DriveModel.Options.DEFAULT;
            final double lock = options.lockDeg();

            final double wheelbase = Math.max(1.0, this.car.wheelbase);
            final double lockRad = Math.toRadians(lock * Math.abs(this.steer));
            final double radius = Math.abs(this.steer) < 1.0e-6 || this.preset == SteerPreset.SKID
                    || this.preset == SteerPreset.NONE ? Double.POSITIVE_INFINITY : wheelbase / Math.tan(lockRad);
            final double yaw = Double.isFinite(this.yawRate) ? this.yawRate
                    : (Double.isFinite(radius) ? -Math.signum(this.steer) * this.speed / radius : 0.0);
            final double rearZ = rearAxleZ(this.car);

            final Quat q = orientation(this.heading, this.pitch);
            final V3 forward = q.rotate(Body.NORTH);
            final V3 right = forward.cross(V3.UP).normalized();
            final V3 velocity = forward.times(this.speed);

            final double lonA = this.braking ? -Math.min(6.0, Math.abs(this.speed) * 0.9) : this.throttle * 0.8;
            final V3 accel = right.times(-yaw * this.speed).plus(forward.times(lonA));
            final V3 position = ORIGIN.plus(new V3(0.0, 0.0, 0.0));

            final List<Wheel> wheels = new ArrayList<>();
            for (int i = 0; i < this.car.wheels.size(); i++) {
                final WheelSpec w = this.car.wheels.get(i);
                double angle = 0.0;
                if (w.steered() && Double.isFinite(radius)) {
                    final double side = Math.signum(this.steer);
                    final double across = radius - side * w.x();
                    angle = side * Math.toDegrees(Math.atan2(w.z() - rearZ, across));

                    angle = Math.round(angle / 2.0) * 2.0;
                } else if (w.steered() && this.preset == SteerPreset.REAR) {
                    angle = 0.0;
                }
                if (this.preset == SteerPreset.REAR && w.steered() && Double.isFinite(radius)) {
                    angle = -angle;
                }

                final double ground = Math.abs(this.speed + yaw * w.x());
                double applied = w.driven() ? this.throttle * maxRpm : 0.0;
                if (this.preset == SteerPreset.SKID && w.driven()) {
                    applied = w.x() < 0 ? this.leftRpm : this.rightRpm;
                }
                final int brake = this.braking || this.parked ? 15 : 0;
                final boolean lifted = this.airborne;
                final boolean icy = i == this.icyWheel;
                final double friction = icy ? 0.2 : 1.0;
                final double grip = TelemetryMath.grip(friction, lifted);
                final double free = TelemetryMath.freeRpm(applied, brake);
                final double rolling = TelemetryMath.rollingRpm(ground, RADIUS);
                final double actual = TelemetryMath.actualRpm(free, rolling, grip, lifted);
                final double slip = lifted ? Double.NaN : TelemetryMath.slip(actual, ground, RADIUS);

                final double extension = lifted ? 0.65 : 0.40 - 0.04 * Math.signum(yaw) * Math.signum(w.x());
                final double load = TelemetryMath.load(extension, RADIUS);
                wheels.add(new Wheel(w.unit(), w.axle(), w.governor(), (float) w.x(), (float) w.y(), (float) w.z(),
                        w.driven(), w.steered(), (float) RADIUS, 10f, 10f, (float) angle, (float) applied, (float) actual,
                        (float) slip, (float) grip, (float) load, lifted, brake));
            }

            final List<Governor> governors = new ArrayList<>();
            for (int g = 0; g < this.car.governors && this.car.driven > 0; g++) {
                int count = 0;
                for (final WheelSpec w : this.car.wheels) {
                    if (w.governor() == g) {
                        count++;
                    }
                }
                final double rpm = this.preset == SteerPreset.SKID ? (g == 0 ? this.leftRpm : this.rightRpm)
                        : this.throttle * maxRpm;
                governors.add(new Governor("G" + (g + 1), 0, (float) (g == 0 ? -0.5 : 0.5), (float) rearZ,
                        (int) Math.round(rpm), (int) Math.round(rpm), 1.0f, count, 0, false, Supply.OK, false));
            }
            for (final Machine m : this.machines) {
                governors.add(new Governor(m.name(), 0, m.x(), m.z(), m.applied(), m.applied(), Float.NaN, 0, 0, true,
                        Supply.OK, false));
            }

            final List<Unit> units = new ArrayList<>();
            final List<Shape> shapes = new ArrayList<>();
            final List<Tie> ties = new ArrayList<>();
            units.add(new Unit(0, -1, BodyRole.PART, LinkKind.JOINT, TrailerMode.OFF, true, false, 0f, 0f, 0f, Float.NaN,
                    0f, Float.NaN, Float.NaN, Float.NaN, 0, this.car.wheels.size(), (float) this.mass, "B1"));
            shapes.add(this.car.shape(0));

            final double[] tailZ = new double[8];
            final double[] yawOf = new double[8];
            final double[] atX = new double[8];
            final double[] atZ = new double[8];
            tailZ[0] = backOf(this.car);
            for (final TrailerSpec t : this.trailers) {
                final double parentYaw = yawOf[t.parent()];
                final double yawDeg = parentYaw + t.articulation();
                final double[] hitch = placed(t.parent(), atX, atZ, yawOf, 0.0, tailZ[t.parent()]);
                final double gap = t.link() == LinkKind.ROPE || t.link() == LinkKind.SPRING ? 3.0 : 0.0;
                final double front = 1.0 + gap;
                final double r = Math.toRadians(yawDeg);
                final double cx = hitch[0] - Math.sin(r) * (front + 3.0);
                final double cz = hitch[1] - Math.cos(r) * (front + 3.0);
                atX[t.index()] = cx;
                atZ[t.index()] = cz;
                yawOf[t.index()] = yawDeg;
                tailZ[t.index()] = -4.0;
                final Car body = new Car();
                for (int a = 0; a < t.axles().length; a++) {
                    for (int k = 0; k < t.wheelsPerAxle(); k++) {
                        final double x = t.wheelsPerAxle() == 1 ? 0.0 : (k == 0 ? -t.track() : t.track());
                        body.wheels.add(new WheelSpec(t.index(), a, x, -0.6, t.axles()[a] + 3.0, t.link() == LinkKind.JOINT,
                                false, -1));
                    }
                }
                body.box(-1.6, 1.6, -0.5, 0.5, -4.0, 4.0);
                if (t.link() != LinkKind.JOINT) {
                    body.box(-1.6, 1.6, 0.5, 2.5, -3.5, 3.0);
                }
                for (final WheelSpec w : body.wheels) {
                    final double ground = Math.abs(this.speed);
                    final double rolling = TelemetryMath.rollingRpm(ground, RADIUS);
                    final boolean lost = (t.flags() & Unit.FLAG_LOST) != 0;
                    wheels.add(new Wheel(w.unit(), w.axle(), -1, (float) w.x(), (float) w.y(), (float) w.z(), w.driven(),
                            true, (float) RADIUS, 10f, 10f, (float) (-t.articulation() * 0.35), 0f, (float) rolling,
                            0f, 1f, 0.35f, false, lost ? 15 : (this.braking ? 15 : 0)));
                }
                units.add(new Unit(t.index(), t.parent(), t.link() == LinkKind.JOINT ? BodyRole.PART : BodyRole.TRAILER,
                        t.link(), t.link() == LinkKind.JOINT ? TrailerMode.OFF : TrailerMode.ASSIST, true, false,
                        (float) cx, (float) cz, (float) yawDeg, (float) t.articulation(),
                        (float) (-Math.toDegrees(yaw) * 0.4), t.link() == LinkKind.HITCH ? (float) t.articulation() : t.reading(),
                        Float.NaN, t.snapShare(), t.flags(), body.wheels.size(), (float) t.mass(),
                        t.name()));
                shapes.add(body.shape(t.index()));
                if (t.link() == LinkKind.ROPE || t.link() == LinkKind.SPRING) {
                    for (final double off : new double[]{-1.0, 1.0}) {
                        final double[] a = placed(t.parent(), atX, atZ, yawOf, off, tailZ[t.parent()]);
                        final double[] b = placed(t.index(), atX, atZ, yawOf, off, 4.0);
                        ties.add(new Tie(t.link(), t.parent(), (float) a[0], (float) a[1], t.index(), (float) b[0],
                                (float) b[1], t.reading()));
                    }
                }
            }

            final List<Lamp> lamps = new ArrayList<>();
            final double back = backOf(this.car);
            final double nose = frontOf(this.car);
            lamps.add(new Lamp(0, -1.0f, 0.0f, (float) nose, 1, LinkSource.HEADLIGHT, this.driving ? 8 : 0, true));
            lamps.add(new Lamp(0, 1.0f, 0.0f, (float) nose, 1, LinkSource.HEADLIGHT, this.driving ? 8 : 0, true));
            lamps.add(new Lamp(0, -1.2f, 0.0f, (float) back, 2, LinkSource.TAIL_BRAKE,
                    this.driving ? (this.braking ? 15 : 6) : 0, true));
            lamps.add(new Lamp(0, 1.2f, 0.0f, (float) back, 2, LinkSource.TAIL_BRAKE,
                    this.driving ? (this.braking ? 15 : 6) : 0, true));
            lamps.add(new Lamp(0, -1.5f, 0.0f, (float) (nose - 0.5), 3, LinkSource.LEFT, 0, true));
            lamps.add(new Lamp(0, 1.5f, 0.0f, (float) (nose - 0.5), 3, LinkSource.RIGHT, this.steer > 0.25 ? 15 : 0, true));

            final int trailersTaking = (int) this.trailers.stream().filter(t -> t.link() != LinkKind.JOINT).count();
            int axles = 0;
            for (final WheelSpec w : this.car.wheels) {
                axles = Math.max(axles, w.axle() + 1);
            }
            final VehicleKind kind = VehicleKind.classify(axles, this.car.wheels.size(), this.jets.size(), this.preset,
                    this.articulated, this.jetDrive, trailersTaking, this.machines.size());
            final Identity identity = new Identity(this.name, CAR_ID, profile(this.car, this.preset, trailersTaking), kind,
                    GroundCommand.all(), true, true, this.towedBy);
            final Body body = new Body(position, q, Body.NORTH, velocity, yaw, this.speed, accel, new V3(0, -G, 0));
            final Log log = new Log(Math.max(0.35, Math.abs(yaw * this.speed) / G), this.impact, this.impactAge,
                    this.impact, this.airNow, this.airborne ? 0.0 : 0.8);
            final float[] controls = new float[12];
            controls[0] = (float) this.throttle;
            controls[1] = (float) this.steer;
            controls[2] = this.braking ? 1f : 0f;
            controls[4] = (float) this.lift;
            final Drive drive = new Drive(this.driver, this.driving ? this.tick - 1_800L : Long.MIN_VALUE, true, this.driving,
                    this.parked, this.towed, this.throttle, this.steer, this.braking || this.parked, false, false, this.gear,
                    lock, this.lift, this.alert, (int) this.machines.stream().filter(m -> m.output() != 0f).count(), controls);
            final Setup setup = new Setup(this.preset, true, this.articulated, this.jetDrive, options, axles, true);
            final Mass mass = new Mass(this.mass, this.mass + this.trailers.stream().mapToDouble(TrailerSpec::mass).sum(),
                    this.mass * 2.1, true, new V3(0.4, 0.6, 1.2), 46);
            final Lights lights = new Lights(this.driving, this.braking || this.parked, this.speed < -1.0, false,
                    (float) this.steer, false, lamps);
            final GroundSnapshot measured = new GroundSnapshot(this.tick, identity, body, log, drive, setup, mass, wheels,
                    governors, this.jets, units, ties, lights, this.machines, this.problems, terrain(position), null, null,
                    null, null, null, shapes);
            final GroundRecorder recorder = GroundDriveSim.lap(this.recordedMinutes, this.tick);
            return recorder.stamp(measured);
        }
    }

    static double[] placed(int unit, double[] atX, double[] atZ, double[] yawOf, double x, double z) {
        final double r = Math.toRadians(yawOf[unit]);
        return new double[]{atX[unit] + x * Math.cos(r) + z * Math.sin(r), atZ[unit] - x * Math.sin(r) + z * Math.cos(r)};
    }

    static double rearAxleZ(Car c) {
        double rear = Double.POSITIVE_INFINITY;
        boolean anyFixed = false;
        for (final WheelSpec w : c.wheels) {
            if (!w.steered()) {
                rear = Math.min(rear, w.z());
                anyFixed = true;
            }
        }
        if (!anyFixed) {
            for (final WheelSpec w : c.wheels) {
                rear = Math.min(rear, w.z());
            }
        }
        return Double.isFinite(rear) ? rear : 0.0;
    }

    static double backOf(Car c) {
        double back = 0.0;
        for (final double[] b : c.boxes) {
            back = Math.min(back, b[4]);
        }
        return back;
    }

    static double frontOf(Car c) {
        double front = 0.0;
        for (final double[] b : c.boxes) {
            front = Math.max(front, b[5]);
        }
        return front;
    }

    static String profile(Car c, SteerPreset preset, int trailers) {
        final String steer = switch (preset) {
            case FRONT -> "front steer";
            case REAR -> "rear steer";
            case TWIN_FRONT -> "twin front steer";
            case SKID -> "skid steer";
            case NONE -> "joint steer";
            default -> "custom steering";
        };
        final String base = c.wheels.isEmpty() ? "jets drive" : c.wheels.size() + "x" + c.driven + ", " + steer + " (auto)";
        return trailers > 0 ? base + ", + " + trailers + (trailers == 1 ? " trailer" : " trailers") : base;
    }

    static Quat orientation(double headingDeg, double pitchDeg) {
        final Quat yaw = Quat.axisAngle(V3.UP, -Math.toRadians(headingDeg));
        final Quat pitch = Quat.axisAngle(new V3(1.0, 0.0, 0.0), Math.toRadians(pitchDeg));
        return yaw.mul(pitch);
    }

    static Terrain terrain(V3 at) {
        final int size = 32;
        final double cell = 4.0;
        final double ox = Math.floor((at.x() - size * cell * 0.5) / cell) * cell;
        final double oz = Math.floor((at.z() - size * cell * 0.5) / cell) * cell;
        final short[] h = new short[size * size];
        for (int iz = 0; iz < size; iz++) {
            for (int ix = 0; ix < size; ix++) {
                final double x = ox + (ix + 0.5) * cell - at.x();
                final double z = oz + (iz + 0.5) * cell - at.z();
                final double ridge = 14.0 * Math.exp(-((x - 40.0) * (x - 40.0)) / 900.0);
                final double roll = 3.0 * Math.sin(z / 18.0) + 2.0 * Math.cos(x / 23.0);
                h[iz * size + ix] = (short) Math.round(at.y() - 1.0 + ridge + roll);
            }
        }
        return new Terrain(ox, oz, cell, size, h, at.y() - 1.0);
    }

    public static List<Named> all() {
        return List.of(new Named("car", car()), new Named("parked", parked()), new Named("braking", braking()),
                new Named("airborne", airborne()), new Named("impact", impact()), new Named("runaway", runaway()),
                new Named("road train", roadTrain()), new Named("lost link", lostLink()),
                new Named("snapping", snapping()), new Named("tank", tank()), new Named("forklift", forklift()),
                new Named("articulated", articulated()), new Named("hovercraft", hovercraft()),
                new Named("crane", crane()), new Named("truck16", truck16()), new Named("towed", towed()),
                new Named("realistic", realistic()), new Named("empty", GroundSnapshot.empty(100L)));
    }

    public record Named(String name, GroundSnapshot snapshot) {
    }
}
