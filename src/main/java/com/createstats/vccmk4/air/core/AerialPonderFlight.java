package com.createstats.vccmk4.air.core;

import java.util.Arrays;

public final class AerialPonderFlight {
    public static final double HERO_PUSH = 1920.0;
    public static final double HERO_REVERSE = 960.0;
    public static final double HERO_DRAG = 258.0;

    static final int SETTLE_TICKS = 40;

    static final int HOVER_PREROLL = 20 * 40;

    private AerialPonderFlight() {
    }

    public enum Start {
        GROUNDED,

        HOVERING
    }

    public static final class Script {
        final Start start;
        final int ticks;
        final boolean[] seated;
        final double[] throttle;
        final double[] steer;
        final double[] lift;
        final double[] cargo;

        public Script(Start start, int ticks) {
            this.start = start == null ? Start.GROUNDED : start;
            this.ticks = Math.max(1, ticks);
            seated = new boolean[this.ticks];
            throttle = new double[this.ticks];
            steer = new double[this.ticks];
            lift = new double[this.ticks];
            cargo = new double[this.ticks];
            if (this.start == Start.HOVERING) Arrays.fill(seated, true);
        }

        public int ticks() {
            return ticks;
        }

        public Script seat(int from) {
            for (int t = clamp(from); t < ticks; t++) seated[t] = true;
            return this;
        }

        public Script stand(int from) {
            for (int t = clamp(from); t < ticks; t++) seated[t] = false;
            return this;
        }

        public Script tap(int at, int dir) {
            for (int t = at; t < at + 2; t++) if (t >= 0 && t < ticks) throttle[t] = Math.signum(dir);
            return this;
        }

        public Script lift(int from, int to, double value) {
            for (int t = clamp(from); t < Math.min(ticks, to); t++) lift[t] = value;
            return this;
        }

        public Script steer(int from, int to, double value) {
            for (int t = clamp(from); t < Math.min(ticks, to); t++) steer[t] = value;
            return this;
        }

        public Script cargo(int at, double kpg) {
            if (at >= 0 && at < ticks) cargo[at] += kpg;
            return this;
        }

        private int clamp(int t) {
            return Math.max(0, Math.min(ticks, t));
        }
    }

    public static final class Recording {
        final int n;
        final double[] y;
        final double[] vy;
        final double[] x;
        final double[] z;
        final double[] heading;
        final double[] yawRate;
        final double[] airForward;
        final double[] gas;
        final double[] gasTarget;
        final double[] order;
        final double[] engine;
        final double[] forward;
        final double[] yaw;
        final double[] held;
        final double[] ceiling;
        final double[] mass;
        final double[] keel;
        final int[][] signals;
        final int[] rings;
        final AerialPilot.Mode[] mode;
        final boolean[] braking;
        final double capacity;
        final double groundY;
        final double keelBelow;
        final double maxForward;
        final double maxReverse;

        Recording(int n, AirPlant p) {
            this.n = n;
            y = new double[n];
            vy = new double[n];
            x = new double[n];
            z = new double[n];
            heading = new double[n];
            yawRate = new double[n];
            airForward = new double[n];
            gas = new double[n];
            gasTarget = new double[n];
            order = new double[n];
            engine = new double[n];
            forward = new double[n];
            yaw = new double[n];
            held = new double[n];
            ceiling = new double[n];
            mass = new double[n];
            keel = new double[n];
            signals = new int[n][];
            rings = new int[n];
            mode = new AerialPilot.Mode[n];
            braking = new boolean[n];
            capacity = p.capacity;
            groundY = p.groundY;
            keelBelow = p.keelBelow;
            maxForward = p.maxForward;
            maxReverse = p.maxReverse;
        }

        public int length() {
            return n;
        }

        private int at(int t) {
            return Math.max(0, Math.min(n - 1, t));
        }

        public double y(int t) {
            return y[at(t)];
        }

        public double vy(int t) {
            return vy[at(t)];
        }

        public double x(int t) {
            return x[at(t)];
        }

        public double z(int t) {
            return z[at(t)];
        }

        public double heading(int t) {
            return heading[at(t)];
        }

        public double yawRate(int t) {
            return yawRate[at(t)];
        }

        public double airForward(int t) {
            return airForward[at(t)];
        }

        public double gas(int t) {
            return gas[at(t)];
        }

        public double fill(int t) {
            return capacity > 0.0 ? gas[at(t)] / capacity : 0.0;
        }

        public double gasTarget(int t) {
            return gasTarget[at(t)];
        }

        public double order(int t) {
            return order[at(t)];
        }

        public double engine(int t) {
            return engine[at(t)];
        }

        public double forward(int t) {
            return forward[at(t)];
        }

        public double push(int t) {
            double f = forward[at(t)];
            double max = f >= 0.0 ? maxForward : maxReverse;
            return max > 0.0 ? Math.max(-1.0, Math.min(1.0, f / max)) : 0.0;
        }

        public double yaw(int t) {
            return yaw[at(t)];
        }

        public double held(int t) {
            return held[at(t)];
        }

        public double ceiling(int t) {
            return ceiling[at(t)];
        }

        public double mass(int t) {
            return mass[at(t)];
        }

        public double keel(int t) {
            return keel[at(t)];
        }

        public int signal(int t, int burner) {
            int[] s = signals[at(t)];
            return burner >= 0 && burner < s.length ? s[burner] : 0;
        }

        public int burners() {
            return signals[0].length;
        }

        public int rings(int t) {
            return rings[at(t)];
        }

        public boolean rang(int t) {
            return t > 0 && t < n && rings[t] != rings[t - 1];
        }

        public AerialPilot.Mode mode(int t) {
            return mode[at(t)];
        }

        public boolean braking(int t) {
            return braking[at(t)];
        }

        public double groundY() {
            return groundY;
        }

        public double restY() {
            return groundY + keelBelow;
        }

        public double northSince(int from, int t) {
            return -(z(t) - z(from));
        }
    }

    static AirPlant heroPlant() {
        AirPlant p = new AirPlant();
        heroEngines(p);
        return p;
    }

    static void heroEngines(AirPlant p) {
        p.maxForward = HERO_PUSH;
        p.maxReverse = HERO_REVERSE;
        p.dragF = HERO_DRAG;
    }

    public static Recording fly(Script script) {
        AirPlant plant = heroPlant();
        AerialPilot pilot = new AerialPilot();
        BurnerDither dither = new BurnerDither();
        plant.rest();
        for (int i = 0; i < SETTLE_TICKS; i++) plant.fly(pilot, dither, AerialPilot.Keys.NONE, null);
        if (script.start == Start.HOVERING) {
            AerialPilot.Keys seated = new AerialPilot.Keys(true, 0.0, 0.0, 0.0, 0.0, false);
            for (int i = 0; i < HOVER_PREROLL; i++) plant.fly(pilot, dither, seated, null);
        }
        Recording r = new Recording(script.ticks, plant);
        for (int t = 0; t < script.ticks; t++) {
            if (script.cargo[t] != 0.0) plant.mass += script.cargo[t];
            AerialPilot.Keys keys = script.seated[t]
                    ? new AerialPilot.Keys(true, script.throttle[t], script.steer[t], script.lift[t], 0.0, false)
                    : AerialPilot.Keys.NONE;
            AerialPilot.Command c = plant.fly(pilot, dither, keys, null);
            record(r, t, plant, pilot, c);
        }
        return r;
    }

    private static void record(Recording r, int t, AirPlant p, AerialPilot pilot, AerialPilot.Command c) {
        r.y[t] = p.y;
        r.vy[t] = p.vy;
        r.x[t] = p.x;
        r.z[t] = p.z;
        r.heading[t] = p.heading;
        r.yawRate[t] = p.yawRate;
        r.airForward[t] = p.u;
        r.gas[t] = p.gas;
        r.gasTarget[t] = c.gasTargets().length > 0 ? c.gasTargets()[0] : 0.0;
        r.order[t] = c.order();
        r.engine[t] = c.engine();
        r.forward[t] = p.forward;
        r.yaw[t] = p.yawMoment;
        r.held[t] = c.targetAltitude();
        r.ceiling[t] = c.ceiling();
        r.mass[t] = p.mass;
        r.keel[t] = p.y - p.keelBelow - p.groundAt(p.x, p.z);
        r.signals[t] = p.signals.clone();
        r.rings[t] = pilot.telegraph().rings();
        r.mode[t] = c.mode();
        r.braking[t] = c.braking();
    }

    public static final double PID_KP = 0.8;
    public static final double PID_KI = 0.03;
    public static final double PID_KD = 3.0;

    public static Recording lever(int ticks, int stepAt, double climb) {
        return lever(ticks, stepAt, climb, Double.NaN);
    }

    public static Recording lever(int ticks, int stepAt, double climb, double startY) {
        double[] asked = new double[Math.max(1, ticks)];
        Arrays.fill(asked, Math.min(asked.length, Math.max(0, stepAt)), asked.length, climb);
        return lever(asked, startY);
    }

    public static Recording lever(double[] asked, double startY) {
        int ticks = asked.length;
        AirPlant plant = heroPlant();
        AerialPilot pilot = new AerialPilot();
        BurnerDither dither = new BurnerDither();
        plant.rest();
        for (int i = 0; i < SETTLE_TICKS; i++) plant.fly(pilot, dither, AerialPilot.Keys.NONE, null);
        AerialPilot.Keys seated = new AerialPilot.Keys(true, 0.0, 0.0, 0.0, 0.0, false);
        for (int i = 0; i < HOVER_PREROLL; i++) plant.fly(pilot, dither, seated, null);

        if (Double.isFinite(startY)) plant.y = startY;
        double start = plant.y;
        plant.vy = 0.0;
        plant.u = 0.0;
        plant.trim();
        double trimSignal = GasLaw.STEPS * plant.gas / sum(plant.dials);
        Recording r = new Recording(Math.max(1, ticks), plant);
        double integral = 0.0;
        for (int t = 0; t < r.n; t++) {
            double target = start + asked[t];
            double e = target - plant.y;
            integral += e * AirPlant.DT;
            double u = trimSignal + PID_KP * e + PID_KI * integral - PID_KD * plant.vy;
            int lever = (int) Math.max(0, Math.min(GasLaw.STEPS, Math.round(u)));
            Arrays.fill(plant.signals, lever);
            plant.forward = 0.0;
            plant.yawMoment = 0.0;
            plant.step();
            r.y[t] = plant.y;
            r.vy[t] = plant.vy;
            r.x[t] = plant.x;
            r.z[t] = plant.z;
            r.gas[t] = plant.gas;
            r.gasTarget[t] = BurnerDither.output(plant.signals, plant.dials);
            r.held[t] = target;
            r.ceiling[t] = Double.NaN;
            r.mass[t] = plant.mass;
            r.keel[t] = plant.y - plant.keelBelow - plant.groundAt(plant.x, plant.z);
            r.signals[t] = plant.signals.clone();
            r.mode[t] = AerialPilot.Mode.OFF;
        }
        return r;
    }

    private static double sum(double[] v) {
        double s = 0.0;
        for (double d : v) s += d;
        return s;
    }
}
