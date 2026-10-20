package com.createstats.powertrack.core;

// drawn as the rest it teleports at speed
public final class CollectorSwing {
    public static final double FALL_DEG_S2 = 3600.0;

    public static final double BOUNCE = 0.5;

    public static final double SETTLE_SPEED = 80.0;

    public static final double MAX_STEP = 0.1;

    private double angle = Double.NaN;

    private double speed;
    private double lastTime = Double.NaN;

    public double step(double timeSeconds, double rest, boolean touching) {
        final double target = Math.max(CollectorPose.STOP_DEG, rest);
        if (Double.isNaN(angle) || Double.isNaN(lastTime) || timeSeconds < lastTime) {
            angle = target;
            speed = 0.0;
            lastTime = timeSeconds;
            return angle;
        }
        final double dt = Math.min(MAX_STEP, timeSeconds - lastTime);
        lastTime = timeSeconds;
        if (touching) {
            speed = dt > 0.0 ? Math.min(0.0, (target - angle) / dt) : 0.0;
            angle = target;
            return angle;
        }

        speed -= FALL_DEG_S2 * dt;
        final double free = angle + speed * dt;
        if (free > CollectorPose.STOP_DEG) {
            angle = free;
            return angle;
        }
        final double strike = -speed;
        angle = CollectorPose.STOP_DEG;
        speed = strike > SETTLE_SPEED ? strike * BOUNCE : 0.0;
        return angle;
    }

    public double angle() {
        return angle;
    }
}
