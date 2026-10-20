package com.createstats.vccmk4.air.core;

import com.createstats.vccmk4.core.BtaG1Motion.Spring;

// springs not lerps
public final class AerialCockpitMotion {
    private AerialCockpitMotion() {
    }

    public static final double ASI_ZERO_DEG = -135.0;
    public static final double ASI_SPAN_DEG = 270.0;
    public static final double ASI_MAX_BPS = 16.0;

    public static final double ALT_DEG_PER_BLOCK = 3.6;

    public static final double VSI_ZERO_DEG = -90.0;
    public static final double VSI_SPAN_DEG = 90.0;
    public static final double VSI_MAX_BPS = 4.0;

    public static final double TURN_MARK_DEG = 20.0;
    public static final double TURN_PIN_DEG = 40.0;

    public static final double YOKE_ROLL_DEG = 60.0;
    public static final double YOKE_SLIDE_UNITS = 0.75;

    public static final double LEVER_SWING_DEG = 35.0;

    public static final double TRIM_WHEEL_DEG = 270.0;

    public static final double HORIZON_UNITS_PER_DEG = 0.1;
    public static final double HORIZON_PITCH_STOP = 15.0;

    public static final double DT = 1.0 / 20.0;
    public static final double NEEDLE_OMEGA = 9.0;

    public static final double NEEDLE_ZETA = 0.6;
    public static final double HAND_OMEGA = 14.0;
    public static final double TRIM_OMEGA = 10.0;

    public static final double GYRO_OMEGA = 10.0;

    public static final double LAMP_UP = 0.5;
    public static final double LAMP_DOWN = 0.25;

    public static final double LIGHTS_UP = 0.10;
    public static final double LIGHTS_DOWN = 0.05;

    public static final double HALO_ALPHA = 0.32;

    public static final int LAMP_TEST_TICKS = 12;

    public enum Lamp {
        CAUTION, HELM, ANCHOR_ARMED, ANCHOR_ENGAGED, KEEP_ARMED, KEEP_ENGAGED, GOTO_ARMED, GOTO_ENGAGED, MOORED;

        public static final int COUNT = values().length;

        public Lamp partner() {
            return switch (this) {
                case ANCHOR_ARMED -> ANCHOR_ENGAGED;
                case ANCHOR_ENGAGED -> ANCHOR_ARMED;
                case KEEP_ARMED -> KEEP_ENGAGED;
                case KEEP_ENGAGED -> KEEP_ARMED;
                case GOTO_ARMED -> GOTO_ENGAGED;
                case GOTO_ENGAGED -> GOTO_ARMED;
                default -> null;
            };
        }

        public boolean engaged() {
            return this == ANCHOR_ENGAGED || this == KEEP_ENGAGED || this == GOTO_ENGAGED;
        }
    }

    public static double asiDegrees(double bps) {
        return ASI_ZERO_DEG + ASI_SPAN_DEG * clamp(Math.abs(bps) / ASI_MAX_BPS, 0.0, 1.0);
    }

    public static double altitudeDegrees(double altitude) {
        return Double.isFinite(altitude) ? altitude * ALT_DEG_PER_BLOCK : 0.0;
    }

    public static double vsiDegrees(double climbRate) {
        return VSI_ZERO_DEG + VSI_SPAN_DEG * clamp(climbRate / VSI_MAX_BPS, -1.0, 1.0);
    }

    public static double turnDegrees(double yawRate, double feelTurn) {
        double rate = feelTurn > 0.0 ? yawRate / feelTurn : 0.0;
        return clamp(rate * TURN_MARK_DEG, -TURN_PIN_DEG, TURN_PIN_DEG);
    }

    public static double yokeRollDegrees(double turn) {
        return clamp(turn, -1.0, 1.0) * YOKE_ROLL_DEG;
    }

    public static double yokeSlideUnits(double climb) {
        return clamp(climb, -1.0, 1.0) * YOKE_SLIDE_UNITS;
    }

    public static double leverDegrees(double order) {
        return clamp(order, -1.0, 1.0) * LEVER_SWING_DEG;
    }

    public static double trimDegrees(double trim) {
        return clamp(trim, -1.0, 1.0) * TRIM_WHEEL_DEG / 2.0;
    }

    public static double horizonDrop(double pitch) {
        return clamp(pitch, -HORIZON_PITCH_STOP, HORIZON_PITCH_STOP) * HORIZON_UNITS_PER_DEG;
    }

    public static final double EARTH_STRIP = 0.125;

    public static double[] earthStrips(double drop, double r, double step) {
        if (!(r > 0.0) || !(step > 0.0)) return new double[0];
        double top = clamp(Double.isFinite(drop) ? -drop : 0.0, -r, r);
        int n = (int) Math.ceil((top + r) / step - 1.0e-9);
        if (n <= 0) return new double[0];
        double h = (top + r) / n;
        double[] out = new double[4 * n];
        int k = 0;
        for (int i = 0; i < n; i++) {
            double y1 = top - i * h;
            double y0 = y1 - h;
            double mid = (y0 + y1) / 2.0;
            double half = Math.sqrt(Math.max(0.0, r * r - mid * mid));
            if (half < 1.0e-3) continue;
            out[k++] = -half;
            out[k++] = y0;
            out[k++] = half;
            out[k++] = y1;
        }
        return k == out.length ? out : java.util.Arrays.copyOf(out, k);
    }

    public static double[] attitude(double qx, double qy, double qz, double qw, double fx, double fz) {
        double[] f = rotate(qx, qy, qz, qw, fx, 0.0, fz);
        double[] r = rotate(qx, qy, qz, qw, -fz, 0.0, fx);
        double level = Math.hypot(f[0], f[2]);
        double heading = level > 1.0e-9 ? Math.toDegrees(Math.atan2(f[0], -f[2])) : 0.0;
        if (heading < 0.0) heading += 360.0;
        double pitch = Math.toDegrees(Math.asin(clamp(f[1], -1.0, 1.0)));
        double roll = -Math.toDegrees(Math.asin(clamp(r[1], -1.0, 1.0)));
        return new double[] {heading, pitch, roll};
    }

    static double[] rotate(double x, double y, double z, double w, double vx, double vy, double vz) {
        double cx = y * vz - z * vy;
        double cy = z * vx - x * vz;
        double cz = x * vy - y * vx;
        double ccx = y * cz - z * cy;
        double ccy = z * cx - x * cz;
        double ccz = x * cy - y * cx;
        return new double[] {vx + 2.0 * (w * cx + ccx), vy + 2.0 * (w * cy + ccy), vz + 2.0 * (w * cz + ccz)};
    }

    public static double facingHeading(int stepX, int stepZ) {
        double h = Math.toDegrees(Math.atan2(stepX, -stepZ));
        return h < 0.0 ? h + 360.0 : h;
    }

    public static boolean lit(Lamp lamp, AerialCockpitState s) {
        if (!s.live()) return false;
        AerialPilot.Mode mode = s.modeValue();
        boolean helm = mode == AerialPilot.Mode.HELM;
        LeaveMode leave = s.leaveValue();
        return switch (lamp) {
            case CAUTION -> s.caution();
            case HELM -> helm;
            case ANCHOR_ARMED -> helm && leave == LeaveMode.ANCHOR;
            case KEEP_ARMED -> helm && leave == LeaveMode.KEEP_COURSE;
            case GOTO_ARMED -> helm && leave == LeaveMode.GO_TO;
            case ANCHOR_ENGAGED -> mode == AerialPilot.Mode.ANCHOR;
            case KEEP_ENGAGED -> mode == AerialPilot.Mode.KEEP_COURSE;
            case GOTO_ENGAGED -> mode == AerialPilot.Mode.GO_TO;
            case MOORED -> mode == AerialPilot.Mode.MOORED;
        };
    }

    public static boolean lightsOn(AerialCockpitState s) {
        return s.live() && s.modeValue().flying();
    }

    public static int haloAlpha(double level) {
        double l = clamp(level, 0.0, 1.0);
        return (int) Math.round(255.0 * HALO_ALPHA * l * l);
    }

    public static boolean testLit(Lamp lamp, int t) {
        int k = lamp.ordinal();
        return t >= k && t < k + LAMP_TEST_TICKS;
    }

    public static int lampTestTicks() {
        return Lamp.COUNT + LAMP_TEST_TICKS;
    }

    public static final class Frame {
        public final double asi;
        public final double alt;
        public final double altBug;
        public final boolean altBugShown;
        public final double vsi;
        public final double turn;
        public final double yokeRoll;
        public final double yokeSlide;
        public final double lever;
        public final double trim;
        public final double heading;
        public final double pitch;
        public final double roll;
        public final double heldHeading;
        public final double lights;
        private final double[] lamps;

        Frame(double asi, double alt, double altBug, boolean altBugShown, double vsi, double turn, double yokeRoll,
              double yokeSlide, double lever, double trim, double heading, double pitch, double roll, double heldHeading,
              double lights, double[] lamps) {
            this.asi = asi;
            this.alt = alt;
            this.altBug = altBug;
            this.altBugShown = altBugShown;
            this.vsi = vsi;
            this.turn = turn;
            this.yokeRoll = yokeRoll;
            this.yokeSlide = yokeSlide;
            this.lever = lever;
            this.trim = trim;
            this.heading = heading;
            this.pitch = pitch;
            this.roll = roll;
            this.heldHeading = heldHeading;
            this.lights = lights;
            this.lamps = lamps;
        }

        public double lamp(Lamp l) {
            return lamps[l.ordinal()];
        }

        public double shown(Lamp l) {
            Lamp other = l.partner();
            double mine = lamp(l);
            if (other == null) return mine;
            double theirs = lamp(other);
            return (l.engaged() ? mine >= theirs : mine > theirs) ? mine : 0.0;
        }
    }

    public static final class Pose {
        private final Spring asi = new Spring();
        private final Spring alt = new Spring();
        private final Spring altBug = new Spring();
        private final Spring vsi = new Spring();
        private final Spring turn = new Spring();
        private final Spring yokeRoll = new Spring();
        private final Spring yokeSlide = new Spring();
        private final Spring lever = new Spring();
        private final Spring trim = new Spring();
        private final Spring heading = new Spring();
        private final Spring pitch = new Spring();
        private final Spring roll = new Spring();
        private final Spring[] all = {asi, alt, altBug, vsi, turn, yokeRoll, yokeSlide, lever, trim, heading, pitch, roll};
        private final double[] prev = new double[all.length];
        private final double[] lamp = new double[Lamp.COUNT];
        private final double[] lampPrev = new double[Lamp.COUNT];
        private double lights;
        private double lightsPrev;
        private AerialCockpitState last = AerialCockpitState.IDLE;
        private boolean primed;
        private int testLeft;
        private double fallbackHeading;

        public Pose() {
            asi.snap(asiDegrees(0.0));
            vsi.snap(vsiDegrees(0.0));
            for (int i = 0; i < all.length; i++) prev[i] = all[i].value();
        }

        public void restingHeading(double deg) {
            fallbackHeading = deg;
            if (!primed) {
                heading.snap(deg);
                prev[9] = deg;
            }
        }

        public boolean testing() {
            return testLeft > 0;
        }

        public void tick(AerialCockpitState target) {
            for (int i = 0; i < all.length; i++) prev[i] = all[i].value();
            System.arraycopy(lamp, 0, lampPrev, 0, lamp.length);
            lightsPrev = lights;
            double headingTarget = target.live() ? unwrap(heading.value(), target.heading()) : unwrap(heading.value(), fallbackHeading);
            double altTarget = altitudeDegrees(target.altitude());
            double heldDeg = Double.isFinite(target.held()) ? altitudeDegrees(target.held()) : altBug.value();
            if (!primed && target.live()) {
                primed = true;
                asi.snap(asiDegrees(target.air()));
                alt.snap(altTarget);
                altBug.snap(heldDeg);
                vsi.snap(vsiDegrees(target.climbRate()));
                turn.snap(turnDegrees(target.yawRate(), target.feelTurn()));
                yokeRoll.snap(yokeRollDegrees(target.turn()));
                yokeSlide.snap(yokeSlideUnits(target.climb()));
                lever.snap(leverDegrees(target.order()));
                trim.snap(trimDegrees(target.trim()));
                heading.snap(target.heading());
                pitch.snap(target.pitch());
                roll.snap(target.roll());
                for (Lamp l : Lamp.values()) lamp[l.ordinal()] = lit(l, target) ? 1.0 : 0.0;
                lights = lightsOn(target) ? 1.0 : 0.0;
                for (int i = 0; i < all.length; i++) prev[i] = all[i].value();
                System.arraycopy(lamp, 0, lampPrev, 0, lamp.length);
                lightsPrev = lights;
                last = target;
                return;
            }
            if (last.live() && !last.piloted() && target.piloted()) testLeft = lampTestTicks();
            last = target;
            int t = lampTestTicks() - testLeft;
            asi.step(asiDegrees(target.air()), NEEDLE_OMEGA, NEEDLE_ZETA, DT);
            asi.pin(ASI_ZERO_DEG, ASI_ZERO_DEG + ASI_SPAN_DEG);
            alt.step(unwrap(alt.value(), altTarget), NEEDLE_OMEGA, NEEDLE_ZETA, DT);
            altBug.step(unwrap(altBug.value(), heldDeg), HAND_OMEGA, 1.0, DT);
            vsi.step(vsiDegrees(target.climbRate()), NEEDLE_OMEGA, NEEDLE_ZETA, DT);
            vsi.pin(VSI_ZERO_DEG - VSI_SPAN_DEG, VSI_ZERO_DEG + VSI_SPAN_DEG);
            turn.step(turnDegrees(target.yawRate(), target.feelTurn()), NEEDLE_OMEGA, NEEDLE_ZETA, DT);
            turn.pin(-TURN_PIN_DEG, TURN_PIN_DEG);
            yokeRoll.step(yokeRollDegrees(target.turn()), HAND_OMEGA, 1.0, DT);
            yokeSlide.step(yokeSlideUnits(target.climb()), HAND_OMEGA, 1.0, DT);
            lever.step(leverDegrees(target.order()), HAND_OMEGA, 1.0, DT);
            trim.step(trimDegrees(target.trim()), TRIM_OMEGA, 1.0, DT);
            heading.step(headingTarget, GYRO_OMEGA, 1.0, DT);
            pitch.step(target.pitch(), GYRO_OMEGA, 1.0, DT);
            roll.step(target.roll(), GYRO_OMEGA, 1.0, DT);
            for (Lamp l : Lamp.values()) {
                boolean on = lit(l, target) || (testLeft > 0 && testLit(l, t));
                lamp[l.ordinal()] = approach(lamp[l.ordinal()], on ? 1.0 : 0.0, LAMP_UP, LAMP_DOWN);
            }
            lights = approach(lights, lightsOn(target) || testLeft > 0 ? 1.0 : 0.0, LIGHTS_UP, LIGHTS_DOWN);
            if (testLeft > 0) testLeft--;
        }

        public Frame frame(float partial) {
            double p = clamp(partial, 0.0, 1.0);
            double[] v = new double[all.length];
            for (int i = 0; i < all.length; i++) v[i] = mix(prev[i], all[i].value(), p);
            double[] lamps = new double[Lamp.COUNT];
            for (int i = 0; i < lamps.length; i++) lamps[i] = mix(lampPrev[i], lamp[i], p);
            return new Frame(v[0], v[1], v[2], Double.isFinite(last.held()), v[3], v[4], v[5], v[6], v[7], v[8], v[9],
                    v[10], v[11], last.heldHeading(), mix(lightsPrev, lights, p), lamps);
        }
    }

    static double unwrap(double current, double target) {
        if (!Double.isFinite(target)) return current;
        double d = (target - current) % 360.0;
        if (d > 180.0) d -= 360.0;
        if (d < -180.0) d += 360.0;
        return current + d;
    }

    static double approach(double value, double target, double up, double down) {
        double delta = target - value;
        double rate = delta > 0.0 ? up : down;
        if (Math.abs(delta) <= rate + 1.0e-9) return target;
        return value + Math.signum(delta) * rate;
    }

    static double mix(double a, double b, double t) {
        return a + (b - a) * t;
    }

    static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
