package com.createstats.surveyor.client;

public final class StaffMotion {
    public static final double AIM_TAU = 0.12;

    public static final double FLASH_TICKS = 10.0;

    static final double MAX_STEP = 1.0 / 240.0;

    static final double RESTART_GAP = 0.5;

    public static final double VANE_FRONT_DELAY = 0.10;
    public static final double VANE_REAR_DELAY = 0.20;

    public static final double VANE_SECONDS = 0.24;
    static final double VANE_BACK = 2.4;

    public static final double WHEEL_TICKS = 6.0;
    static final double WHEEL_BACK = 2.6;

    public static final double DETENT_LIFT = 16.0;

    public static final double NEEDLE_OMEGA = 2.0 * Math.PI / 0.9;

    public static final double NEEDLE_ZETA = 0.16;

    public static final double NEEDLE_DRAG = 1.0;

    public static final double NEEDLE_KNOCK = 170.0;

    public static final double BUBBLE_OMEGA = 9.0;
    public static final double BUBBLE_ZETA = 0.35;

    public static final double BUBBLE_KNOCK = 9.0;

    public static final double BUBBLE_THROW = -0.004;

    public static final double BUBBLE_TRAVEL = 1.8;

    public static final double GRAVITY = 9.8;

    public static final double DAMPING = 1.6;

    private StaffMotion() {
    }

    public static double yaw(double dx, double dz) {
        return Math.toDegrees(Math.atan2(-dx, -dz));
    }

    public static double follow(double current, double target, double dt, double tau) {
        if (dt <= 0.0) return current;
        double k = 1.0 - Math.exp(-dt / tau);
        return wrap(current + wrap(target - current) * k);
    }

    public static double wrap(double deg) {
        double d = deg % 360.0;
        if (d >= 180.0) d -= 360.0;
        if (d < -180.0) d += 360.0;
        return d;
    }

    public static double flash(double ticksSince) {
        if (!(ticksSince >= 0.0) || ticksSince >= FLASH_TICKS) return 0.0;
        double left = 1.0 - ticksSince / FLASH_TICKS;
        return left * left;
    }

    static double easeOutBack(double t, double back) {
        if (!(t > 0.0)) return 0.0;
        if (t >= 1.0) return 1.0;
        double u = t - 1.0;
        return 1.0 + (back + 1.0) * u * u * u + back * u * u;
    }

    public static double vane(double seconds, double delay) {
        if (!(seconds >= 0.0)) return 0.0;
        double u = (seconds - delay) / VANE_SECONDS;
        if (u <= 0.0) return 90.0;
        return 90.0 * (1.0 - easeOutBack(u, VANE_BACK));
    }

    public static double quarters(int fromMode, int toMode, int modes) {
        int steps = Math.floorMod(toMode - fromMode, modes);
        if (steps == 0) return 0.0;
        if (steps * 2 > modes) steps -= modes;
        return -90.0 * steps;
    }

    public static double wheel(double from, double to, double ticksSince) {
        if (!(ticksSince >= 0.0)) return to;
        return from + (to - from) * easeOutBack(ticksSince / WHEEL_TICKS, WHEEL_BACK);
    }

    public static double detent(double wheelDeg) {
        double q = -wheelDeg / 90.0;
        double frac = q - Math.floor(q);
        if (frac <= 0.02 || frac >= 0.98) return 0.0;
        return DETENT_LIFT * Math.pow(Math.sin(Math.PI * frac), 0.7);
    }

    public static final class Needle {
        private double heading;
        private double speed;

        public void step(double dt, double boxRate) {
            if (dt > RESTART_GAP) {
                heading = 0.0;
                speed = 0.0;
                return;
            }
            if (!(dt > 0.0)) return;
            int n = Math.max(1, (int) Math.ceil(dt / MAX_STEP));
            double h = dt / n;
            double w2 = NEEDLE_OMEGA * NEEDLE_OMEGA;
            double c = 2.0 * NEEDLE_ZETA * NEEDLE_OMEGA;
            for (int i = 0; i < n; i++) {
                double a = -w2 * wrap(heading) - c * (speed - NEEDLE_DRAG * boxRate);
                speed += a * h;
                heading = wrap(heading + speed * h);
            }
        }

        public void kick(double degreesPerSecond) {
            speed += degreesPerSecond;
        }

        public double heading() {
            return heading;
        }
    }

    public static final class Spring {
        private final double omega;
        private final double zeta;
        private double x;
        private double v;

        public Spring(double omega, double zeta) {
            this.omega = omega;
            this.zeta = zeta;
        }

        public double step(double target, double dt) {
            if (dt > RESTART_GAP) {
                x = target;
                v = 0.0;
                return x;
            }
            if (!(dt > 0.0)) return x;
            int n = Math.max(1, (int) Math.ceil(dt / MAX_STEP));
            double h = dt / n;
            for (int i = 0; i < n; i++) {
                double a = -omega * omega * (x - target) - 2.0 * zeta * omega * v;
                v += a * h;
                x += v * h;
            }
            return x;
        }

        public void kick(double dv) {
            v += dv;
        }

        public double value() {
            return x;
        }
    }

    public static final class Bob {
        private double x, y, z;
        private double px, py, pz;
        private double ax, ay, az;
        private boolean placed;

        public void step(double pivotX, double pivotY, double pivotZ, double length, double dt) {
            if (!placed || dt > RESTART_GAP || length <= 1e-6) {
                x = px = pivotX;
                y = py = pivotY - length;
                z = pz = pivotZ;
                ax = pivotX;
                ay = pivotY;
                az = pivotZ;
                placed = true;
                return;
            }
            if (dt <= 0.0) return;
            int n = Math.max(1, (int) Math.ceil(dt / MAX_STEP));
            double h = dt / n;
            double keep = Math.exp(-DAMPING * h);
            double sx = ax, sy = ay, sz = az;
            for (int i = 1; i <= n; i++) {
                double f = (double) i / n;
                double qx = sx + (pivotX - sx) * f;
                double qy = sy + (pivotY - sy) * f;
                double qz = sz + (pivotZ - sz) * f;
                double vx = (x - px) * keep;
                double vy = (y - py) * keep;
                double vz = (z - pz) * keep;
                px = x;
                py = y;
                pz = z;
                x += vx;
                y += vy - GRAVITY * h * h;
                z += vz;
                double dx = x - qx, dy = y - qy, dz = z - qz;
                double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (d > 1e-9) {
                    x = qx + dx / d * length;
                    y = qy + dy / d * length;
                    z = qz + dz / d * length;
                } else {
                    x = qx;
                    y = qy - length;
                    z = qz;
                }
            }
            ax = pivotX;
            ay = pivotY;
            az = pivotZ;
        }

        public double[] hang() {
            if (!placed) return new double[]{0.0, -1.0, 0.0};
            double dx = x - ax, dy = y - ay, dz = z - az;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            return d > 1e-9 ? new double[]{dx / d, dy / d, dz / d} : new double[]{0.0, -1.0, 0.0};
        }
    }
}
