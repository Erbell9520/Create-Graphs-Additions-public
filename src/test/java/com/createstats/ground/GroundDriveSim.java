package com.createstats.ground;

import java.util.ArrayList;
import java.util.List;

import com.createstats.aerial.Quat;
import com.createstats.aerial.V3;
import com.createstats.ground.GroundSnapshot.Body;
import com.createstats.ground.GroundSnapshot.Drive;
import com.createstats.ground.GroundSnapshot.Identity;
import com.createstats.ground.GroundSnapshot.Log;
import com.createstats.ground.GroundSnapshot.Unit;
import com.createstats.vccmk4.core.BodyRole;
import com.createstats.vccmk4.core.LinkKind;
import com.createstats.vccmk4.core.TrailerMode;
import com.createstats.vccmk4.core.WheelForce;

public final class GroundDriveSim {
    static final double MASS = 180.0;
    static final double WHEELBASE = 4.0;
    static final int WHEELS = 4;
    static final int MAX_RPM = 128;
    static final double LOCK_DEG = 30.0;

    private GroundDriveSim() {
    }

    record Leg(double seconds, double throttle, double steer, boolean brake) {
    }

    static final List<Leg> LAP = List.of(
            new Leg(2.0, 0.0, 0.0, false),
            new Leg(12.0, 1.0, 0.0, false),
            new Leg(8.0, 1.0, 0.0, false),
            new Leg(6.0, 0.8, 0.3, false),
            new Leg(6.0, 1.0, 0.0, false),
            new Leg(3.0, 0.0, 0.0, true),
            new Leg(5.0, 0.5, -0.5, false),
            new Leg(10.0, 1.0, 0.0, false),
            new Leg(4.0, 0.0, 0.0, true),
            new Leg(3.0, 0.0, 0.0, false));

    public static GroundRecorder lap(int minutes, long endTick) {
        final int ticks = Math.max(1, minutes) * 1200;
        final long start = endTick - ticks;
        final List<GroundSnapshot> samples = new ArrayList<>();
        double x = 0.0;
        double z = 0.0;
        double heading = 0.0;
        double v = 0.0;
        double lastV = 0.0;
        double lapTime = 0.0;
        long impactTick = Long.MIN_VALUE;
        boolean kerbDone = false;
        int leg = 0;
        double inLeg = 0.0;
        for (int t = 0; t <= ticks; t++) {
            final Leg l = LAP.get(leg);
            final double b = l.brake() ? 1.0 : 0.0;
            final double drive = WHEELS * WheelForce.drive(l.throttle() * MAX_RPM, b, 1.0);
            final double drag = WHEELS * WheelForce.dragPerSpeed(WheelForce.Wheel.typical(true), b) * v;
            final double a = (drive - drag) / MASS;
            v = Math.max(0.0, v + a * 0.05);
            final double radius = Math.abs(l.steer()) < 1.0e-6 ? Double.POSITIVE_INFINITY
                    : WHEELBASE / Math.tan(Math.toRadians(LOCK_DEG * Math.abs(l.steer())));
            final double yaw = Double.isFinite(radius) ? -Math.signum(l.steer()) * v / radius : 0.0;
            heading = DriveMath.wrap360(heading - Math.toDegrees(yaw) * 0.05);
            final double h = Math.toRadians(heading);
            x += Math.sin(h) * v * 0.05;
            z += -Math.cos(h) * v * 0.05;

            if (leg == 8 && !kerbDone && v < 1.0) {
                impactTick = start + t;
                kerbDone = true;
            }
            if (t % GroundRecorder.SAMPLE_TICKS == 0) {
                final double lonA = (v - lastV) / (0.05 * GroundRecorder.SAMPLE_TICKS);
                lastV = v;
                samples.add(snapshot(start + t, x, z, heading, v, yaw, lonA, l, t == 0 ? "" : "Bolat", lapTime % 59.0,
                        impactTick));
            }
            inLeg += 0.05;
            lapTime += 0.05;
            if (inLeg >= l.seconds()) {
                inLeg = 0.0;
                leg = (leg + 1) % LAP.size();
                if (leg == 0) {
                    kerbDone = false;
                }
            }
        }
        final GroundSnapshot last = samples.get(samples.size() - 1);
        final V3 shift = GroundFixtures.ORIGIN.minus(last.body().position());
        final GroundRecorder recorder = new GroundRecorder();
        for (final GroundSnapshot s : samples) {
            recorder.observe(shifted(s, shift));
        }
        return recorder;
    }

    private static GroundSnapshot snapshot(long tick, double x, double z, double heading, double v, double yaw,
                                           double lonA, Leg leg, String driver, double lapTime, long impactTick) {
        final Quat q = GroundFixtures.orientation(heading, 0.0);
        final V3 forward = q.rotate(Body.NORTH);
        final V3 right = forward.cross(V3.UP).normalized();
        final V3 accel = right.times(-yaw * v).plus(forward.times(lonA));
        final Body body = new Body(new V3(x, GroundFixtures.ORIGIN.y(), z), q, Body.NORTH, forward.times(v), yaw, v,
                accel, new V3(0, -GroundFixtures.G, 0));

        final Log log = impactTick == Long.MIN_VALUE ? new Log(0.6, 0.0, -1, 0.0, 0.0, 0.0)
                : new Log(0.6, 5.6, (int) Math.min(Integer.MAX_VALUE, tick - impactTick), 5.6, 0.0, 0.0);
        final List<String> problems = lapTime > 40.0 && lapTime < 46.0
                ? List.of("G1 gets no rotation from its engine: is the engine running and fuelled, and its shaft connected?")
                : List.of();
        final Drive drive = new Drive(driver, driver.isEmpty() ? Long.MIN_VALUE : tick, true, !driver.isEmpty(), false,
                false, leg.throttle(), leg.steer(), leg.brake(), false, false,
                leg.brake() ? 'B' : (leg.throttle() > 0 ? 'D' : 'N'), LOCK_DEG, 0.0, "", 0, new float[0]);
        final Identity identity = new Identity("Rosie", GroundFixtures.CAR_ID, "4x4, front steer (auto)", VehicleKind.CAR,
                GroundCommand.all(), true, true, "");
        final Unit tractor = new Unit(0, -1, BodyRole.PART, LinkKind.JOINT, TrailerMode.OFF, true, false, 0f, 0f, 0f,
                Float.NaN, 0f, Float.NaN, Float.NaN, Float.NaN, 0, WHEELS, (float) MASS, "B1");
        return new GroundSnapshot(tick, identity, body, log, drive, null, null, null, null, null, List.of(tractor), null,
                null, null, problems, null, null, null, null, null, null, null);
    }

    private static GroundSnapshot shifted(GroundSnapshot s, V3 shift) {
        final Body b = s.body();
        return s.withBody(new Body(b.position().plus(shift), b.orientation(), b.nose(), b.velocity(), b.yawRate(),
                b.forwardSpeed(), b.accel(), b.gravity()));
    }
}
