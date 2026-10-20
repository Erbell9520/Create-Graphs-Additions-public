package com.createstats.vccmk4.core;

// springs not lerps
public final class BtaG1Motion {
    private BtaG1Motion() {
    }

    public static final double SPEED_MAX_KMH = 120.0;

    public static final double SWEEP_START_DEG = 120.0;
    public static final double SWEEP_DEG = 240.0;
    public static final double WHEEL_LOCK_DEG = 135.0;
    public static final double THROTTLE_SWING_DEG = 30.0;
    public static final double BRAKE_RELEASED_DEG = -22.5;
    public static final double BRAKE_SERVICE_DEG = 6.0;
    public static final double BRAKE_PARK_DEG = 34.0;

    public static final double BRAKE_SERVICE_LEVEL = 0.55;

    public static final double DT = 1.0 / 20.0;
    public static final double WHEEL_OMEGA = 16.0;
    public static final double THROTTLE_OMEGA = 14.0;
    public static final double BRAKE_APPLY_OMEGA = 20.0;
    public static final double BRAKE_RELEASE_OMEGA = 8.0;
    public static final double NEEDLE_OMEGA = 9.0;

    public static final double NEEDLE_ZETA = 0.6;

    public static final double LAMP_UP = 0.5;
    public static final double LAMP_DOWN = 0.25;

    public static final double DIAL_UP = 0.10;
    public static final double DIAL_DOWN = 0.05;

    public static final double HALO_ALPHA = 0.32;

    public static final int SELF_TEST_TICKS = 32;

    public static final double SELF_TEST_MAX_KMH = 3.6;
    private static final int SWEEP_UP_TICKS = 12;
    private static final int SWEEP_HOLD_TICKS = 3;

    private static final int LAMP_TEST_TICKS = 12;

    public enum Lamp {
        BRAKE, PARK, HOLD, REVERSE, LEFT, READY, AUTO, BOOST, RIGHT;

        public static final int COUNT = values().length;
    }

    public static double needleFraction(double kmh) {
        return clamp(Math.abs(kmh) / SPEED_MAX_KMH, 0.0, 1.0);
    }

    public static double needleDegrees(double fraction) {
        return SWEEP_START_DEG - SWEEP_DEG * clamp(fraction, 0.0, 1.0);
    }

    public static double wheelDegrees(double steer) {
        return -clamp(steer, -1.0, 1.0) * WHEEL_LOCK_DEG;
    }

    public static double throttleDegrees(double throttle) {
        return -clamp(throttle, -1.0, 1.0) * THROTTLE_SWING_DEG;
    }

    public static double brakeLevel(BtaG1State s) {
        if (s.parked()) return 1.0;
        return BRAKE_SERVICE_LEVEL * clamp(s.brake(), 0.0, 1.0);
    }

    public static double brakeDegrees(double level) {
        double l = clamp(level, 0.0, 1.0);
        if (l <= BRAKE_SERVICE_LEVEL) {
            return BRAKE_RELEASED_DEG + (BRAKE_SERVICE_DEG - BRAKE_RELEASED_DEG) * l / BRAKE_SERVICE_LEVEL;
        }
        return BRAKE_SERVICE_DEG + (BRAKE_PARK_DEG - BRAKE_SERVICE_DEG) * (l - BRAKE_SERVICE_LEVEL) / (1.0 - BRAKE_SERVICE_LEVEL);
    }

    public static boolean lit(Lamp lamp, BtaG1State s, long tick) {
        if (lamp == Lamp.PARK) return s.parked();
        if (!s.driving()) return false;
        double signal = s.indicate() != 0 ? s.indicate() : s.steer();
        boolean flash = Math.floorMod(tick / LinkLogic.BLINK_TICKS, 2L) == 0L;
        return switch (lamp) {
            case BRAKE -> s.braking();
            case HOLD -> s.holding();
            case REVERSE -> s.reversing();
            case READY -> true;
            case AUTO -> s.route();
            case BOOST -> s.boosting();
            case LEFT -> signal < -LinkLogic.SIGNAL_STEER && flash;
            case RIGHT -> signal > LinkLogic.SIGNAL_STEER && flash;
            case PARK -> false;
        };
    }

    public static int haloAlpha(double level) {
        double l = clamp(level, 0.0, 1.0);
        return (int) Math.round(255.0 * HALO_ALPHA * l * l);
    }

    public static double sweep(int t) {
        if (t < 0 || t >= SELF_TEST_TICKS) return 0.0;
        if (t < SWEEP_UP_TICKS) return smooth(t / (double) SWEEP_UP_TICKS);
        if (t < SWEEP_UP_TICKS + SWEEP_HOLD_TICKS) return 1.0;
        int down = SELF_TEST_TICKS - SWEEP_UP_TICKS - SWEEP_HOLD_TICKS;
        return 1.0 - smooth((t - SWEEP_UP_TICKS - SWEEP_HOLD_TICKS) / (double) down);
    }

    public static boolean testLit(Lamp lamp, int t) {
        int k = lamp.ordinal();
        return t >= k && t < k + LAMP_TEST_TICKS;
    }

    private static double smooth(double x) {
        double c = clamp(x, 0.0, 1.0);
        return c * c * (3.0 - 2.0 * c);
    }

    public static final class Spring {
        private double x;
        private double v;

        public double value() {
            return x;
        }

        public double velocity() {
            return v;
        }

        public void snap(double value) {
            x = value;
            v = 0.0;
        }

        public void step(double target, double omega, double zeta, double dt) {
            double d = x - target;
            if (omega <= 0.0 || dt <= 0.0) return;
            if (zeta >= 0.999) {
                double e = Math.exp(-omega * dt);
                double j = v + omega * d;
                x = target + (d + j * dt) * e;
                v = (v - omega * j * dt) * e;
            } else if (zeta > 0.0) {
                double wd = omega * Math.sqrt(1.0 - zeta * zeta);
                double e = Math.exp(-zeta * omega * dt);
                double c = Math.cos(wd * dt);
                double s = Math.sin(wd * dt);
                double b = (v + zeta * omega * d) / wd;
                x = target + e * (d * c + b * s);
                v = e * (v * c - ((d * omega * omega + zeta * omega * v) / wd) * s);
            } else {
                x = target;
                v = 0.0;
            }
        }

        public void pin(double lo, double hi) {
            if (x < lo) {
                x = lo;
                if (v < 0.0) v = 0.0;
            } else if (x > hi) {
                x = hi;
                if (v > 0.0) v = 0.0;
            }
        }
    }

    public static final class Frame {
        public final double wheelDeg;
        public final double throttleDeg;
        public final double brakeDeg;
        public final double needleDeg;
        public final double dial;
        private final double[] lamps;

        Frame(double wheelDeg, double throttleDeg, double brakeDeg, double needleDeg, double dial, double[] lamps) {
            this.wheelDeg = wheelDeg;
            this.throttleDeg = throttleDeg;
            this.brakeDeg = brakeDeg;
            this.needleDeg = needleDeg;
            this.dial = dial;
            this.lamps = lamps;
        }

        public double lamp(Lamp l) {
            return lamps[l.ordinal()];
        }
    }

    public static final class Pose {
        private final Spring wheel = new Spring();
        private final Spring throttle = new Spring();
        private final Spring brake = new Spring();
        private final Spring needle = new Spring();
        private final double[] lamp = new double[Lamp.COUNT];
        private double dial;
        private double wheelPrev;
        private double throttlePrev;
        private double brakePrev;
        private double needlePrev;
        private final double[] lampPrev = new double[Lamp.COUNT];
        private double dialPrev;
        private BtaG1State last = BtaG1State.IDLE;
        private boolean primed;
        private int testLeft;

        public Pose() {
            brake.snap(brakeDegrees(brakeLevel(BtaG1State.IDLE)));
            brakePrev = brake.value();
        }

        public boolean testing() {
            return testLeft > 0;
        }

        public void tick(BtaG1State target, long gameTime) {
            wheelPrev = wheel.value();
            throttlePrev = throttle.value();
            brakePrev = brake.value();
            needlePrev = needle.value();
            dialPrev = dial;
            System.arraycopy(lamp, 0, lampPrev, 0, lamp.length);

            if (!primed) {
                primed = true;
                wheel.snap(wheelDegrees(target.steer()));
                throttle.snap(throttleDegrees(target.throttle()));
                brake.snap(brakeDegrees(brakeLevel(target)));
                needle.snap(needleFraction(target.speedKmh()));
                dial = target.driving() ? 1.0 : 0.0;
                for (Lamp l : Lamp.values()) lamp[l.ordinal()] = lit(l, target, gameTime) ? 1.0 : 0.0;
                wheelPrev = wheel.value();
                throttlePrev = throttle.value();
                brakePrev = brake.value();
                needlePrev = needle.value();
                dialPrev = dial;
                System.arraycopy(lamp, 0, lampPrev, 0, lamp.length);
                last = target;
                return;
            }
            if (!last.seated() && target.seated() && target.greeting() && Math.abs(target.speedKmh()) < SELF_TEST_MAX_KMH) {
                testLeft = SELF_TEST_TICKS;
            }
            last = target;
            int t = SELF_TEST_TICKS - testLeft;

            wheel.step(wheelDegrees(target.steer()), WHEEL_OMEGA, 1.0, DT);
            throttle.step(throttleDegrees(target.throttle()), THROTTLE_OMEGA, 1.0, DT);
            double brakeTarget = brakeDegrees(brakeLevel(target));
            brake.step(brakeTarget, brakeTarget > brake.value() ? BRAKE_APPLY_OMEGA : BRAKE_RELEASE_OMEGA, 1.0, DT);
            double scale = needleFraction(target.speedKmh());
            if (testLeft > 0) scale = Math.max(scale, sweep(t));
            needle.step(scale, NEEDLE_OMEGA, NEEDLE_ZETA, DT);
            needle.pin(0.0, 1.0);

            for (Lamp l : Lamp.values()) {
                boolean on = lit(l, target, gameTime) || (testLeft > 0 && testLit(l, t));
                lamp[l.ordinal()] = approach(lamp[l.ordinal()], on ? 1.0 : 0.0, LAMP_UP, LAMP_DOWN);
            }
            dial = approach(dial, target.driving() ? 1.0 : 0.0, DIAL_UP, DIAL_DOWN);
            if (testLeft > 0) testLeft--;
        }

        public Frame frame(float partial) {
            double p = clamp(partial, 0.0, 1.0);
            double[] lamps = new double[Lamp.COUNT];
            for (int i = 0; i < lamps.length; i++) lamps[i] = mix(lampPrev[i], lamp[i], p);
            return new Frame(mix(wheelPrev, wheel.value(), p), mix(throttlePrev, throttle.value(), p),
                    mix(brakePrev, brake.value(), p), needleDegrees(mix(needlePrev, needle.value(), p)),
                    mix(dialPrev, dial, p), lamps);
        }
    }

    static double approach(double value, double target, double up, double down) {
        double delta = target - value;
        double rate = delta > 0.0 ? up : down;
        if (Math.abs(delta) <= rate + 1.0e-9) return target;
        return value + Math.signum(delta) * rate;
    }

    static double mix(double a, double b, double t) {
        return a + (b - a) * t;
    }

    static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
