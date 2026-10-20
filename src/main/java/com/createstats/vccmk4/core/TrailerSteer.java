package com.createstats.vccmk4.core;

// straight trailers cut corners and jackknife in reverse. same kinematics fixes both
public final class TrailerSteer {
    public static final double HITCH_GAIN = 1.5;

    public static final double FLEX_GAIN = 1.0;

    public static final double MIN_SPEED = 0.5;

    static final double MAX_TRACK_RATIO = 0.9;

    public record Input(double hx, double hy, double vx, double vy, double heading, double parentRate,
                        double[] centre, double tractorHeading, double[] wx, double[] wy, LinkKind link,
                        double lockDeg) {
    }

    public static final double MEASURED_SPEED = 1.0;

    public static final double MEASURED_RATE = 0.05;

    private TrailerSteer() {
    }

    public static double[] turnCentre(double[] geometric, double cx, double cy, double vx, double vy, double yawRate) {
        boolean measurable = Math.hypot(vx, vy) >= MEASURED_SPEED && Math.abs(yawRate) >= MEASURED_RATE
                && Double.isFinite(vx) && Double.isFinite(vy) && Double.isFinite(yawRate);
        if (measurable) return new double[]{cx - vy / yawRate, cy + vx / yawRate};
        return geometric;
    }

    public static final double MIN_DRAWBAR = 0.5;

    public static Input drawbar(Input in, double px, double py, double pvx, double pvy) {
        double rx = in.hx() - px;
        double ry = in.hy() - py;
        double length = Math.hypot(rx, ry);
        if (!Double.isFinite(length) || !Double.isFinite(pvx) || !Double.isFinite(pvy)) return in;
        if (length < MIN_DRAWBAR || Math.hypot(pvx, pvy) < MIN_SPEED) {
            return new Input(in.hx(), in.hy(), pvx, pvy, in.heading(), in.parentRate(), in.centre(), in.tractorHeading(),
                    in.wx(), in.wy(), in.link(), in.lockDeg());
        }
        Input bar = new Input(px, py, pvx, pvy, Math.atan2(-ry, -rx), in.parentRate(), in.centre(), in.tractorHeading(),
                new double[]{in.hx()}, new double[]{in.hy()}, in.link(), in.lockDeg());
        double omega = desiredRate(bar);

        return new Input(in.hx(), in.hy(), pvx - omega * ry, pvy + omega * rx, in.heading(), omega, in.centre(),
                in.tractorHeading(), in.wx(), in.wy(), in.link(), in.lockDeg());
    }

    public static double[] angles(Input in) {
        int n = in.wx() == null ? 0 : in.wx().length;
        double[] out = new double[n];
        if (n == 0) return out;
        double lock = WheelForce.clamp(in.lockDeg(), 0.0, GroundSteer.MOUNT_LOCK_DEG);
        if (in.link().rigid()) {
            for (int i = 0; i < n; i++) out[i] = clampDeg(rigidAngle(in, in.wx()[i], in.wy()[i]), lock);
            return out;
        }
        double speed = Math.hypot(in.vx(), in.vy());
        if (speed < MIN_SPEED) return null;
        double omega = desiredRate(in);
        for (int i = 0; i < n; i++) {
            double rx = in.wx()[i] - in.hx();
            double ry = in.wy()[i] - in.hy();

            double ax = in.vx() - omega * ry;
            double ay = in.vy() + omega * rx;
            out[i] = clampDeg(wheelDeg(Math.atan2(ay, ax), in.heading()), lock);
        }
        return out;
    }

    public static double desiredRate(Input in) {
        if (in.link().rigid()) return in.parentRate();
        double tx = Math.cos(in.heading());
        double ty = Math.sin(in.heading());
        double along = in.vx() * tx + in.vy() * ty;
        double s = along < 0.0 ? -1.0 : 1.0;
        double dx = s * in.vx();
        double dy = s * in.vy();
        double phi = wrapPi(Math.atan2(dy, dx) - in.heading());
        double gain = in.link().holds() ? HITCH_GAIN : FLEX_GAIN;
        return in.parentRate() + gain * wrapPi(phi - target(in, dx, dy, tx, ty));
    }

    static double target(Input in, double dx, double dy, double tx, double ty) {
        if (in.centre() == null) return 0.0;
        double reach = 0.0;
        for (int i = 0; i < in.wx().length; i++) {
            reach = Math.max(reach, (in.hx() - in.wx()[i]) * tx + (in.hy() - in.wy()[i]) * ty);
        }
        double cx = in.centre()[0] - in.hx();
        double cy = in.centre()[1] - in.hy();
        double radius = Math.hypot(cx, cy);
        if (reach <= 1.0e-6 || radius <= 1.0e-6) return 0.0;
        double ratio = Math.min(MAX_TRACK_RATIO, reach / (2.0 * radius));

        double side = Math.signum(dx * cy - dy * cx);
        return side * Math.asin(ratio);
    }

    static double rigidAngle(Input in, double wx, double wy) {
        double dir;
        if (in.centre() == null) {
            dir = in.tractorHeading();
        } else {
            double rx = wx - in.centre()[0];
            double ry = wy - in.centre()[1];
            if (Math.hypot(rx, ry) < 1.0e-6) return 0.0;

            dir = Math.atan2(rx, -ry);
        }
        return wheelDeg(dir, in.heading());
    }

    static double wheelDeg(double direction, double heading) {
        double delta = wrapPi(direction - heading);
        if (delta > Math.PI / 2) delta -= Math.PI;
        else if (delta <= -Math.PI / 2) delta += Math.PI;
        return -Math.toDegrees(delta);
    }

    public static double wrapPi(double a) {
        double r = a % (2.0 * Math.PI);
        if (r > Math.PI) r -= 2.0 * Math.PI;
        else if (r <= -Math.PI) r += 2.0 * Math.PI;
        return r;
    }

    private static double clampDeg(double deg, double lock) {
        if (!Double.isFinite(deg)) return 0.0;
        return Math.max(-lock, Math.min(lock, deg));
    }
}
