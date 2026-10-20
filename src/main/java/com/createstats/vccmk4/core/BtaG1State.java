package com.createstats.vccmk4.core;

// seven bytes, everyone near sees the wheel turn
public record BtaG1State(int flags, int throttleQ, int steerQ, int brakeQ, int speedQ) {
    public static final int DRIVING = 1;

    public static final int ROUTE = 2;

    public static final int BRAKING = 4;

    public static final int HOLDING = 8;

    public static final int PARKED = 16;
    public static final int BOOSTING = 32;
    public static final int REVERSING = 64;

    public static final int SIGNAL_LEFT = 128;
    public static final int SIGNAL_RIGHT = 256;

    public static final int GREETING = 512;

    public static final int SEATED = 1024;

    public static final double KMH_PER_BPS = 3.6;

    public static final double REST_BPS = 0.05;

    public static final int BYTES = 7;

    private static final double SPEED_STEPS_PER_KMH = 8.0;
    private static final int STICK = 127;

    public static final BtaG1State IDLE = new BtaG1State(0, 0, 0, 0, 0);

    public static BtaG1State of(boolean driving, boolean route, boolean braking, boolean holding, boolean parked,
                                boolean boosting, boolean reversing, int indicate,
                                double throttle, double steer, double brake, double speedBps) {
        int f = 0;
        if (driving) f |= DRIVING;
        if (route) f |= ROUTE;
        if (braking) f |= BRAKING;
        if (holding) f |= HOLDING;
        if (parked) f |= PARKED;
        if (boosting) f |= BOOSTING;
        if (reversing) f |= REVERSING;
        if (indicate < 0) f |= SIGNAL_LEFT;
        if (indicate > 0) f |= SIGNAL_RIGHT;
        double bps = Math.abs(speedBps) < REST_BPS || !Double.isFinite(speedBps) ? 0.0 : speedBps;
        long speed = Math.round(bps * KMH_PER_BPS * SPEED_STEPS_PER_KMH);
        return new BtaG1State(f, stick(throttle, -STICK), stick(steer, -STICK), stick(brake, 0),
                (int) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, speed)));
    }

    private static int stick(double v, int min) {
        if (!Double.isFinite(v)) return 0;
        return (int) Math.max(min, Math.min(STICK, Math.round(v * STICK)));
    }

    public boolean has(int flag) {
        return (flags & flag) != 0;
    }

    public BtaG1State with(int flag, boolean on) {
        return new BtaG1State(on ? flags | flag : flags & ~flag, throttleQ, steerQ, brakeQ, speedQ);
    }

    public boolean driving() {
        return has(DRIVING);
    }

    public boolean seated() {
        return has(SEATED);
    }

    public boolean greeting() {
        return has(GREETING);
    }

    public boolean route() {
        return has(ROUTE);
    }

    public boolean braking() {
        return has(BRAKING);
    }

    public boolean holding() {
        return has(HOLDING);
    }

    public boolean parked() {
        return has(PARKED);
    }

    public boolean boosting() {
        return has(BOOSTING);
    }

    public boolean reversing() {
        return has(REVERSING);
    }

    public double throttle() {
        return throttleQ / (double) STICK;
    }

    public double steer() {
        return steerQ / (double) STICK;
    }

    public double brake() {
        return brakeQ / (double) STICK;
    }

    public double speedKmh() {
        return speedQ / SPEED_STEPS_PER_KMH;
    }

    public int indicate() {
        return has(SIGNAL_LEFT) ? -1 : has(SIGNAL_RIGHT) ? 1 : 0;
    }

    public byte[] encode() {
        return new byte[]{
                (byte) flags, (byte) (flags >> 8), (byte) throttleQ, (byte) steerQ, (byte) brakeQ,
                (byte) (speedQ >> 8), (byte) speedQ};
    }

    public static BtaG1State decode(byte[] b) {
        if (b == null || b.length < BYTES) return IDLE;
        int flags = (b[0] & 0xFF) | ((b[1] & 0xFF) << 8);
        int speed = (short) (((b[5] & 0xFF) << 8) | (b[6] & 0xFF));
        return new BtaG1State(flags, clampStick(b[2], -STICK), clampStick(b[3], -STICK), clampStick(b[4], 0), speed);
    }

    private static int clampStick(int v, int min) {
        return Math.max(min, Math.min(STICK, v));
    }
}
