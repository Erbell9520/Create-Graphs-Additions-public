package com.createstats.vacuum;

public final class HoseTether {
    private HoseTether() {
    }

    public static final double LENGTH = 8.0;

    public static final double STIFFNESS = 0.7;

    public static double distance(double[] port, double[] head) {
        final double dx = head[0] - port[0];
        final double dy = head[1] - port[1];
        final double dz = head[2] - port[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static boolean reaches(double[] port, double[] head) {
        return distance(port, head) <= LENGTH;
    }

    public static double sag(double span, double floorClearance) {
        final double s = Math.max(0.0, Math.min(span, LENGTH));
        final double v = 0.5 * Math.sqrt(Math.max(0.0, LENGTH * LENGTH - s * s));
        return Math.max(0.0, Math.min(v * STIFFNESS, floorClearance));
    }

    public static double[] point(double[] port, double[] head, double sagDepth, double t) {
        final double dip = 4.0 * t * (1.0 - t) * sagDepth;
        return new double[] {
                port[0] + (head[0] - port[0]) * t,
                port[1] + (head[1] - port[1]) * t - dip,
                port[2] + (head[2] - port[2]) * t};
    }
}
