package com.createstats.vccmk4.core;

public final class RouteDrive {
    public static final double THROTTLE_SLEW = 0.1;

    public static final double STEER_SLEW = 0.2;

    private double throttle;
    private double steer;

    public void reset() {
        throttle = 0.0;
        steer = 0.0;
    }

    public void follow(DriveModel.Output out) {
        throttle = out.throttle();
        steer = out.steer();
    }

    public DriveModel.Output step(DriveModel.Options options, double throttleCmd, double steerCmd, double brakeCmd,
                                  double forwardSpeed, boolean hold) {
        double t = GroundSteer.clamp(throttleCmd, -1.0, 1.0);
        double b = GroundSteer.clamp(brakeCmd, 0.0, 1.0);

        if (throttle * t < 0.0) throttle = 0.0;
        throttle += GroundSteer.clamp(t - throttle, -THROTTLE_SLEW, THROTTLE_SLEW);
        if (Math.abs(throttle) < 1.0e-9) throttle = 0.0;
        double s = GroundSteer.clamp(steerCmd, -1.0, 1.0);
        steer += GroundSteer.clamp(s - steer, -STEER_SLEW, STEER_SLEW);
        double lock = DriveModel.lockAt(options, forwardSpeed);
        boolean braking = b > 0.0;
        return new DriveModel.Output(throttle, steer, braking, false, lock, throttle < 0.0 ? -1.0 : 1.0, hold && braking, b);
    }
}
