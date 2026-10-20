package com.createstats.battle.core;

public final class GunDrive {
    public static final double DEG_PER_RPM_TICK = 0.0375;

    public static final int PERIOD = 2;

    public static final int DELAY = 1;

    public static final double RATE_AUTOCANNON = 2.4;

    public static final double RATE_BIG_CANNON = 1.2;

    public static final int PROBE_RPM = 8;

    public static final int RAMP = 12;

    public static final int FAULT_HOLD = 100;

    static final double P_GAIN = 0.3;

    static final double FF_CAP = 3.0;

    static final double DEADBAND_FLOOR = 0.1;
    static final double DEADBAND_STEPS = 1.5;

    static final double RATIO_MIN = 1.0 / 64.0;
    static final double RATIO_MAX = 64.0;

    static final double CHECK_DEG = 2.0;

    static final double STALL_SHARE = 0.2;

    static final double WRONG_SHARE = 0.5;

    static final int DEAD_LINE_TICKS = 20;

    static final double MAX_STEP = 45.0;

    static final int RPM_CLAMP = 256;

    static final int LEASE_TICKS = 10;

    static final int STRESS_HOLDOFF = 10;
    static final int CAP_MIN = 4;

    static final int STRESS_RESTORE_TICKS = 200;
    static final int STRESS_RESTORE_STEP = 4;

    private static final int NO_CAP = Integer.MAX_VALUE;
    private static final long NEVER = Long.MIN_VALUE / 4;

    private GunDrive() {
    }

    public static double yawLaw() {
        return -DEG_PER_RPM_TICK;
    }

    public static double pitchLaw(boolean positive, boolean alongX) {
        return positive == alongX ? DEG_PER_RPM_TICK : -DEG_PER_RPM_TICK;
    }

    public static final class Axis {
        private final boolean wrap;
        private double ratio;
        private int sense;
        private int lastCommand;
        private long lastCommandTick = NEVER;
        private boolean hasWanted;
        private double lastWanted;
        private double ff;
        private int prevApplied;
        private double prevInterface;
        private long lastObserveTick = NEVER;
        private double checkExpected;
        private double checkMoved;
        private int deadLine;
        private long faultUntil = NEVER;
        private boolean lineFault;
        private int stressCap = NO_CAP;
        private long lastHalving = NEVER;
        private long quietSince = NEVER;

        public Axis(double ratio, int sense, boolean wrap) {
            this.wrap = wrap;
            double m = Math.abs(ratio);
            this.ratio = Double.isFinite(ratio) && m >= RATIO_MIN && m <= RATIO_MAX ? ratio : 0.0;
            this.sense = sense < 0 ? -1 : 1;
        }

        public double ratio() {
            return ratio;
        }

        public int sense() {
            return sense;
        }

        public boolean known() {
            return ratio != 0.0;
        }

        public double gain(double law) {
            return sense * law * (ratio == 0.0 ? 1.0 : ratio);
        }

        public boolean faulted(long now) {
            return now < faultUntil;
        }

        public boolean lineFault() {
            return lineFault;
        }

        public int lastCommand() {
            return lastCommand;
        }

        public int cap(double law, double maxRate) {
            return GunDrive.cap(this, gain(law), maxRate);
        }

        public void relearn() {
            ratio = 0.0;
            sense = 1;
            faultUntil = NEVER;
            lineFault = false;
            stressCap = NO_CAP;
            lastHalving = NEVER;
            quietSince = NEVER;
            reset();
        }

        public void reset() {
            forgetCommand();
            lastObserveTick = NEVER;
            prevApplied = 0;
            prevInterface = 0.0;
            deadLine = 0;
            clearCheck();
        }

        private void forgetCommand() {
            lastCommand = 0;
            lastCommandTick = NEVER;
            hasWanted = false;
            ff = 0.0;
        }

        private void clearCheck() {
            checkExpected = 0.0;
            checkMoved = 0.0;
        }
    }

    public static void observe(Axis a, double law, double delta, int applied, double interfaceRpm,
                               boolean atLimit, boolean overstressed, long now) {
        boolean contiguous = now - a.lastObserveTick == 1;
        int prev = a.prevApplied;
        double prevInterface = a.prevInterface;
        a.prevApplied = applied;
        a.prevInterface = interfaceRpm;
        a.lastObserveTick = now;
        stress(a, overstressed, now);

        boolean steady = applied != 0 && applied == prev;
        if (steady && Math.abs(interfaceRpm) > 1e-3) {
            double k = interfaceRpm / applied;
            double m = Math.abs(k);
            if (Double.isFinite(k) && m >= RATIO_MIN && m <= RATIO_MAX) a.ratio = k;
        }
        if (a.faulted(now)) {
            a.clearCheck();
            a.deadLine = 0;
            return;
        }
        if (steady && Math.abs(interfaceRpm) <= 1e-3 && !overstressed) {
            if (++a.deadLine >= DEAD_LINE_TICKS) {
                fault(a, true, now);
                return;
            }
        } else {
            a.deadLine = 0;
        }

        double d = a.wrap ? wrap180(delta) : delta;
        if (!contiguous || atLimit || overstressed || !Double.isFinite(d) || Math.abs(d) > MAX_STEP
                || interfaceRpm != prevInterface) {
            a.clearCheck();
            return;
        }
        double owed = a.sense * law * interfaceRpm;
        if (Math.abs(owed) < 1e-9) return;
        a.checkExpected += Math.abs(owed);
        a.checkMoved += d * Math.signum(owed);
        if (a.checkExpected < CHECK_DEG) return;
        double share = a.checkMoved / a.checkExpected;
        a.clearCheck();
        if (share <= -WRONG_SHARE) {
            a.sense = -a.sense;
        } else if (share < STALL_SHARE) {
            fault(a, false, now);
        }
    }

    public static int command(Axis a, double law, double wanted, double current, double maxRate,
                              boolean blockUp, boolean blockDown, long now) {
        if (a.faulted(now)) {
            a.forgetCommand();
            return 0;
        }
        long dt = now - a.lastCommandTick;
        if (dt >= 0 && dt < PERIOD) {
            a.lastCommand = limit(a, law, a.lastCommand, blockUp, blockDown);
            return a.lastCommand;
        }
        if (dt < 0 || dt > LEASE_TICKS) {
            a.forgetCommand();
            dt = PERIOD;
        }
        if (!Double.isFinite(wanted) || !Double.isFinite(current)) {
            a.hasWanted = false;
            a.ff = 0.0;
            return record(a, 0, now);
        }

        double fNew = 0.0;
        if (a.hasWanted) {
            double dw = wanted - a.lastWanted;
            fNew = (a.wrap ? wrap180(dw) : dw) / dt;
        }
        a.ff = clamp(0.5 * a.ff + 0.5 * fNew, -FF_CAP, FF_CAP);
        a.lastWanted = wanted;
        a.hasWanted = true;

        double g = a.gain(law);
        int prev = a.lastCommand;
        double err = wanted - current;
        if (a.wrap) err = wrap180(err);
        double e = err + (a.ff - prev * g) * DELAY;

        long u;
        if (Math.abs(e) <= deadband(g)) {
            u = Math.round(clamp(a.ff / g, -RPM_CLAMP, RPM_CLAMP));
        } else {
            u = Math.round(clamp((a.ff + P_GAIN * e) / g, -RPM_CLAMP, RPM_CLAMP));
            if (u == 0) u = e / g > 0.0 ? 1 : -1;
        }
        long room = (long) Math.floor((Math.abs(e) + Math.abs(a.ff) * PERIOD) / (Math.abs(g) * PERIOD) + 1e-9);
        if (Math.abs(u) > room) u = Long.signum(u) * room;

        int cap = cap(a, g, maxRate);
        int out = (int) Math.max(-cap, Math.min(cap, u));
        if (out != 0) {
            int mag = Math.abs(out);
            if (prev == 0 || Integer.signum(prev) != Integer.signum(out)) {
                mag = Math.min(mag, RAMP);
            } else {
                mag = Math.min(mag, Math.abs(prev) + RAMP);
            }
            out = Integer.signum(out) * mag;
        }
        return record(a, limit(a, law, out, blockUp, blockDown), now);
    }

    public static double fullRate(Axis a, double law, double maxRate) {
        return Math.abs(a.gain(law)) * cap(a, a.gain(law), maxRate);
    }

    private static int cap(Axis a, double g, double maxRate) {
        double ag = Math.abs(g);
        int c = ag <= 0.0 ? RPM_CLAMP : (int) Math.min(RPM_CLAMP, Math.floor(maxRate / ag + 1e-9));
        if (!a.known()) c = Math.min(c, PROBE_RPM);
        c = Math.min(c, a.stressCap);
        return Math.max(1, c);
    }

    private static void fault(Axis a, boolean line, long now) {
        a.faultUntil = now + FAULT_HOLD;
        a.lineFault = line;
        a.deadLine = 0;
        a.clearCheck();
        a.forgetCommand();
    }

    private static void stress(Axis a, boolean overstressed, long now) {
        if (overstressed) {
            if (now - a.lastHalving >= STRESS_HOLDOFF) {
                int base = a.stressCap == NO_CAP ? Math.max(CAP_MIN, Math.abs(a.lastCommand)) : a.stressCap;
                a.stressCap = Math.max(CAP_MIN, base / 2);
                a.lastHalving = now;
            }
            a.quietSince = now;
        } else if (a.stressCap != NO_CAP && now - a.quietSince >= STRESS_RESTORE_TICKS) {
            a.stressCap += STRESS_RESTORE_STEP;
            a.quietSince = now;
            if (a.stressCap >= RPM_CLAMP) a.stressCap = NO_CAP;
        }
    }

    private static int record(Axis a, int u, long now) {
        a.lastCommand = u;
        a.lastCommandTick = now;
        return u;
    }

    private static int limit(Axis a, double law, int u, boolean blockUp, boolean blockDown) {
        if (u == 0) return 0;
        double heading = u * a.gain(law);
        if ((heading > 0.0 && blockUp) || (heading < 0.0 && blockDown)) return 0;
        return u;
    }

    static double deadband(double g) {
        return Math.max(DEADBAND_FLOOR, DEADBAND_STEPS * Math.abs(g) * PERIOD);
    }

    static double wrap180(double deg) {
        double w = deg % 360.0;
        if (w >= 180.0) w -= 360.0;
        if (w < -180.0) w += 360.0;
        return w;
    }

    private static double clamp(double x, double lo, double hi) {
        return Math.max(lo, Math.min(hi, x));
    }
}
