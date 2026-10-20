package com.createstats.vcu.core;

public record V3(double x, double y, double z) {
    public static final V3 ZERO = new V3(0.0, 0.0, 0.0);
    public static final V3 UNIT_X = new V3(1.0, 0.0, 0.0);
    public static final V3 UNIT_Y = new V3(0.0, 1.0, 0.0);
    public static final V3 UNIT_Z = new V3(0.0, 0.0, 1.0);

    public V3 add(V3 o) {
        return new V3(x + o.x, y + o.y, z + o.z);
    }

    public V3 sub(V3 o) {
        return new V3(x - o.x, y - o.y, z - o.z);
    }

    public V3 scale(double k) {
        return new V3(x * k, y * k, z * k);
    }

    public double dot(V3 o) {
        return x * o.x + y * o.y + z * o.z;
    }

    public V3 cross(V3 o) {
        return new V3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
    }

    public double length() {
        return Math.sqrt(dot(this));
    }

    public V3 normalized() {
        double l = length();
        return l < 1.0e-12 ? ZERO : scale(1.0 / l);
    }

    public V3 rejectFrom(V3 unitAxis) {
        return sub(unitAxis.scale(dot(unitAxis)));
    }

    public boolean isFinite() {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }
}
