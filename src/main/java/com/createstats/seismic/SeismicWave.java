package com.createstats.seismic;

public final class SeismicWave {
    public static final double BLOCKS_PER_SECOND = 32.0;

    public static final double BLOCKS_PER_TICK = BLOCKS_PER_SECOND / 20.0;

    private SeismicWave() {
    }

    public static double frontAt(double ticks) {
        return ticks <= 0.0 ? 0.0 : ticks * BLOCKS_PER_TICK;
    }

    public static int scanTicks(int radius) {
        return radius <= 0 ? 0 : (int) Math.ceil(radius / BLOCKS_PER_TICK - 1.0e-9);
    }

    public static double echoTicks(double distance) {
        return 2.0 * Math.max(0.0, distance) / BLOCKS_PER_TICK;
    }

    public static int echoTick(double distance) {
        return Math.max(1, (int) Math.ceil(echoTicks(distance) - 1.0e-9));
    }

    public static int listenTicks(int radius) {
        return echoTick(radius);
    }
}
