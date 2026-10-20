package com.createstats.vccmk4.core;

public final class WheelForce {
    public static final double DRIVE_PER_SPEED = 1.75;

    public static final double ROLLING = 0.075;

    public static final double BRAKING = 0.3;

    public static final double STRENGTH_SCALE = 20.0;

    public static final double DEFAULT_STRENGTH = 10.0;

    public record Wheel(boolean driven, double surface, double capMass) {
        public Wheel {
            surface = clamp(surface, 0.0, 1.0);
            capMass = Math.max(0.0, capMass);
        }

        public static Wheel typical(boolean driven) {
            return new Wheel(driven, 1.0, DEFAULT_STRENGTH);
        }
    }

    private WheelForce() {
    }

    public static double drive(double mountSpeed, double brake, double surface) {
        return DRIVE_PER_SPEED * mountSpeed * (1.0 - clamp(brake, 0.0, 1.0)) * clamp(surface, 0.0, 1.0);
    }

    public static double dragPerSpeed(Wheel w, double brake) {
        return (ROLLING + BRAKING * clamp(brake, 0.0, 1.0)) * w.surface() * STRENGTH_SCALE * w.capMass();
    }

    public static double rolling(Iterable<Wheel> wheels) {
        double k = 0.0;
        for (Wheel w : wheels) k += dragPerSpeed(w, 0.0);
        return k;
    }

    public static double brakeCapacity(Iterable<Wheel> wheels) {
        double k = 0.0;
        for (Wheel w : wheels) k += BRAKING * w.surface() * STRENGTH_SCALE * w.capMass();
        return k;
    }

    public static double drivePerSpeed(Iterable<Wheel> wheels) {
        double k = 0.0;
        for (Wheel w : wheels) if (w.driven()) k += DRIVE_PER_SPEED * w.surface();
        return k;
    }

    public static double capMass(double normalMass, double strength) {
        double s = strength > 0.0 ? strength : DEFAULT_STRENGTH;
        if (!(normalMass > 0.0) || !Double.isFinite(normalMass)) return s;
        return Math.min(normalMass, s);
    }

    // far from the CoM a push tips more than it lifts
    public static double normalMass(double mass, double[] inertia, double rx, double ry, double rz) {
        if (!(mass > 0.0) || inertia == null || inertia.length < 9) return Double.NaN;
        double[] inv = invert3(inertia);
        if (inv == null) return Double.NaN;

        double cx = -rz;
        double cz = rx;

        double tx = inv[0] * cx + inv[6] * cz;
        double tz = inv[2] * cx + inv[8] * cz;
        double inverse = cx * tx + cz * tz + 1.0 / mass;
        return inverse > 0.0 && Double.isFinite(inverse) ? 1.0 / inverse : Double.NaN;
    }

    static double[] invert3(double[] m) {
        double a = m[0], b = m[3], c = m[6];
        double d = m[1], e = m[4], f = m[7];
        double g = m[2], h = m[5], i = m[8];
        double co00 = e * i - f * h;
        double co01 = -(d * i - f * g);
        double co02 = d * h - e * g;
        double det = a * co00 + b * co01 + c * co02;
        if (Math.abs(det) < 1.0e-12 || !Double.isFinite(det)) return null;
        double inv = 1.0 / det;

        double r00 = co00 * inv;
        double r01 = -(b * i - c * h) * inv;
        double r02 = (b * f - c * e) * inv;
        double r10 = co01 * inv;
        double r11 = (a * i - c * g) * inv;
        double r12 = -(a * f - c * d) * inv;
        double r20 = co02 * inv;
        double r21 = -(a * h - b * g) * inv;
        double r22 = (a * e - b * d) * inv;
        return new double[]{r00, r10, r20, r01, r11, r21, r02, r12, r22};
    }

    static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
