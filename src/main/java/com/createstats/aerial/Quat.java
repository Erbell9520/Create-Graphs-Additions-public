package com.createstats.aerial;

public record Quat(double x, double y, double z, double w) {
    public static final Quat IDENTITY = new Quat(0.0, 0.0, 0.0, 1.0);

    public static Quat axisAngle(V3 axis, double radians) {
        final V3 a = axis.normalized();
        final double s = Math.sin(radians * 0.5);
        return new Quat(a.x() * s, a.y() * s, a.z() * s, Math.cos(radians * 0.5));
    }

    public Quat normalized() {
        final double n = Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z + this.w * this.w);
        if (!(n > 1.0e-12) || !Double.isFinite(n)) {
            return IDENTITY;
        }
        return new Quat(this.x / n, this.y / n, this.z / n, this.w / n);
    }

    public Quat mul(Quat o) {
        return new Quat(
                this.w * o.x + this.x * o.w + this.y * o.z - this.z * o.y,
                this.w * o.y - this.x * o.z + this.y * o.w + this.z * o.x,
                this.w * o.z + this.x * o.y - this.y * o.x + this.z * o.w,
                this.w * o.w - this.x * o.x - this.y * o.y - this.z * o.z);
    }

    public Quat conjugate() {
        return new Quat(-this.x, -this.y, -this.z, this.w);
    }

    public V3 rotate(V3 v) {
        final Quat q = normalized();

        final V3 u = new V3(q.x, q.y, q.z);
        final V3 t = u.cross(v).times(2.0);
        return v.plus(t.times(q.w)).plus(u.cross(t));
    }

    public V3 inverseRotate(V3 v) {
        return normalized().conjugate().rotate(v);
    }

    public Quat slerp(Quat o, double t) {
        final Quat a = normalized();
        Quat b = o.normalized();
        double dot = a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w;
        if (dot < 0.0) {
            b = new Quat(-b.x, -b.y, -b.z, -b.w);
            dot = -dot;
        }
        if (dot > 0.9995) {
            return new Quat(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t,
                    a.z + (b.z - a.z) * t, a.w + (b.w - a.w) * t).normalized();
        }
        final double theta = Math.acos(Math.min(1.0, dot));
        final double sin = Math.sin(theta);
        final double wa = Math.sin((1.0 - t) * theta) / sin;
        final double wb = Math.sin(t * theta) / sin;
        return new Quat(a.x * wa + b.x * wb, a.y * wa + b.y * wb, a.z * wa + b.z * wb, a.w * wa + b.w * wb);
    }
}
