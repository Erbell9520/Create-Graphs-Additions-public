package com.createstats.aerial;

// immutable on purpose, joml mutates in place and a shared reading got normalised under us once
public record V3(double x, double y, double z) {
    public static final V3 ZERO = new V3(0.0, 0.0, 0.0);
    public static final V3 UP = new V3(0.0, 1.0, 0.0);
    public static final V3 NAN = new V3(Double.NaN, Double.NaN, Double.NaN);

    public static V3 of(double x, double y, double z) {
        return new V3(x, y, z);
    }

    public V3 plus(V3 o) {
        return new V3(this.x + o.x, this.y + o.y, this.z + o.z);
    }

    public V3 minus(V3 o) {
        return new V3(this.x - o.x, this.y - o.y, this.z - o.z);
    }

    public V3 times(double k) {
        return new V3(this.x * k, this.y * k, this.z * k);
    }

    public double dot(V3 o) {
        return this.x * o.x + this.y * o.y + this.z * o.z;
    }

    public V3 cross(V3 o) {
        return new V3(this.y * o.z - this.z * o.y, this.z * o.x - this.x * o.z, this.x * o.y - this.y * o.x);
    }

    public double length() {
        return Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
    }

    public double horizontalLength() {
        return Math.sqrt(this.x * this.x + this.z * this.z);
    }

    public V3 horizontal() {
        return new V3(this.x, 0.0, this.z);
    }

    public V3 normalized() {
        final double len = length();
        return len < 1.0e-9 ? ZERO : new V3(this.x / len, this.y / len, this.z / len);
    }

    public boolean isFinite() {
        return Double.isFinite(this.x) && Double.isFinite(this.y) && Double.isFinite(this.z);
    }

    public V3 lerp(V3 o, double t) {
        return new V3(this.x + (o.x - this.x) * t, this.y + (o.y - this.y) * t, this.z + (o.z - this.z) * t);
    }
}
