package com.createstats.surveyor.core;

import javax.annotation.Nullable;

public record PathNode(int id, double x, double y, double z, String name, double speedLimit, Corner corner,
                       @Nullable Stop stop) {
    public static final double MAX_SPEED = 40.0;

    public static final int MAX_NAME = 32;

    public PathNode {
        name = name == null ? "" : name;
        corner = corner == null ? Corner.SMOOTH : corner;
    }

    public boolean isStop() {
        return stop != null;
    }

    public PathNode sane() {
        double lim = Double.isFinite(speedLimit) ? Math.max(0.0, Math.min(MAX_SPEED, speedLimit)) : 0.0;
        String n = name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name;
        return new PathNode(id, finite(x), finite(y), finite(z), n, lim, corner, stop == null ? null : stop.sane());
    }

    public PathNode at(double nx, double ny, double nz) {
        return new PathNode(id, nx, ny, nz, name, speedLimit, corner, stop);
    }

    public PathNode withName(String n) {
        return new PathNode(id, x, y, z, n, speedLimit, corner, stop);
    }

    public PathNode withSpeedLimit(double v) {
        return new PathNode(id, x, y, z, name, v, corner, stop);
    }

    public PathNode withCorner(Corner c) {
        return new PathNode(id, x, y, z, name, speedLimit, c, stop);
    }

    public PathNode withStop(@Nullable Stop s) {
        return new PathNode(id, x, y, z, name, speedLimit, corner, s);
    }

    public double distanceSq(double px, double py, double pz) {
        double dx = px - x;
        double dy = py - y;
        double dz = pz - z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static double finite(double v) {
        return Double.isFinite(v) ? Math.max(-3.0e7, Math.min(3.0e7, v)) : 0.0;
    }
}
