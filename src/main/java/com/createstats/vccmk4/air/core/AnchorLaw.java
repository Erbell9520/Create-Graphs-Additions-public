package com.createstats.vccmk4.air.core;

public final class AnchorLaw {
    static final double K_POS = 0.25;

    static final double K_VEL = 0.8;

    public static final double MAX_SPEED = 2.0;

    private AnchorLaw() {
    }

    public static double[] force(double dx, double dz, double vx, double vz, double mass, double accelLimit,
                                 double drag, double pressure) {
        double wx = K_POS * dx;
        double wz = K_POS * dz;
        double w = Math.hypot(wx, wz);
        if (w > MAX_SPEED) {
            wx *= MAX_SPEED / w;
            wz *= MAX_SPEED / w;
        }
        double p = pressure > 0.0 && Double.isFinite(pressure) ? pressure : 1.0;
        double d = Double.isFinite(drag) ? drag : 0.0;
        double speed = Math.hypot(vx, vz);

        double over = speed > 1.0e-9 ? speed - (wx * vx + wz * vz) / speed : 0.0;
        boolean shedding = over > CruiseLaw.SHED_BAND;
        double limit = accelLimit + (shedding && mass > 0.0 ? d * p / mass * speed : 0.0);
        double ax = K_VEL * (wx - vx);
        double az = K_VEL * (wz - vz);
        double a = Math.hypot(ax, az);
        if (a > limit && a > 0.0) {
            ax *= limit / a;
            az *= limit / a;
        }
        double fx = mass * ax + d * p * vx;
        double fz = mass * az + d * p * vz;
        if (shedding) {
            double along = (fx * vx + fz * vz) / speed;
            if (along > 0.0) {
                fx -= along * vx / speed;
                fz -= along * vz / speed;
            }
        }
        return new double[] {fx, fz};
    }
}
