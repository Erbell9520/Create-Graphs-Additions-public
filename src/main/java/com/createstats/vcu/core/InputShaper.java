package com.createstats.vcu.core;

public final class InputShaper {
    public static final double LATCH_PER_TICK = 0.02;

    public record Demand(double[] axes, double wheelThrottle, double wheelSteer, double lift, boolean boost) {
    }

    private double collective;
    private double throttle;

    public double collective() {
        return collective;
    }

    public double throttle() {
        return throttle;
    }

    public void reset() {
        collective = 0.0;
        throttle = 0.0;
    }

    public Demand shape(Scheme scheme, int bits, boolean hasForwardThrust) {
        double fwd = PilotInput.axis(bits, PilotInput.FORWARD, PilotInput.BACK);
        double side = PilotInput.axis(bits, PilotInput.RIGHT, PilotInput.LEFT);
        double vertical = PilotInput.axis(bits, PilotInput.UP, PilotInput.DOWN);
        double aux = PilotInput.axis(bits, PilotInput.AUX_RIGHT, PilotInput.AUX_LEFT);
        boolean boost = PilotInput.held(bits, PilotInput.BOOST);

        double[] axes = new double[Axis6.COUNT];
        double wheelThrottle;
        double wheelSteer;
        switch (scheme) {
            case PLANE -> {
                throttle = Allocator.clamp(throttle + vertical * LATCH_PER_TICK, 0.0, 1.0);
                axes[Axis6.FORWARD.index()] = throttle;

                axes[Axis6.PITCH.index()] = -fwd;
                axes[Axis6.ROLL.index()] = side;
                axes[Axis6.YAW.index()] = aux;
                wheelThrottle = throttle;

                wheelSteer = aux != 0.0 ? aux : side;
            }
            case HOVER -> {
                collective = Allocator.clamp(collective + vertical * LATCH_PER_TICK, 0.0, 1.0);
                axes[Axis6.UP.index()] = collective;
                if (hasForwardThrust) {
                    axes[Axis6.FORWARD.index()] = fwd;
                } else {
                    axes[Axis6.PITCH.index()] = -fwd;
                }
                axes[Axis6.ROLL.index()] = side;
                axes[Axis6.YAW.index()] = aux;
                wheelThrottle = fwd;
                wheelSteer = side;
            }
            case AIRSHIP -> {
                collective = Allocator.clamp(collective + vertical * LATCH_PER_TICK, 0.0, 1.0);
                axes[Axis6.UP.index()] = collective;
                axes[Axis6.FORWARD.index()] = fwd;
                axes[Axis6.YAW.index()] = side;
                axes[Axis6.RIGHT.index()] = aux;
                wheelThrottle = fwd;
                wheelSteer = side;
            }
            default -> {
                collective = Allocator.clamp(collective + vertical * LATCH_PER_TICK, 0.0, 1.0);
                axes[Axis6.UP.index()] = collective;
                axes[Axis6.FORWARD.index()] = fwd;
                axes[Axis6.YAW.index()] = side;
                axes[Axis6.RIGHT.index()] = aux;
                wheelThrottle = fwd;
                wheelSteer = side;
            }
        }
        return new Demand(axes, wheelThrottle, wheelSteer, collective, boost);
    }
}
