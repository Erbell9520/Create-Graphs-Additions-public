package com.createstats.vehiclelight;

public final class HeadlightState {
    public static final int PROBE_TICKS = 10;

    public static final double PROBE_DEPTH = 8.0;
    public static final double MIN_HEIGHT = 0.25;
    public static final double MAX_HEIGHT = 6.0;

    public static final double EASE_SECONDS = 0.4;

    public static final double LOOK_AHEAD = 0.6;

    public static final double MAX_BEND = 15.0;

    public static final double TURN_DEADBAND = 3.0;

    private static final double HEIGHT_EASE = 1.0 - Math.exp(-1.0 / (20.0 * EASE_SECONDS));
    private static final double RATE_EASE = 0.3;
    private static final double SPEED_EASE = 0.5;

    private double height = Double.NaN;
    private double target = Double.NaN;
    private int probeIn;

    private double heading = Double.NaN;
    private double yawRate;

    private double lastX = Double.NaN;
    private double lastY;
    private double lastZ;
    private double speed;

    private long cutShow = Long.MIN_VALUE;

    private double down = Double.NaN;
    private double left;
    private double prevDown = Double.NaN;
    private double prevLeft;

    public boolean probeDue() {
        if (--this.probeIn > 0) {
            return false;
        }
        this.probeIn = PROBE_TICKS;
        return true;
    }

    public void measured(double h) {
        if (!Double.isFinite(h)) {
            return;
        }
        this.target = Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, h));
        if (Double.isNaN(this.height)) {
            this.height = this.target;
        }
    }

    public double height() {
        return this.height;
    }

    public void tick() {
        if (!Double.isNaN(this.target) && !Double.isNaN(this.height)) {
            this.height += (this.target - this.height) * HEIGHT_EASE;
        }
    }

    public void travelled(double x, double y, double z) {
        if (!Double.isNaN(this.lastX)) {
            final double dx = x - this.lastX;
            final double dy = y - this.lastY;
            final double dz = z - this.lastZ;
            final double now = Math.sqrt(dx * dx + dy * dy + dz * dz) * 20.0;
            this.speed += (now - this.speed) * SPEED_EASE;
        }
        this.lastX = x;
        this.lastY = y;
        this.lastZ = z;
    }

    public double speed() {
        return this.speed;
    }

    public void heading(double radians) {
        if (Double.isNaN(radians)) {
            this.heading = Double.NaN;
            this.yawRate = 0.0;
            return;
        }
        if (!Double.isNaN(this.heading)) {
            double step = radians - this.heading;
            step = Math.atan2(Math.sin(step), Math.cos(step));
            this.yawRate += (step * 20.0 - this.yawRate) * RATE_EASE;
        }
        this.heading = radians;
    }

    public double bend() {
        final double rate = Math.toDegrees(this.yawRate);
        if (Math.abs(rate) < TURN_DEADBAND) {
            return 0.0;
        }
        return Math.max(-MAX_BEND, Math.min(MAX_BEND, rate * LOOK_AHEAD));
    }

    public void cut(long start) {
        this.cutShow = start;
    }

    public boolean isCut(long start) {
        return this.cutShow == start;
    }

    public void aimed(double downDeg, double leftDeg) {
        this.prevDown = Double.isNaN(this.down) ? downDeg : this.down;
        this.prevLeft = Double.isNaN(this.down) ? leftDeg : this.left;
        this.down = downDeg;
        this.left = leftDeg;
    }

    public double downAt(float partialTick) {
        return this.prevDown + (this.down - this.prevDown) * partialTick;
    }

    public double leftAt(float partialTick) {
        return this.prevLeft + (this.left - this.prevLeft) * partialTick;
    }
}
