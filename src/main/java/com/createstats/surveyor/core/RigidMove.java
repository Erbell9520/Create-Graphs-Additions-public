package com.createstats.surveyor.core;

public record RigidMove(double px, double py, double pz, double turn, double dx, double dy, double dz) {
    public static final RigidMove NONE = new RigidMove(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);

    public static RigidMove between(double fromX, double fromY, double fromZ, double fromHeading,
                                    double toX, double toY, double toZ, double toHeading) {
        return new RigidMove(fromX, fromY, fromZ, PathSpline.wrapAngle(toHeading - fromHeading),
                toX - fromX, toY - fromY, toZ - fromZ);
    }

    public double[] point(double x, double y, double z) {
        double c = Math.cos(turn);
        double s = Math.sin(turn);
        double rx = x - px;
        double rz = z - pz;

        return new double[]{px + rx * c - rz * s + dx, y + dy, pz + rx * s + rz * c + dz};
    }

    public double[] orientation(double qx, double qy, double qz, double qw) {
        double ry = Math.sin(-turn / 2.0);
        double rw = Math.cos(-turn / 2.0);
        double x = rw * qx + ry * qz;
        double y = rw * qy + ry * qw;
        double z = rw * qz - ry * qx;
        double w = rw * qw - ry * qy;
        return new double[]{x, y, z, w};
    }

    public double heading(double h) {
        return PathSpline.wrapAngle(h + turn);
    }

    public double[] box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double[] out = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (int i = 0; i < 8; i++) {
            double[] p = point((i & 1) == 0 ? minX : maxX, (i & 2) == 0 ? minY : maxY, (i & 4) == 0 ? minZ : maxZ);
            for (int a = 0; a < 3; a++) {
                out[a] = Math.min(out[a], p[a]);
                out[a + 3] = Math.max(out[a + 3], p[a]);
            }
        }
        return out;
    }

    public double distance() {
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
