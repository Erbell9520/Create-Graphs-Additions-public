package com.createstats.vccmk4.air.core;

import com.createstats.vcu.core.Axis6;

import java.util.List;

final class AirPlant {
    static final double G = 11.0;
    static final double DT = AerialPilot.DT;

    final PressureModel air = PressureModel.sable(63.0);

    double capacity = 900.0;
    double gas;
    double[] dials = {500.0, 500.0};
    boolean[] joined = {true, true};
    int[] signals = {0, 0};
    long tick;

    double mass = 600.0;
    double y = 80.0;
    double vy;

    double dragV = 0.6 * 600.0;

    double wing;

    double lean;

    double hidden;

    double deckLoad;

    double x;
    double z;
    double u;
    double w;
    double windX;
    double windZ;
    double heading;
    double yawRate;
    double dragF = 150.0;
    double dragS = 300.0;
    double inertia = 60000.0;
    double yawDamping = 2000.0;

    double forward;
    double side;
    double yawMoment;

    double maxForward = 2000.0;
    double maxReverse = 1000.0;
    double maxSide;
    double maxYaw = 20000.0;

    double groundY = 60.0;
    double keelBelow = 3.0;

    java.util.function.DoubleBinaryOperator ground = (px, pz) -> groundY;

    double groundAt(double px, double pz) {
        return ground.applyAsDouble(px, pz);
    }

    double pressure() {
        return air.at(y);
    }

    TerrainGuard.Profile profile(double ux, double uz, double halfLength, double halfWidth) {
        AerialPilot.Sense s = sense();
        double along = Math.max(0.0, s.vx() * ux + s.vz() * uz);
        return TerrainGuard.read(x, z, ux, uz, keelBelow, halfLength, halfWidth, TerrainGuard.reach(along), this::groundAt);
    }

    void trim() {
        gas = mass * G / (G * GasLaw.STRENGTH * pressure());
    }

    void rest() {
        y = groundAt(x, z) + keelBelow;
        vy = 0.0;
        gas = 0.0;
    }

    double target() {
        double t = 0.0;
        for (int i = 0; i < dials.length; i++) {
            if (signals[i] <= 0) joined[i] = false;
            else if (!joined[i] && tick % 20 == 0) joined[i] = true;
            if (joined[i] && signals[i] > 0) t += dials[i] * signals[i] / 15.0;
        }
        return Math.min(t, capacity);
    }

    void step() {
        gas = GasLaw.step(gas, target(), capacity);
        double p = pressure();
        double airspeed = Math.hypot(u, w);
        double total = mass + Math.max(0.0, deckLoad);
        double fy = G * GasLaw.STRENGTH * gas * p - total * G - dragV * vy * p + wing * airspeed * p
                + lean * forward + hidden;
        double fx = forward - dragF * u * p;
        double fs = side - dragS * w * p;
        double m = yawMoment - yawDamping * yawRate;
        yawRate += m / inertia * (180.0 / Math.PI) * DT;
        heading = HeadingLaw.normalise(heading + yawRate * DT);
        vy += fy / total * DT;
        y += vy * DT;
        u += fx / total * DT;
        w += fs / total * DT;
        double h = Math.toRadians(heading);
        x += (u * Math.sin(h) + w * Math.cos(h) + windX) * DT;
        z += (-u * Math.cos(h) + w * Math.sin(h) + windZ) * DT;
        double under = groundAt(x, z);
        if (y - keelBelow < under) {
            y = under + keelBelow;
            if (vy < 0.0) vy = 0.0;
            u *= 0.8;
            w *= 0.8;
        }
        tick++;
    }

    double maxGas() {
        double s = 0.0;
        for (double d : dials) s += d;
        return Math.min(capacity, s);
    }

    AerialPilot.Sense sense() {
        double p = pressure();
        double h = Math.toRadians(heading);
        double vx = u * Math.sin(h) + w * Math.cos(h) + windX;
        double vz = -u * Math.cos(h) + w * Math.sin(h) + windZ;
        PressureModel fitted = PressureModel.measured(y, p, air.at(y + PressureModel.PROBE), PressureModel.PROBE, 63.0);
        return new AerialPilot.Sense(y, vy, x, z, vx, vz, u, w, heading, yawRate, mass, G, inertia, fitted, p,
                List.of(new AltitudeLaw.Cell(gas, capacity, GasLaw.STRENGTH, p, maxGas())), 0.0,
                y - keelBelow - groundAt(x, z), 320.0,
                new AerialPilot.Thrust(maxForward, maxReverse, maxForward, 0.0, maxSide, 0.0, 0.0, maxYaw));
    }

    AerialPilot.Command fly(AerialPilot pilot, BurnerDither dither, AerialPilot.Keys keys, double[] destination) {
        AerialPilot.Command c = pilot.tick(sense(), keys, destination);
        if (c.handsOff()) {
            signals = new int[dials.length];
            forward = 0.0;
            side = 0.0;
            yawMoment = 0.0;
            dither.reset();
            pilot.applied(0.0, 0.0, 0.0);
        } else {
            signals = dither.signals(c.gasTargets()[0], dials);
            forward = clamp(c.wrench()[Axis6.FORWARD.index()], -maxReverse, maxForward);
            side = clamp(c.wrench()[Axis6.RIGHT.index()], -maxSide, maxSide);
            yawMoment = clamp(c.wrench()[Axis6.YAW.index()], -maxYaw, maxYaw);
            pilot.applied(forward, 0.0, yawMoment);
        }
        step();
        return c;
    }

    static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
