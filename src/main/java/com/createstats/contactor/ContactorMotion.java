package com.createstats.contactor;

public final class ContactorMotion {
    private ContactorMotion() {
    }

    static final double PULL = 420.0;

    static final double SPRING_PRELOAD = 200.0;
    static final double SPRING_RATE = 100.0;

    static final double DAMPING = 6.0;

    static final double GAP_EPS = 0.12;

    static final double RESTITUTION_POLE = 0.30;
    static final double RESTITUTION_STOP = 0.35;

    static final double SETTLE_SPEED = 0.8;

    static final double STEP = 0.0005;

    static final double MAX_FRAME = 0.08;

    public static final class Armature {
        private double x;
        private double v;

        private double impactSpeed;
        private int impactStop;

        public void place(boolean pulledIn) {
            x = pulledIn ? 1.0 : 0.0;
            v = 0.0;
        }

        public double fraction() {
            return x;
        }

        public double speed() {
            return v;
        }

        public double drive() {
            return x * ContactorGeometry.STROKE;
        }

        public boolean settled() {
            return v == 0.0 && (x <= 0.0 || x >= 1.0);
        }

        public double advance(double seconds, boolean energised) {
            double left = Math.max(0.0, Math.min(MAX_FRAME, seconds));
            double hardest = 0.0;
            while (left > 1e-9) {
                final double dt = Math.min(STEP, left);
                left -= dt;
                final double a = acceleration(x, v, energised);

                if (x >= 1.0 && v <= 0.0 && v + a * dt >= 0.0) {
                    v = 0.0;
                    continue;
                }
                if (x <= 0.0 && v >= 0.0 && v + a * dt <= 0.0) {
                    v = 0.0;
                    continue;
                }
                v += a * dt;
                x += v * dt;
                if (x >= 1.0) {
                    x = 1.0;
                    if (v > 0.0) {
                        if (v > hardest) {
                            hardest = v;
                            impactStop = 1;
                        }
                        v = -RESTITUTION_POLE * v;
                        if (Math.abs(v) < SETTLE_SPEED) {
                            v = 0.0;
                        }
                    }
                } else if (x <= 0.0) {
                    x = 0.0;
                    if (v < 0.0) {
                        if (-v > hardest) {
                            hardest = -v;
                            impactStop = -1;
                        }
                        v = -RESTITUTION_STOP * v;
                        if (Math.abs(v) < SETTLE_SPEED) {
                            v = 0.0;
                        }
                    }
                }
            }
            impactSpeed = hardest;
            return hardest;
        }

        public int lastStop() {
            return impactStop;
        }

        public double lastImpactSpeed() {
            return impactSpeed;
        }
    }

    static double acceleration(double x, double v, boolean energised) {
        final double pull = energised ? PULL / sq(GAP_EPS + 1.0 - Math.min(1.0, x)) : 0.0;
        return pull - SPRING_PRELOAD - SPRING_RATE * x - DAMPING * v;
    }

    private static double sq(double d) {
        return d * d;
    }

    public static double poleDrive(int pole, double leftDrive, double rightDrive) {
        return switch (pole) {
            case ContactorLogic.LEFT -> leftDrive;
            case ContactorLogic.RIGHT -> rightDrive;
            default -> (leftDrive + rightDrive) / 2.0;
        };
    }

    public static boolean made(double drive, boolean normallyClosed) {
        return normallyClosed ? drive <= ContactorGeometry.NC_HOLD + 1e-9 : drive >= ContactorGeometry.NO_GAP - 1e-9;
    }

    public static double bridgeDrop(double drive, boolean normallyClosed) {
        if (normallyClosed) {
            return Math.max(0.0, drive - ContactorGeometry.NC_HOLD);
        }
        return Math.min(drive, ContactorGeometry.NO_GAP);
    }

    public static double tiltDegrees(double leftDrive, double rightDrive) {
        final double s = (leftDrive - rightDrive) / ContactorGeometry.BAR_SPAN;
        return Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, s))));
    }

    static final double BOUNCE_SECONDS = 0.075;

    static final double MAX_BOUNCE = 0.32;

    public static double bounceAmplitude(double speed) {
        return Math.min(MAX_BOUNCE, Math.abs(speed) * 0.0035);
    }

    public static double bounce(double since, double amplitude) {
        if (since < 0.0 || since >= BOUNCE_SECONDS || amplitude <= 0.0) {
            return 0.0;
        }
        final double t = since / BOUNCE_SECONDS;
        final double envelope = 1.0 - t;
        return amplitude * envelope * Math.abs(Math.sin(Math.PI * 3.0 * t));
    }

    static final double FLIP_SECONDS = 0.11;

    public static double flagTurn(double since, boolean toMade) {
        final double from = toMade ? 180.0 : 0.0;
        final double to = toMade ? 0.0 : 180.0;
        if (since <= 0.0) {
            return from;
        }
        if (since >= FLIP_SECONDS) {
            return to;
        }
        final double t = since / FLIP_SECONDS;

        final double c = 1.70158 * 0.6;
        final double eased = 1.0 + (c + 1.0) * Math.pow(t - 1.0, 3) + c * Math.pow(t - 1.0, 2);
        return from + (to - from) * eased;
    }

    static final double STRIKE_SECONDS = 0.03;
    static final double STRIKE_OVERSHOOT = 0.35;
    static final double STRIKE_SETTLE = 0.12;

    static final double AFTERGLOW_SECONDS = 0.07;

    public static double pilotGlow(double since, boolean energised) {
        if (since < 0.0) {
            since = 0.0;
        }
        if (!energised) {
            return Math.exp(-since / AFTERGLOW_SECONDS) * (since < 0.6 ? 1.0 : 0.0);
        }
        if (since < STRIKE_SECONDS) {
            return (since / STRIKE_SECONDS) * (1.0 + STRIKE_OVERSHOOT);
        }
        if (since < STRIKE_SETTLE) {
            final double t = (since - STRIKE_SECONDS) / (STRIKE_SETTLE - STRIKE_SECONDS);
            return 1.0 + STRIKE_OVERSHOOT * (1.0 - t);
        }
        return 1.0;
    }

    static final double ARC_SECONDS = 0.14;
    static final double ARC_RISE = 1.6;

    public static double arcGlow(double since) {
        if (since < 0.0 || since >= ARC_SECONDS) {
            return 0.0;
        }
        final double t = 1.0 - since / ARC_SECONDS;
        return t * t;
    }

    public static double arcRise(double since) {
        if (since <= 0.0) {
            return 0.0;
        }
        return ARC_RISE * Math.pow(Math.min(1.0, since / ARC_SECONDS), 0.7);
    }

    static final long REPLACE_NANOS = 500_000_000L;

    public static final class Pole {
        boolean made;
        double changedAt = -1.0e9;
        double landedAt = -1.0e9;
        double bounce;
        double brokeAt = -1.0e9;
        double brokeLoad;

        public boolean made() {
            return made;
        }
    }

    public static final class Coil {
        boolean energised;
        double changedAt = -1.0e9;
    }

    public static final class Unit {
        private final Armature left = new Armature();
        private final Armature right = new Armature();
        private final Pole[] poles = {new Pole(), new Pole(), new Pole()};
        private final Coil[] coils = {new Coil(), new Coil()};
        private boolean started;
        private boolean form;
        private double clock;
        private long lastNanos;

        public Armature left() {
            return left;
        }

        public Armature right() {
            return right;
        }

        public double clock() {
            return clock;
        }

        public int advance(long nanos, boolean leftIn, boolean rightIn, boolean leftCoil, boolean rightCoil,
                           boolean normallyClosed, int[] loads) {
            if (!started || nanos - lastNanos > REPLACE_NANOS) {
                started = true;
                lastNanos = nanos;
                form = normallyClosed;
                left.place(leftIn);
                right.place(rightIn);
                for (Coil coil : coils) {
                    coil.changedAt = -1.0e9;
                }
                coils[0].energised = leftCoil;
                coils[1].energised = rightCoil;
                for (int k = 0; k < ContactorLogic.POLES; k++) {
                    poles[k].made = ContactorMotion.made(
                            ContactorMotion.poleDrive(k, left.drive(), right.drive()), normallyClosed);
                    poles[k].changedAt = -1.0e9;
                    poles[k].landedAt = -1.0e9;
                    poles[k].brokeAt = -1.0e9;
                }
                return 0;
            }
            final double dt = Math.max(0.0, Math.min(MAX_FRAME, (nanos - lastNanos) / 1.0e9));
            lastNanos = nanos;
            clock += dt;
            if (coils[0].energised != leftCoil) {
                coils[0].energised = leftCoil;
                coils[0].changedAt = clock;
            }
            if (coils[1].energised != rightCoil) {
                coils[1].energised = rightCoil;
                coils[1].changedAt = clock;
            }
            final double beforeL = left.drive();
            final double beforeR = right.drive();
            left.advance(dt, leftIn);
            right.advance(dt, rightIn);

            form = normallyClosed;
            int broke = 0;
            for (int k = 0; k < ContactorLogic.POLES; k++) {
                final Pole pole = poles[k];
                final double drive = ContactorMotion.poleDrive(k, left.drive(), right.drive());
                final boolean now = ContactorMotion.made(drive, normallyClosed);
                if (now == pole.made) {
                    continue;
                }
                pole.made = now;
                pole.changedAt = clock;
                if (now) {
                    final double before = ContactorMotion.poleDrive(k, beforeL, beforeR);
                    final double speed = dt > 0.0 ? Math.abs(drive - before) / dt : 0.0;
                    pole.landedAt = clock;
                    pole.bounce = bounceAmplitude(speed);
                } else {
                    final int load = loads != null && k < loads.length ? loads[k] : 0;
                    if (load > 0) {
                        pole.brokeAt = clock;
                        pole.brokeLoad = load;
                        broke |= 1 << k;
                    }
                }
            }
            return broke;
        }

        public boolean made(int k) {
            return poles[k].made;
        }

        public double poleDrive(int k) {
            return ContactorMotion.poleDrive(k, left.drive(), right.drive());
        }

        public double bridgeDrop(int k) {
            final double drop = ContactorMotion.bridgeDrop(poleDrive(k), form);
            final double b = bounce(clock - poles[k].landedAt, poles[k].bounce);
            return form ? drop + b : Math.max(0.0, drop - b);
        }

        public double flagTurn(int k) {
            return ContactorMotion.flagTurn(clock - poles[k].changedAt, poles[k].made);
        }

        public double pilot(int side) {
            return pilotGlow(clock - coils[side].changedAt, coils[side].energised);
        }

        public double arcGlow(int k) {
            return ContactorMotion.arcGlow(clock - poles[k].brokeAt);
        }

        public double arcRise(int k) {
            return ContactorMotion.arcRise(clock - poles[k].brokeAt);
        }

        public double brokeLoad(int k) {
            return poles[k].brokeLoad;
        }

        public double tilt() {
            return tiltDegrees(left.drive(), right.drive());
        }

        public double meanDrive() {
            return (left.drive() + right.drive()) / 2.0;
        }

        public boolean busy() {
            if (!left.settled() || !right.settled()) {
                return true;
            }
            for (Pole pole : poles) {
                if (clock - pole.changedAt < FLIP_SECONDS || clock - pole.landedAt < BOUNCE_SECONDS
                        || clock - pole.brokeAt < ARC_SECONDS) {
                    return true;
                }
            }
            for (Coil coil : coils) {
                if (clock - coil.changedAt < STRIKE_SETTLE + AFTERGLOW_SECONDS * 4) {
                    return true;
                }
            }
            return false;
        }
    }
}
