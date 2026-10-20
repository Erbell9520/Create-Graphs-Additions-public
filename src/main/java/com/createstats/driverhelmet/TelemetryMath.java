package com.createstats.driverhelmet;

public final class TelemetryMath {
    static final double SUSPENSION_REST = 0.65;
    static final double AXLE_OFFSET = 0.10833333333333334;

    static final double LIFTED_EXTENSION = 0.65;

    public static final int FULL_BRAKE = 15;

    private TelemetryMath() {
    }

    public static double rollingRpm(double speed, double radius) {
        return radius <= 0.0 ? 0.0 : Math.abs(speed) / (2.0 * Math.PI * radius) * 60.0;
    }

    public static double surfaceSpeed(double rpm, double radius) {
        return Math.abs(rpm) * 2.0 * Math.PI * Math.max(0.0, radius) / 60.0;
    }

    public static boolean airborne(double extension) {
        return extension == LIFTED_EXTENSION;
    }

    public static double load(double extension, double radius) {
        if (airborne(extension) || Double.isNaN(extension) || Double.isNaN(radius)) {
            return 0.0;
        }
        final double spring = Math.max(0.0, Math.min(SUSPENSION_REST, AXLE_OFFSET + extension - radius));
        return (SUSPENSION_REST - spring) / SUSPENSION_REST;
    }

    public static double grip(double friction, boolean airborne) {
        if (airborne) {
            return 0.0;
        }
        return Double.isNaN(friction) ? Double.NaN : Math.max(0.0, Math.min(1.0, friction));
    }

    public static double freeRpm(double appliedRpm, int brakeSignal) {
        final int brake = Math.max(0, Math.min(FULL_BRAKE, brakeSignal));
        return Math.abs(appliedRpm) * (FULL_BRAKE - brake) / FULL_BRAKE;
    }

    public static double actualRpm(double freeRpm, double rollingRpm, double grip, boolean airborne) {
        if (airborne) {
            return Math.abs(freeRpm);
        }
        if (Double.isNaN(grip) || grip >= 1.0) {
            return Math.abs(rollingRpm);
        }
        return Math.abs(freeRpm) + grip * (Math.abs(rollingRpm) - Math.abs(freeRpm));
    }

    public static double slip(double actualRpm, double groundSpeed, double radius) {
        if (radius <= 0.0) {
            return 0.0;
        }
        final double tyre = surfaceSpeed(actualRpm, radius);
        final double ground = Math.abs(groundSpeed);
        final double s = (tyre - ground) / Math.max(0.5, Math.max(tyre, ground));
        return Math.max(-1.0, Math.min(1.0, s));
    }

    public static double verticalG(double[] accel, double[] up, double[] gravity) {
        final double magnitude = Math.sqrt(dot(gravity, gravity));
        final double g = magnitude > 1.0e-6 ? magnitude : 9.81;
        final double[] felt = {accel[0] - gravity[0], accel[1] - gravity[1], accel[2] - gravity[2]};
        return dot(felt, up) / g;
    }

    public static double[] gForce(double[] accel, double[] forward, double[] up, double gravity) {
        final double[] right = cross(forward, up);
        final double g = gravity > 1.0e-6 ? gravity : 9.81;
        return new double[]{dot(accel, right) / g, dot(accel, forward) / g};
    }

    public static double pitch(double[] forward) {
        return Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, forward[1]))));
    }

    public static double roll(double[] forward, double[] up) {
        final double[] right = cross(forward, up);
        return Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, -right[1]))));
    }

    static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }
}
