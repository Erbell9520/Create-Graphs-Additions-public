package com.createstats.seismic;

public final class SeismicGeometry {
    public static final double WEIGHT_BOTTOM = 1.5;

    public static final double EYE_TOP = 9.0;

    public static final double LIFT = 13.0;

    public static final double CABLE_X = 8.0;
    public static final double CABLE_Z = 5.875;

    public static final double SHEAVE_X = 10.0;
    public static final double SHEAVE_Y = 27.5;
    public static final double SHEAVE_Z = 5.875;

    public static final double DROP_X = 12.0;

    public static final double SHEAVE_PITCH_R = (DROP_X - CABLE_X) / 2.0;

    public static final double ROPE_LAY = 2.0;

    public static final double DRUM_Y = 4.5;
    public static final double DRUM_Z = 7.944;

    public static final int TEETH = 8;

    public static final double RATCHET_REST_DEG = 1.84;

    public static final double PAWL_X = 14.575;
    public static final double PAWL_Y = 7.85;
    public static final double PAWL_Z = 6.594;
    public static final double PAWL_LIFT_DEG = 30.0;

    public static final double PAWL_RIDE_DEG = 21.8;

    public static final double RIDE_FROM = 0.08;
    public static final double RIDE_TOP = 0.881;
    public static final double RIDE_TO = 0.995;

    public static final double RIDE_CURVE = 0.9;

    public static final double LEVER_X = 15.45;
    public static final double LEVER_Y = 6.25;
    public static final double LEVER_Z = 7.944;
    public static final double LEVER_PULL_DEG = 38.0;

    public static final double[] LEVER_BOX = {14.55, 5.65, 4.19, 16.0, 13.74, 8.85};

    public static final double PAPER_X = 2.625;
    public static final double PAPER_Z = 2.25;
    public static final double PAPER_R = 1.45;
    public static final double PAPER_Y0 = 4.1;
    public static final double PAPER_Y1 = 7.4;

    public static final double PEN_DEG = Math.toDegrees(Math.atan2(3.85 - PAPER_X, -(1.5 - PAPER_Z)));

    public static final double PEN_TRAVEL = 1.45;

    public static final double PAPER_DEG_PER_TICK = 1.45;
    public static final double PAPER_LAST_DEG = -88.0;

    public static final double[] CELL = {2.75, 1.25, 4.25, 2.75};
    public static final double CELL_Z = 0.55;

    public static final double LAMP_X = 13.85;
    public static final double LAMP_Y = 27.05;
    public static final double LAMP_Z = 5.875;

    public static final double PLATE_X = 7.875;
    public static final double PLATE_Y = 1.5;
    public static final double PLATE_Z = 5.875;

    private SeismicGeometry() {
    }

    public static double weightRise(double height) {
        return LIFT * Math.max(0.0, Math.min(1.0, height));
    }

    public static double drumDegrees(double height) {
        return RATCHET_REST_DEG - 360.0 * height;
    }

    public static double sheaveDegrees(double height) {
        return -Math.toDegrees(LIFT * height / SHEAVE_PITCH_R);
    }

    public static double pawlRide(double height) {
        final double f = height * TEETH - Math.floor(height * TEETH);
        if (f <= RIDE_FROM) {
            return 0.0;
        }
        if (f < RIDE_TOP) {
            final double u = (f - RIDE_FROM) / (RIDE_TOP - RIDE_FROM);
            return 1.0 - Math.pow(1.0 - u, RIDE_CURVE);
        }
        return f < RIDE_TO ? 1.0 : 0.0;
    }

    public static int tooth(double height) {
        return (int) Math.floor(Math.max(0.0, height) * TEETH + 1.0e-9);
    }

    public static double[] toNorth(int quarter, double x, double z) {
        return switch (Math.floorMod(quarter, 4)) {
            case 1 -> new double[] {z, 16.0 - x};
            case 2 -> new double[] {16.0 - x, 16.0 - z};
            case 3 -> new double[] {16.0 - z, x};
            default -> new double[] {x, z};
        };
    }

    public static final double[] RECORDER_BOX = {0.0, 0.0, 0.0, 5.5, 9.0, 7.25};

    public static boolean onRecorder(int quarter, double x, double y, double z) {
        final double[] n = toNorth(quarter, x, z);
        final double[] b = RECORDER_BOX;
        return n[0] >= b[0] && n[0] <= b[3] && y >= b[1] && y <= b[4] && n[1] >= b[2] && n[1] <= b[5];
    }

    public static boolean onLever(int quarter, double x, double y, double z) {
        final double[] n = toNorth(quarter, x, z);
        final double[] b = LEVER_BOX;
        return n[0] >= b[0] && n[0] <= b[3] && y >= b[1] && y <= b[4] && n[1] >= b[2] && n[1] <= b[5];
    }
}
