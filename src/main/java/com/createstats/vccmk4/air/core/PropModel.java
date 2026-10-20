package com.createstats.vccmk4.air.core;

public final class PropModel {
    public static final double DEFAULT_MAX_RPM = 256.0;

    private PropModel() {
    }

    public static double rpmFor(double force, double gain, double flow, double speed, double pressure, double maxRpm) {
        if (!(gain > 0.0) || !(pressure > 0.0) || !Double.isFinite(force) || Math.abs(force) < 1.0e-9) return 0.0;
        int way = force > 0.0 ? 1 : -1;
        double rpm = Math.abs(force) / (gain * pressure) + slip(way, flow, speed);
        return way * Math.min(maxRpm, rpm);
    }

    public static double force(double rpm, double gain, double flow, double speed, double pressure) {
        if (!(gain > 0.0) || !(pressure > 0.0) || !Double.isFinite(rpm) || rpm == 0.0) return 0.0;
        int way = rpm > 0.0 ? 1 : -1;
        return way * gain * pressure * Math.max(0.0, Math.abs(rpm) - slip(way, flow, speed));
    }

    public static double maxForce(int way, double gain, double flow, double speed, double pressure, double maxRpm) {
        if (!(gain > 0.0) || !(pressure > 0.0)) return 0.0;
        return gain * pressure * Math.max(0.0, maxRpm - slip(way, flow, speed));
    }

    static double slip(int way, double flow, double speed) {
        double along = way > 0 ? speed : -speed;
        return flow > 1.0e-9 && along > 0.0 && Double.isFinite(along) ? along / flow : 0.0;
    }
}
