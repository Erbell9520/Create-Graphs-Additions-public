package com.createstats.vcu.core;

public record BodyFrame(V3 forward, V3 up, V3 right, V3 pivot) {
    public static BodyFrame of(V3 forward, V3 up, V3 pivot) {
        V3 f = forward.normalized();
        V3 u = up.rejectFrom(f).normalized();
        return new BodyFrame(f, u, f.cross(u).normalized(), pivot);
    }

    public double[] wrench(V3 position, V3 force) {
        V3 arm = position.sub(pivot);
        V3 m = arm.cross(force);
        return new double[] {
                force.dot(forward), force.dot(up), force.dot(right),
                m.dot(forward), m.dot(right), -m.dot(up)
        };
    }

    public double lateral(V3 position) {
        return position.sub(pivot).dot(right);
    }

    public double longitudinal(V3 position) {
        return position.sub(pivot).dot(forward);
    }
}
