package com.createstats.vccmk4.core;

import java.util.List;

public final class TrailerAssist {
    public static final double SWAY_RATE = 0.35;

    public static final double SWAY_SPEED = 3.0;

    static final double SWAY_BRAKE = 0.6;
    static final double SWAY_BIAS = 0.3;

    public static final double SWAY_DAMPING = 3.0;

    public static final double THRUST_BRAKE_STOP_SECONDS = 0.3;

    public record Train(double a0, double speed, boolean holding, double throttle, int brakeSignal, double topSpeed) {
        public int travel() {
            return TrainBalance.travel(speed);
        }
    }

    public record Command(boolean touch, double mountSpeed, int brakeLeft, int brakeRight, double pushForce, double swayAccel,
                          double needed, double made, boolean swaying) {
        public static final Command OFF = new Command(false, 0.0, 0, 0, 0.0, 0.0, 0.0, 0.0, false);

        public static final Command HOLD = new Command(true, 0.0, 15, 15, 0.0, 0.0, 0.0, 0.0, false);

        public int brake() {
            return Math.max(brakeLeft, brakeRight);
        }
    }

    private TrailerAssist() {
    }

    public static Command mirror(Train t) {
        int b = Math.max(0, Math.min(15, t.brakeSignal()));
        double speed = b > 0 ? 0.0 : t.throttle() * t.topSpeed();
        return new Command(true, speed, b, b, 0.0, 0.0, 0.0, 0.0, false);
    }

    public static Command assist(Train t, double demand, double trim, List<WheelForce.Wheel> wheels, double speed,
                                 double maxMountSpeed, double swayError) {
        return assist(t, demand, trim, wheels, speed, maxMountSpeed, swayError, 0.0, Double.NaN);
    }

    public static Command assist(Train t, double demand, double trim, List<WheelForce.Wheel> wheels, double speed,
                                 double maxMountSpeed, double swayError, double jetsNow, double mass) {
        double net = demand + trim;
        int travel = t.travel();
        if (travel == 0 && Math.abs(speed) < TrainBalance.REST_SPEED) {
            double dir = Math.signum(t.a0());
            if (net * dir <= 0.0) net = 0.0;
        }
        TrainBalance.Allocation whole = TrainBalance.allocate(net, speed, wheels, maxMountSpeed, t.holding());
        double jets = jetTarget(whole.unmet(), speed, mass);
        double made = Double.isFinite(jetsNow) ? jetsNow : 0.0;
        TrainBalance.Allocation now = made == 0.0 ? whole : TrainBalance.allocate(net - made, speed, wheels, maxMountSpeed, t.holding());
        double brake = now.brake();
        double left = 0.0;
        double right = 0.0;
        boolean swaying = false;
        double swayAccel = 0.0;
        if (Double.isFinite(swayError) && Math.abs(t.speed()) >= SWAY_SPEED && Math.abs(swayError) > SWAY_RATE) {
            swaying = true;
            double excess = Math.min(1.0, (Math.abs(swayError) - SWAY_RATE) / (2.0 * SWAY_RATE));
            brake = Math.max(brake, SWAY_BRAKE * excess);

            if (swayError < 0.0) left = SWAY_BIAS * excess;
            else right = SWAY_BIAS * excess;
            swayAccel = -SWAY_DAMPING * swayError;
        }
        double mount = brake > now.brake() + 1.0e-9 ? 0.0 : now.mountSpeed();
        int bl = signal(Math.min(1.0, brake + left));
        int br = signal(Math.min(1.0, brake + right));
        double needed = whole.needed() + WheelForce.rolling(wheels) * speed;
        double share = Math.abs(needed) < 1.0e-6 ? 1.0 : WheelForce.clamp(1.0 - Math.abs(whole.unmet()) / Math.abs(needed), 0.0, 1.0);
        return new Command(true, mount, bl, br, jets, swayAccel, whole.needed(), share, swaying);
    }

    static double jetTarget(double unmet, double speed, double mass) {
        if (!Double.isFinite(unmet) || unmet == 0.0) return 0.0;
        boolean braking = unmet * speed < 0.0;
        if (!braking) return unmet;
        if (Math.abs(speed) < TrainBalance.REST_SPEED) return 0.0;
        if (!(mass > 0.0) || !Double.isFinite(mass)) return unmet;
        double cap = mass * Math.abs(speed) / THRUST_BRAKE_STOP_SECONDS;
        return Math.copySign(Math.min(Math.abs(unmet), cap), unmet);
    }

    public static final double MIN_ARM = 0.3;

    public static double pivotInertia(double atCentre, double mass, double distance) {
        if (!(atCentre > 0.0) || !(mass > 0.0) || !Double.isFinite(distance)) return Double.NaN;
        return atCentre + mass * distance * distance;
    }

    private static int signal(double brake) {
        return (int) Math.min(15, Math.ceil(WheelForce.clamp(brake, 0.0, 1.0) * 15.0 - 1.0e-6));
    }
}
