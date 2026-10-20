package com.createstats.ground;

import java.util.List;

import com.createstats.aerial.V3;
import com.createstats.driverhelmet.LedBar;
import com.createstats.driverhelmet.TelemetryMath;
import com.createstats.ground.DriveReadout.LinkState;
import com.createstats.ground.DriveReadout.Model;
import com.createstats.ground.DriveReadout.UnitRead;
import com.createstats.ground.DriveReadout.WheelRead;
import com.createstats.ground.GroundSnapshot.Governor;
import com.createstats.ground.GroundSnapshot.Jet;
import com.createstats.ground.GroundSnapshot.Unit;
import com.createstats.ground.GroundSnapshot.Wheel;
import com.createstats.vccmk4.core.DriveModel;
import com.createstats.vccmk4.core.JetSupply;
import com.createstats.vccmk4.core.LinkKind;
import com.createstats.vccmk4.core.LinkLogic;
import com.createstats.vccmk4.core.LinkTrim;
import com.createstats.vccmk4.core.SteerPreset;
import com.createstats.vccmk4.core.WheelForce;

public final class DriveMath {
    public static final double KMH = 3.6;

    public static final double MOVING = 0.3;

    public static final double RUNAWAY_SPEED = 1.0;

    static final double MIN_TURN = 0.02;

    static final double STRAIGHT = 0.5;

    public static final double GOVERNOR_MAX = 256.0;

    static final double TAUT = 0.1;

    private DriveMath() {
    }

    public static DriveReadout read(GroundSnapshot s) {
        final DriveReadout r = new DriveReadout(s);
        frame(s, r);
        motion(s, r);
        wheels(s, r);
        steering(s, r);
        powertrain(s, r);
        jets(s, r);
        train(s, r);
        stability(r);
        state(s, r);
        return r;
    }

    static void frame(GroundSnapshot s, DriveReadout r) {
        final GroundSnapshot.Body b = s.body();
        final V3 f = b.orientation().rotate(b.nose()).normalized();
        final V3 u = b.orientation().rotate(V3.UP).normalized();
        r.forward = f.length() < 0.5 ? new V3(0.0, 0.0, -1.0) : f;
        r.up = u.length() < 0.5 ? V3.UP : u;
        r.right = r.forward.cross(r.up).normalized();
        r.heading = wrap360(Math.toDegrees(Math.atan2(r.forward.x(), -r.forward.z())));
        final double[] fw = arr(r.forward);
        r.pitch = TelemetryMath.pitch(fw);
        r.roll = TelemetryMath.roll(fw, arr(r.up));
        r.grade = Math.tan(Math.toRadians(clamp(r.pitch, -89.0, 89.0))) * 100.0;
    }

    static void motion(GroundSnapshot s, DriveReadout r) {
        final GroundSnapshot.Body b = s.body();
        final V3 v = b.velocity();
        r.speed = Double.isFinite(b.forwardSpeed()) ? b.forwardSpeed() : v.dot(r.forward);
        r.speedKmh = Math.abs(r.speed) * KMH;
        r.groundSpeed = v.horizontalLength();
        r.verticalSpeed = v.y();
        r.sideSpeed = v.dot(r.right);
        if (r.groundSpeed >= MOVING) {
            r.track = wrap360(Math.toDegrees(Math.atan2(v.x(), -v.z())));
            r.drift = Math.toDegrees(Math.atan2(r.sideSpeed, Math.max(1.0e-6, Math.abs(v.dot(r.forward)))));
        }
        final double yaw = Double.isFinite(b.yawRate()) ? b.yawRate() : 0.0;
        r.turnRate = -Math.toDegrees(yaw);
        if (Math.abs(yaw) >= MIN_TURN && r.groundSpeed >= RUNAWAY_SPEED) {
            r.turnRadius = r.groundSpeed / Math.abs(yaw);
        }
        r.altitude = b.position().y();
        r.groundY = s.terrain().groundY();
        final double g = gravity(s);
        final double[] a = arr(b.accel());
        final double[] lateralLongitudinal = TelemetryMath.gForce(a, arr(r.forward), arr(r.up), g);
        r.latG = lateralLongitudinal[0];
        r.lonG = lateralLongitudinal[1];
        r.vertG = TelemetryMath.verticalG(a, arr(r.up), arr(b.gravity()));
        r.combinedG = Math.hypot(r.latG, r.lonG);
    }

    public static double gravity(GroundSnapshot s) {
        final double g = s.body().gravity().length();
        return g > 1.0e-6 ? g : GroundSnapshot.Body.STANDARD_GRAVITY.length();
    }

    static void wheels(GroundSnapshot s, DriveReadout r) {
        final double throttle = s.drive().throttle();
        final boolean braking = s.drive().braking();
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        int tyres = 0;
        for (final Wheel w : s.wheels()) {
            final boolean touching = !w.airborne();
            final int side = w.x() < -0.25f ? -1 : (w.x() > 0.25f ? 1 : 0);
            final double surface = Float.isFinite(w.actual()) && Float.isFinite(w.radius())
                    ? TelemetryMath.surfaceSpeed(w.actual(), w.radius()) : Double.NaN;
            final boolean spin = w.driven() && touching && Float.isFinite(w.slip()) && w.slip() > LedBar.WARN_SLIP
                    && Math.abs(throttle) > 0.05;
            final boolean lock = touching && Float.isFinite(w.slip()) && w.slip() < -LedBar.WARN_SLIP && braking;
            r.wheels.add(new WheelRead(w, side, surface, touching, spin, lock));
            r.spinning |= spin;
            r.locking |= lock;
            if (Float.isFinite(w.radius())) {
                tyres++;
            }
            if (touching) {
                r.touching++;
                if (Float.isFinite(w.grip())) {
                    r.leastGrip = Double.isNaN(r.leastGrip) ? w.grip() : Math.min(r.leastGrip, w.grip());
                }
                if (w.driven() && Float.isFinite(w.slip())
                        && (Double.isNaN(r.worstSlip) || Math.abs(w.slip()) > Math.abs(r.worstSlip))) {
                    r.worstSlip = w.slip();
                }
            } else {
                r.lifted++;
            }
            if (w.unit() == 0) {
                minX = Math.min(minX, w.x());
                maxX = Math.max(maxX, w.x());
                minZ = Math.min(minZ, w.z());
                maxZ = Math.max(maxZ, w.z());
            }
        }
        r.airborne = tyres > 0 && r.touching == 0 && r.lifted > 0;
        if (Double.isFinite(minX) && maxX > minX) {
            r.trackWidth = maxX - minX;
        }
        if (Double.isFinite(minZ) && maxZ > minZ) {
            r.wheelbase = maxZ - minZ;
        }
    }

    static void steering(GroundSnapshot s, DriveReadout r) {
        final List<Wheel> mine = unitWheels(s, 0);
        final SteerPreset preset = s.setup().preset();
        if (preset == SteerPreset.CRAB) {
            double sum = 0.0;
            int n = 0;
            for (final Wheel w : mine) {
                if (w.steered()) {
                    sum += w.angle();
                    n++;
                }
            }
            r.crabAngle = n > 0 ? sum / n : Double.NaN;
        } else if (preset != SteerPreset.SKID) {
            final double[] c = turnCentre(mine);
            if (c != null) {
                r.centreX = c[0];
                r.centreZ = c[1];
                r.pathRadius = Math.hypot(c[0], c[1]);
                double kerb = 0.0;
                for (final Wheel w : mine) {
                    kerb = Math.max(kerb, Math.hypot(w.x() - c[0], w.z() - c[1]));
                }
                r.kerbRadius = kerb;
            }
        }

        if (Double.isFinite(r.trackWidth)) {
            final double yaw = Double.isFinite(s.body().yawRate()) ? s.body().yawRate() : 0.0;
            r.leftSpeed = r.speed - yaw * r.trackWidth * 0.5;
            r.rightSpeed = r.speed + yaw * r.trackWidth * 0.5;
        }
        double left = 0.0;
        int nl = 0;
        double right = 0.0;
        int nr = 0;
        for (final Wheel w : mine) {
            if (!w.driven() || !Float.isFinite(w.applied())) {
                continue;
            }
            if (w.x() < -0.25f) {
                left += w.applied();
                nl++;
            } else if (w.x() > 0.25f) {
                right += w.applied();
                nr++;
            }
        }
        r.leftRpm = nl > 0 ? left / nl : Double.NaN;
        r.rightRpm = nr > 0 ? right / nr : Double.NaN;
        if (s.setup().articulated()) {
            for (final Unit u : s.units()) {
                if (u.parent() == 0 && u.link() == LinkKind.JOINT) {
                    r.jointAngle = u.articulation();
                    break;
                }
            }
        }
    }

    public static double[] turnCentre(List<Wheel> wheels) {
        boolean steered = false;
        for (final Wheel w : wheels) {
            steered |= w.steered() && Math.abs(w.angle()) >= STRAIGHT;
        }
        if (!steered) {
            return null;
        }

        double a = 0.0;
        double bb = 0.0;
        double c = 0.0;
        double bx = 0.0;
        double bz = 0.0;
        for (final Wheel w : wheels) {
            final double th = Math.toRadians(w.steered() ? w.angle() : 0.0);
            final double dx = Math.sin(th);
            final double dz = Math.cos(th);
            a += dx * dx;
            bb += dx * dz;
            c += dz * dz;
            final double proj = dx * w.x() + dz * w.z();
            bx += dx * proj;
            bz += dz * proj;
        }
        final double det = a * c - bb * bb;
        if (Math.abs(det) < 1.0e-6) {
            return null;
        }
        final double px = (c * bx - bb * bz) / det;
        final double pz = (a * bz - bb * bx) / det;
        if (!Double.isFinite(px) || !Double.isFinite(pz) || Math.hypot(px, pz) > 10_000.0) {
            return null;
        }
        return new double[]{px, pz};
    }

    static void powertrain(GroundSnapshot s, DriveReadout r) {
        for (final Governor g : s.governors()) {
            if (g.machine()) {
                continue;
            }
            switch (g.supply()) {
                case OK -> r.engines++;
                case NO_ROTATION -> r.noRotation++;
                case OVERSTRESSED -> r.overstressed++;
                case FOREIGN -> r.foreign++;
                default -> {
                }
            }
            if (g.starved()) {
                r.starved++;
            }
        }
        r.model = model(s, r.pitch, r.speed);
    }

    public static Model model(GroundSnapshot s, double pitchDeg, double speed) {
        final List<Wheel> mine = unitWheels(s, 0);
        final DriveModel.Options o = s.setup().options();
        double drive = 0.0;
        double drag = 0.0;
        double brake = 0.0;
        double mountMax = 0.0;
        boolean anyDriven = false;
        for (final Wheel w : mine) {
            final WheelForce.Wheel law = new WheelForce.Wheel(w.driven(), 1.0,
                    Float.isFinite(w.capMass()) && w.capMass() > 0f ? w.capMass() : WheelForce.DEFAULT_STRENGTH);
            drag += WheelForce.dragPerSpeed(law, 0.0);
            brake += WheelForce.dragPerSpeed(law, 1.0);
            if (!w.driven()) {
                continue;
            }
            final double mount = mountMax(s, w, o.maxRpm());
            drive += WheelForce.drive(mount, 0.0, 1.0);
            mountMax = Math.max(mountMax, mount);
            anyDriven = true;
        }
        if (!anyDriven || !(drag > 0.0)) {
            return Model.NONE;
        }
        final double cruise = drive / drag;
        final double reverse = cruise * o.reversePercent() / 100.0;
        final double m = s.mass().mass();
        final boolean massKnown = Double.isFinite(m) && m > 0.0;
        final double tau = massKnown ? m / drag : Double.NaN;
        final double stopTau = massKnown ? m / brake : Double.NaN;
        final double weight = massKnown ? m * gravity(s) : Double.NaN;
        final double hill = massKnown ? (drive >= weight ? 90.0 : Math.toDegrees(Math.asin(drive / weight))) : Double.NaN;
        final double slope = Math.sin(Math.toRadians(clamp(Double.isFinite(pitchDeg) ? pitchDeg : 0.0, -89.0, 89.0)));
        final double here = massKnown ? Math.max(0.0, (drive - weight * slope) / drag) : Double.NaN;
        final double stop = massKnown ? stopDistance(Math.abs(speed), stopTau) : Double.NaN;
        return new Model(drive, drag, brake, mountMax, cruise, reverse, tau, tau * Math.log(10.0), stopTau, hill,
                here, stop);
    }

    static double mountMax(GroundSnapshot s, Wheel w, int maxRpm) {
        final int g = w.governor();
        if (g >= 0 && g < s.governors().size()) {
            final float ratio = s.governors().get(g).ratio();
            if (Float.isFinite(ratio) && Math.abs(ratio) > 1.0e-3) {
                return Math.min(maxRpm, GOVERNOR_MAX * Math.abs(ratio));
            }
        }
        return maxRpm;
    }

    public static double stopDistance(double speed, double stopTau) {
        if (!Double.isFinite(stopTau) || !Double.isFinite(speed)) {
            return Double.NaN;
        }
        return stopTau * Math.max(0.0, speed - DriveModel.HOLD_SPEED);
    }

    public static double stopTime(double speed, double stopTau) {
        if (!Double.isFinite(stopTau) || !(speed > DriveModel.HOLD_SPEED)) {
            return 0.0;
        }
        return stopTau * Math.log(speed / DriveModel.HOLD_SPEED);
    }

    static void jets(GroundSnapshot s, DriveReadout r) {
        for (final Jet j : s.jets()) {
            if (j.lit()) {
                r.jetsLit++;
            }
            if (j.supply() == JetSupply.EMPTY || j.supply() == JetSupply.UNPOWERED) {
                r.jetsDry++;
            } else if (j.supply() == JetSupply.LOW) {
                r.jetsLow++;
            }
        }
    }

    static void train(GroundSnapshot s, DriveReadout r) {
        for (final Unit u : s.units()) {
            final LinkState link = linkState(u);
            r.units.add(new UnitRead(u, link));
            if (u.isTractor()) {
                continue;
            }
            if (u.role().takesMode()) {
                r.trailers++;
            }
            if (Float.isFinite(u.articulation()) && (Double.isNaN(r.worstArticulation)
                    || Math.abs(u.articulation()) > Math.abs(r.worstArticulation))) {
                r.worstArticulation = u.articulation();
            }
            if (Float.isFinite(u.snapShare())) {
                r.worstSnap = Double.isNaN(r.worstSnap) ? u.snapShare() : Math.max(r.worstSnap, u.snapShare());
            }
            r.linkLost |= u.has(Unit.FLAG_LOST);
            r.swaying |= u.has(Unit.FLAG_SWAYING);
        }
    }

    public static LinkState linkState(Unit u) {
        if (u.isTractor()) {
            return LinkState.RIGID;
        }
        if (u.has(Unit.FLAG_LOST)) {
            return LinkState.GONE;
        }
        return switch (u.link()) {
            case HITCH -> LinkState.HITCHED;
            case JOINT -> LinkState.RIGID;
            case DOCK -> u.has(Unit.FLAG_DOCKING) ? LinkState.DOCKING : LinkState.DOCKED;
            case ROPE -> !Float.isFinite(u.reading()) ? LinkState.TAUT
                    : (u.reading() < -TAUT ? LinkState.SLACK : (u.reading() > TAUT ? LinkState.STRETCHED : LinkState.TAUT));
            case SPRING -> u.has(Unit.FLAG_SNAPPING)
                    || (Float.isFinite(u.snapShare()) && u.snapShare() >= LinkTrim.SNAP_WARN) ? LinkState.NEAR_SNAP
                    : LinkState.STRETCHED;
        };
    }

    static void stability(DriveReadout r) {
        double height = 0.0;
        int n = 0;
        for (final WheelRead wr : r.wheels) {
            final Wheel w = wr.wheel();
            if (w.unit() != 0 || !Float.isFinite(w.radius()) || !Float.isFinite(w.y())) {
                continue;
            }

            height += w.radius() - w.y();
            n++;
        }
        if (n == 0 || !Double.isFinite(r.trackWidth)) {
            return;
        }
        r.comHeight = height / n;
        if (!(r.comHeight > 0.05)) {
            r.comHeight = Double.NaN;
            return;
        }
        r.stability = r.trackWidth / (2.0 * r.comHeight);
        r.tipAngle = Math.toDegrees(Math.atan(r.stability));
        r.rolloverShare = Math.abs(r.latG) / r.stability;
    }

    static void state(GroundSnapshot s, DriveReadout r) {
        final GroundSnapshot.Drive d = s.drive();
        if (!s.identity().onCraft()) {
            r.state = DriveReadout.State.OFF_CRAFT;
        } else if (d.driving()) {
            r.state = DriveReadout.State.DRIVING;
        } else if (d.towed()) {
            r.state = DriveReadout.State.TOWED;
        } else if (r.groundSpeed > RUNAWAY_SPEED) {
            r.state = DriveReadout.State.RUNAWAY;
        } else if (d.parked()) {
            r.state = DriveReadout.State.PARKED;
        } else {
            r.state = DriveReadout.State.IDLE;
        }
    }

    public static boolean indicator(GroundSnapshot s, boolean left, long tick) {
        if (!s.lights().driving()) {
            return false;
        }
        final double steer = s.lights().steer();
        final boolean asked = left ? steer < -LinkLogic.SIGNAL_STEER : steer > LinkLogic.SIGNAL_STEER;
        return asked && Math.floorMod(tick / LinkLogic.BLINK_TICKS, 2L) == 0L;
    }

    public static List<Wheel> unitWheels(GroundSnapshot s, int unit) {
        final java.util.ArrayList<Wheel> out = new java.util.ArrayList<>();
        for (final Wheel w : s.wheels()) {
            if (w.unit() == unit) {
                out.add(w);
            }
        }
        return out;
    }

    public static double[] place(Unit u, double x, double z) {
        if (u == null || u.isTractor()) {
            return new double[]{x, z};
        }
        final double yaw = Math.toRadians(Float.isFinite(u.yaw()) ? u.yaw() : 0.0);
        final double c = Math.cos(yaw);
        final double sn = Math.sin(yaw);

        return new double[]{u.x() + x * c + z * sn, u.z() - x * sn + z * c};
    }

    public static double wrap360(double d) {
        final double w = d % 360.0;
        return w < 0.0 ? w + 360.0 : w;
    }

    public static double wrap180(double d) {
        final double w = wrap360(d + 180.0) - 180.0;
        return w;
    }

    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static double[] arr(V3 v) {
        return new double[]{v.x(), v.y(), v.z()};
    }
}
