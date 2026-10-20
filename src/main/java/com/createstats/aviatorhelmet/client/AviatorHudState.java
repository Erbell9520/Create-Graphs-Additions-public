package com.createstats.aviatorhelmet.client;

import com.createstats.aviatorhelmet.AerialHudPacket;

// ease between packets, 4/s looks like a slideshow otherwise
final class AviatorHudState {
    static final double STEADY = 0.62;
    static final double ACTIVE = 1.0;
    static final double SMOOTH_S = 0.18;
    static final double ATTENTION_S = 0.25;

    double heading = Double.NaN;
    double altitude = Double.NaN;
    double climb;
    double order;
    double engine;
    double speed;
    double roll;
    double pitch;
    double lift = Double.NaN;

    double compassGlow = STEADY;
    double altGlow = STEADY;
    double telegraphGlow = STEADY;
    double attitudeGlow = STEADY;

    private long lastNanos;

    void update(AerialHudPacket p, long nanos) {
        final double dt = lastNanos == 0L ? 1.0 : Math.min(1.0, Math.max(0.0, (nanos - lastNanos) / 1.0e9));
        lastNanos = nanos;
        final double k = Math.min(1.0, dt / SMOOTH_S);
        heading = angle(heading, p.heading(), k);
        altitude = ease(altitude, p.altitude(), k);
        climb = ease(climb, p.climb(), k);
        order = ease(order, p.order(), Math.min(1.0, dt / 0.08));
        engine = ease(engine, p.engine(), k);
        speed = ease(speed, p.speed(), k);
        roll = ease(roll, p.roll(), k);
        pitch = ease(pitch, p.pitch(), k);
        lift = ease(lift, p.lift(), k);

        final double a = Math.min(1.0, dt / ATTENTION_S);
        compassGlow += ((compassActive(p) ? ACTIVE : STEADY) - compassGlow) * a;
        altGlow += ((altActive(p) ? ACTIVE : STEADY) - altGlow) * a;
        telegraphGlow += ((Math.abs(p.order() - p.engine()) > 0.02 ? ACTIVE : STEADY) - telegraphGlow) * a;
        attitudeGlow += ((Math.abs(p.roll()) > 4.0 || Math.abs(p.pitch()) > 4.0 ? ACTIVE : STEADY) - attitudeGlow) * a;
    }

    static boolean compassActive(AerialHudPacket p) {
        if (Float.isNaN(p.heldHeading())) {
            return true;
        }
        if (Math.abs(wrap(p.heading() - p.heldHeading())) > 3.0) {
            return true;
        }
        return !Float.isNaN(p.targetBearing()) && Math.abs(wrap(p.targetBearing() - p.heading())) > 10.0;
    }

    static boolean altActive(AerialHudPacket p) {
        return Math.abs(p.climb()) > 0.3
                || (!Float.isNaN(p.heldAltitude()) && Math.abs(p.heldAltitude() - p.altitude()) > 1.5);
    }

    private static double ease(double from, double to, double k) {
        if (!Double.isFinite(to)) {
            return to;
        }
        return Double.isFinite(from) ? from + (to - from) * k : to;
    }

    private static double angle(double from, double to, double k) {
        if (!Double.isFinite(to)) {
            return to;
        }
        if (!Double.isFinite(from)) {
            return to;
        }
        return normalise(from + wrap(to - from) * k);
    }

    static double wrap(double deg) {
        double d = deg % 360.0;
        if (d > 180.0) {
            d -= 360.0;
        } else if (d < -180.0) {
            d += 360.0;
        }
        return d;
    }

    static double normalise(double deg) {
        final double d = deg % 360.0;
        return d < 0.0 ? d + 360.0 : d;
    }
}
