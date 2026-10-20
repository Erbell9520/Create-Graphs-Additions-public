package com.createstats.vccmk4.core;

// keys are binary, wheels aren't. ramps
public final class DriveModel {
    public static final double STOPPED_SPEED = 1.0;

    public static final double HOLD_SPEED = 0.35;

    public static final int HOLD_DELAY = 6;

    public static final double SPEED_STEER_FROM = 6.0;

    public static final double SPEED_STEER_TO = 30.0;
    public static final double SPEED_STEER_FLOOR = 0.35;

    public record Options(boolean brakeThenReverse, boolean speedSteer, int steerTicks, int lockDeg,
                          int maxRpm, int reversePercent, int throttleTicks, int releaseTicks,
                          boolean holdWhenStopped, boolean parkBrake) {
        public static final int AS_RAMP = -1;
        public static final Options DEFAULT = new Options(true, false, 8, 30, 128, 50, 6, AS_RAMP, true, true);
        public static final int MIN_STEER_TICKS = 2;
        public static final int MAX_STEER_TICKS = 30;
        public static final int MIN_LOCK = 6;
        public static final int MAX_LOCK = 30;
        public static final int MIN_RPM = 16;
        public static final int MAX_RPM = 256;
        public static final int MIN_REVERSE = 10;
        public static final int MAX_REVERSE = 100;
        public static final int MIN_THROTTLE_TICKS = 0;

        public static final int MAX_THROTTLE_TICKS = 60;

        public Options sane() {
            return new Options(brakeThenReverse, speedSteer,
                    clampInt(steerTicks, MIN_STEER_TICKS, MAX_STEER_TICKS),
                    clampInt(lockDeg, MIN_LOCK, MAX_LOCK),
                    clampInt(maxRpm, MIN_RPM, MAX_RPM),
                    clampInt(reversePercent, MIN_REVERSE, MAX_REVERSE),
                    clampInt(throttleTicks, MIN_THROTTLE_TICKS, MAX_THROTTLE_TICKS),
                    clampInt(releaseTicks, AS_RAMP, MAX_THROTTLE_TICKS),
                    holdWhenStopped, parkBrake);
        }

        public int effectiveReleaseTicks() {
            return releaseTicks == AS_RAMP ? throttleTicks : releaseTicks;
        }

        public int releaseStep(int step) {
            Options o = sane();
            int from = o.effectiveReleaseTicks();
            int to = clampInt(from + step, MIN_THROTTLE_TICKS, MAX_THROTTLE_TICKS);
            if (o.releaseTicks() == AS_RAMP) return to == from ? AS_RAMP : to;
            int ramp = o.throttleTicks();
            boolean reaches = to == ramp || (from - ramp) * (to - ramp) < 0;
            return reaches ? AS_RAMP : to;
        }

        public Options withBrakeThenReverse(boolean v) {
            return new Options(v, speedSteer, steerTicks, lockDeg, maxRpm, reversePercent, throttleTicks, releaseTicks, holdWhenStopped, parkBrake);
        }

        public Options withSpeedSteer(boolean v) {
            return new Options(brakeThenReverse, v, steerTicks, lockDeg, maxRpm, reversePercent, throttleTicks, releaseTicks, holdWhenStopped, parkBrake);
        }

        public Options withSteerTicks(int v) {
            return new Options(brakeThenReverse, speedSteer, v, lockDeg, maxRpm, reversePercent, throttleTicks, releaseTicks, holdWhenStopped, parkBrake).sane();
        }

        public Options withLockDeg(int v) {
            return new Options(brakeThenReverse, speedSteer, steerTicks, v, maxRpm, reversePercent, throttleTicks, releaseTicks, holdWhenStopped, parkBrake).sane();
        }

        public Options withMaxRpm(int v) {
            return new Options(brakeThenReverse, speedSteer, steerTicks, lockDeg, v, reversePercent, throttleTicks, releaseTicks, holdWhenStopped, parkBrake).sane();
        }

        public Options withReversePercent(int v) {
            return new Options(brakeThenReverse, speedSteer, steerTicks, lockDeg, maxRpm, v, throttleTicks, releaseTicks, holdWhenStopped, parkBrake).sane();
        }

        public Options withThrottleTicks(int v) {
            return new Options(brakeThenReverse, speedSteer, steerTicks, lockDeg, maxRpm, reversePercent, v, releaseTicks, holdWhenStopped, parkBrake).sane();
        }

        public Options withReleaseTicks(int v) {
            return new Options(brakeThenReverse, speedSteer, steerTicks, lockDeg, maxRpm, reversePercent, throttleTicks, v, holdWhenStopped, parkBrake).sane();
        }

        public Options withHoldWhenStopped(boolean v) {
            return new Options(brakeThenReverse, speedSteer, steerTicks, lockDeg, maxRpm, reversePercent, throttleTicks, releaseTicks, v, parkBrake);
        }

        public Options withParkBrake(boolean v) {
            return new Options(brakeThenReverse, speedSteer, steerTicks, lockDeg, maxRpm, reversePercent, throttleTicks, releaseTicks, holdWhenStopped, v);
        }

        private static int clampInt(int v, int lo, int hi) {
            return v < lo ? lo : (v > hi ? hi : v);
        }
    }

    public record Inputs(double throttle, double steer, boolean brake, boolean boost, double forwardSpeed,
                         boolean otherDemand, boolean holdAllowed) {
        public Inputs(double throttle, double steer, boolean brake, boolean boost, double forwardSpeed) {
            this(throttle, steer, brake, boost, forwardSpeed, false, true);
        }
    }

    public record Output(double throttle, double steer, boolean braking, boolean boosting, double lockDeg,
                         double pushDir, boolean holding, double brake) {
        public Output(double throttle, double steer, boolean braking, boolean boosting, double lockDeg, double pushDir,
                      boolean holding) {
            this(throttle, steer, braking, boosting, lockDeg, pushDir, holding, braking ? 1.0 : 0.0);
        }

        public static Output idle(double lockDeg) {
            return new Output(0.0, 0.0, false, false, lockDeg, 1.0, false);
        }

        public int brakeSignal() {
            return (int) Math.max(0, Math.min(15, Math.round(brake * 15.0)));
        }

        public char gear() {
            if (holding) return 'H';
            if (braking) return 'B';
            if (throttle > 1.0e-6) return 'D';
            if (throttle < -1.0e-6) return 'R';
            return 'N';
        }
    }

    private double steerPos;
    private double throttlePos;
    private int stillTicks;

    public double steerPos() {
        return steerPos;
    }

    public double throttlePos() {
        return throttlePos;
    }

    public void reset() {
        steerPos = 0.0;
        throttlePos = 0.0;
        stillTicks = 0;
    }

    public void follow(Output out) {
        steerPos = GroundSteer.clamp(out.steer(), -1.0, 1.0);
        throttlePos = GroundSteer.clamp(out.throttle(), -1.0, 1.0);
        stillTicks = 0;
    }

    public static double lockAt(Options options, double forwardSpeed) {
        Options o = options.sane();
        double lock = o.lockDeg();
        if (o.speedSteer()) {
            double f = 1.0 - (Math.abs(forwardSpeed) - SPEED_STEER_FROM) / (SPEED_STEER_TO - SPEED_STEER_FROM);
            lock *= GroundSteer.clamp(f, SPEED_STEER_FLOOR, 1.0);
        }
        return lock;
    }

    public void closeThrottle() {
        throttlePos = 0.0;
    }

    public Output step(Options options, Inputs in) {
        Options o = options.sane();
        double target = GroundSteer.clamp(in.steer(), -1.0, 1.0);
        double perTick = 1.0 / o.steerTicks();

        double rate = Math.abs(target) < Math.abs(steerPos) ? perTick * 1.5 : perTick;
        steerPos += GroundSteer.clamp(target - steerPos, -rate, rate);
        if (Math.abs(steerPos) < 1.0e-6) steerPos = 0.0;

        double t = GroundSteer.clamp(in.throttle(), -1.0, 1.0);
        boolean braking = in.brake();
        if (!braking && o.brakeThenReverse()) {
            if (t < 0.0 && in.forwardSpeed() > STOPPED_SPEED) braking = true;
            if (t > 0.0 && in.forwardSpeed() < -STOPPED_SPEED) braking = true;
        }

        boolean demand = t != 0.0 || in.boost() || in.otherDemand();
        boolean atRest = Math.abs(in.forwardSpeed()) <= HOLD_SPEED;
        if (!o.holdWhenStopped() || !in.holdAllowed() || demand || in.brake() || !atRest) stillTicks = 0;
        else if (stillTicks < HOLD_DELAY) stillTicks++;
        boolean holding = !braking && stillTicks >= HOLD_DELAY;
        braking |= holding;

        double wanted = braking ? 0.0 : (t < 0.0 ? t * o.reversePercent() / 100.0 : t);

        throttlePos = ramp(throttlePos, wanted, o.throttleTicks(), braking ? 0 : o.effectiveReleaseTicks());

        boolean boosting = in.boost() && !braking;
        double pushDir = t < 0.0 ? -1.0 : 1.0;

        double lock = o.lockDeg();
        if (o.speedSteer()) {
            double v = Math.abs(in.forwardSpeed());
            double f = 1.0 - (v - SPEED_STEER_FROM) / (SPEED_STEER_TO - SPEED_STEER_FROM);
            lock *= GroundSteer.clamp(f, SPEED_STEER_FLOOR, 1.0);
        }
        return new Output(throttlePos, steerPos, braking, boosting, lock, pushDir, holding);
    }

    static double ramp(double current, double wanted, int up, int down) {
        if (current * wanted < 0.0) current = 0.0;

        if (Math.abs(wanted) > Math.abs(current)) {
            if (up <= 0) return wanted;
            double next = current + Math.signum(wanted) / up;
            return Math.abs(next) >= Math.abs(wanted) - 1.0e-9 ? wanted : next;
        }
        if (down <= 0) return wanted;
        double left = Math.abs(current) - 1.0 / down;
        return left <= Math.abs(wanted) + 1.0e-9 ? wanted : Math.copySign(left, current);
    }
}
