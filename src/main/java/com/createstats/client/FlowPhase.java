package com.createstats.client;

// phase += speed*dt, never time*speed. world time is huge and a speed change jumps the wave
public final class FlowPhase {
    private static final double TURN = Math.PI * 2.0;

    private double phase;

    private double at = Double.NaN;

    public double advance(double now, double radiansPerTick) {
        if (Double.isNaN(at) || now < at) {
            phase = wrap(now * radiansPerTick);
        } else {
            phase = wrap(phase + (now - at) * radiansPerTick);
        }
        at = now;
        return phase;
    }

    private static double wrap(double radians) {
        final double turns = radians % TURN;
        return turns < 0.0 ? turns + TURN : turns;
    }
}
