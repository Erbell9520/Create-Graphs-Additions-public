package com.createstats.vccmk4.core;

import java.util.List;

public final class TrainBalance {
    public static final double BRAKE_LEAD = 0.10;

    public static final double REST_SPEED = 0.35;

    public record Body(int parent, double mass, List<WheelForce.Wheel> wheels, boolean jets) {
        public Body(int parent, double mass, List<WheelForce.Wheel> wheels) {
            this(parent, mass, wheels, false);
        }

        public boolean powered() {
            if (jets) return true;
            for (WheelForce.Wheel w : wheels) if (w.driven()) return true;
            return false;
        }

        double rolling() {
            return WheelForce.rolling(wheels);
        }
    }

    public record Allocation(double mountSpeed, double brake, double unmet, double needed) {
        public static final Allocation NONE = new Allocation(0.0, 0.0, 0.0, 0.0);

        public int brakeSignal() {
            return (int) Math.min(15, Math.ceil(brake * 15.0 - 1.0e-6));
        }
    }

    private TrainBalance() {
    }

    public static double tractorAcceleration(double mass, double driveForce, double dragPerSpeed, double speed) {
        if (!(mass > 0.0) || !Double.isFinite(mass)) return 0.0;
        double a = (driveForce - dragPerSpeed * speed) / mass;
        return Double.isFinite(a) ? a : 0.0;
    }

    public static int[] carriers(List<Body> bodies) {
        int[] out = new int[bodies.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = -1;
            if (i == 0) continue;
            int at = i;
            int guard = 0;
            while (at > 0 && guard++ < bodies.size()) {
                if (bodies.get(at).powered()) {
                    out[i] = at;
                    break;
                }
                at = bodies.get(at).parent();
            }
        }
        return out;
    }

    public static double demand(int i, List<Body> bodies, int[] carriers, double a0, double speed, int travel) {
        Body b = bodies.get(i);
        double a = lead(a0, travel);
        double own = b.mass() * a;
        double carried = 0.0;
        for (int j = 1; j < bodies.size(); j++) {
            if (j == i || carriers[j] != i) continue;
            Body c = bodies.get(j);
            double pull = c.mass() * a + c.rolling() * speed;

            if (travel != 0 && pull * travel > 0.0) carried += pull;
            else if (travel == 0 && a0 * pull > 0.0) carried += pull;
        }
        return own + carried;
    }

    static double lead(double a0, int travel) {
        boolean slowing = travel != 0 && a0 * travel < 0.0;
        return slowing ? a0 * (1.0 + BRAKE_LEAD) : a0;
    }

    public static Allocation allocate(double net, double speed, List<WheelForce.Wheel> wheels, double maxMountSpeed,
                                      boolean holding) {
        double rolling = WheelForce.rolling(wheels);
        double driveK = WheelForce.drivePerSpeed(wheels);
        double brakeK = WheelForce.brakeCapacity(wheels);
        boolean atRest = Math.abs(speed) < REST_SPEED;
        if (holding && atRest) return new Allocation(0.0, 1.0, 0.0, net);

        double propulsion = net + rolling * speed;
        if (!Double.isFinite(propulsion)) return Allocation.NONE;
        boolean opposes = !atRest && propulsion * speed < 0.0;
        if (!opposes) {
            if (driveK <= 0.0) return new Allocation(0.0, 0.0, propulsion, net);
            double s = propulsion / driveK;
            double limit = Math.max(0.0, maxMountSpeed);
            double clamped = Math.max(-limit, Math.min(limit, s));
            return new Allocation(clamped, 0.0, (s - clamped) * driveK, net);
        }
        if (brakeK <= 0.0) return new Allocation(0.0, 0.0, propulsion, net);
        double b = -propulsion / (brakeK * speed);
        double clamped = WheelForce.clamp(b, 0.0, 1.0);
        double made = -clamped * brakeK * speed;
        return new Allocation(0.0, clamped, propulsion - made, net);
    }

    public static double[] wheelForces(List<WheelForce.Wheel> wheels, double[] mountSpeeds, double[] brakes) {
        double drive = 0.0;
        double drag = 0.0;
        for (int i = 0; i < wheels.size(); i++) {
            WheelForce.Wheel w = wheels.get(i);
            double b = brakes != null && i < brakes.length ? brakes[i] : 0.0;
            double s = mountSpeeds != null && i < mountSpeeds.length ? mountSpeeds[i] : 0.0;
            if (w.driven()) drive += WheelForce.drive(s, b, w.surface());
            drag += WheelForce.dragPerSpeed(w, b);
        }
        return new double[]{drive, drag};
    }

    public static int travel(double speed) {
        if (speed > REST_SPEED) return 1;
        if (speed < -REST_SPEED) return -1;
        return 0;
    }
}
