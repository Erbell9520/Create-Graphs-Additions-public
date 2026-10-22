package com.createstats.client.coupler;

public final class ExchangeMotion {
    public static final double STAR_R = 4.0;

    public static final double STAR_C = STAR_R * Math.sqrt(2.0);

    public static final double DRIVER_AT = -135.0;

    public static final double SWING = 0.25;

    public static final double BOUNCE = 0.04;

    public static final double RISE = 0.04;

    public static final double CONTACT_START = SWING + BOUNCE;
    public static final double CONTACT_END = 1.0 - RISE;

    public static final double BOUNCE_HEIGHT = 0.1;

    public static final double SLOWEST_TICKS = 160.0;

    public static final double FASTEST_TICKS = 12.0;

    public static final double TRICKLE_FE = 64.0;

    public static final double RATING_FE = 8192.0;

    public static final int TOP = 0;
    public static final int SOUTH = 1;
    public static final int BOTTOM = 2;
    public static final int NORTH = 3;

    private ExchangeMotion() {
    }

    public static double pace(int crossingFe) {
        if (crossingFe <= 0) {
            return 0.0;
        }
        return clamp((Math.log(Math.max(1.0, crossingFe)) - Math.log(TRICKLE_FE))
                / (Math.log(RATING_FE) - Math.log(TRICKLE_FE)), 0.0, 1.0);
    }

    public static double periodTicks(int crossingFe) {
        if (crossingFe <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        return SLOWEST_TICKS * Math.pow(FASTEST_TICKS / SLOWEST_TICKS, pace(crossingFe));
    }

    public static double frac(double q) {
        return q - Math.floor(q);
    }

    public static double periodFraction(double q, boolean pull) {
        final double f = frac(q);
        return pull ? f : frac(SWING - f);
    }

    public static double starAngle(double q) {
        final double k = Math.floor(q);
        final double f = q - k;
        final double turn;
        if (f < SWING) {
            final double g = Math.toRadians(-45.0 + 360.0 * f);
            final double sigma = Math.toDegrees(Math.atan2(STAR_R * Math.sin(g), STAR_C - STAR_R * Math.cos(g)));
            turn = 45.0 + sigma;
        } else {
            turn = 90.0;
        }
        return 90.0 * k + turn;
    }

    public static double driverAngle(double q) {
        final double towardStar = DRIVER_AT + 180.0;
        return towardStar - 360.0 * (q - SWING / 2.0);
    }

    public static int station(int i, double starDeg) {
        final double angle = ((-90.0 + 90.0 * i + starDeg) % 360.0 + 360.0) % 360.0;
        return (int) Math.round(angle / 90.0) % 4;
    }

    public static double contact(double t) {
        return clamp((t - CONTACT_START) / (CONTACT_END - CONTACT_START), 0.0, 1.0);
    }

    public static double cassetteLevel(int i, double q, boolean pull) {
        final int source = pull ? NORTH : SOUTH;
        final double t = periodFraction(q, pull);
        if (t < SWING) {
            final double from = pull ? 90.0 * Math.floor(q) : 90.0 * (Math.floor(q) + 1.0);
            final int st = station(i, from);
            return st == source || st == TOP ? 1.0 : 0.0;
        }
        final int st = station(i, starAngle(q));
        if (st == TOP) {
            return 1.0;
        }
        if (st == BOTTOM) {
            return 0.0;
        }
        final double p = contact(t);
        return st == source ? p : 1.0 - p;
    }

    public static int capsLit(double level) {
        return level >= 1.0 - 1e-6 ? 3 : level >= 2.0 / 3.0 - 1e-6 ? 2 : level >= 1.0 / 3.0 - 1e-6 ? 1 : 0;
    }

    public static double shoeLift(double t) {
        if (t < SWING) {
            final double u = t / SWING;
            if (u < 0.85) {
                return 1.0;
            }
            final double v = (u - 0.85) / 0.15;
            return 1.0 - v * v;
        }
        if (t < CONTACT_START) {
            return BOUNCE_HEIGHT * Math.sin(Math.PI * (t - SWING) / BOUNCE);
        }
        if (t < CONTACT_END) {
            return 0.0;
        }
        return smooth((t - CONTACT_END) / RISE);
    }

    public static double leverAngle(int direction) {
        return direction > 0 ? 45.0 : direction < 0 ? -45.0 : 0.0;
    }

    public static double leverThrow(double from, double to, double ticks) {
        final double u = clamp(ticks / 7.0, 0.0, 1.0);
        return from + (to - from) * easeOutBack(u, 1.4);
    }

    public static double shutterFall(boolean tripped, double ticks) {
        if (tripped) {
            if (ticks < 6.0) {
                final double u = clamp(ticks / 6.0, 0.0, 1.0);
                return u * u;
            }
            return ticks < 10.0 ? 1.0 - 0.08 * Math.sin((ticks - 6.0) / 4.0 * Math.PI) : 1.0;
        }
        return 1.0 - smooth(clamp(ticks / 10.0, 0.0, 1.0));
    }

    public static float crackleVolume(int crossingFe) {
        if (crossingFe <= 0) {
            return 0.0f;
        }
        return (float) Math.pow(10.0, (-10.0 + 10.0 * pace(crossingFe)) / 20.0);
    }

    public static final class Animator {
        private double q = SWING + 0.35;
        private double lastTime = Double.NaN;
        private double lastPeriod = SLOWEST_TICKS;
        private int lastDirection = 1;
        private int swingsBegun;
        private int seated;
        private int broken;

        public double q() {
            return this.q;
        }

        public int direction() {
            return this.lastDirection;
        }

        public int takeSwingsBegun() {
            final int n = this.swingsBegun;
            this.swingsBegun = 0;
            return n;
        }

        public int takeSeated() {
            final int n = this.seated;
            this.seated = 0;
            return n;
        }

        public int takeBroken() {
            final int n = this.broken;
            this.broken = 0;
            return n;
        }

        public void advance(double time, int direction, int crossingFe, boolean flowing) {
            if (Double.isNaN(this.lastTime) || time < this.lastTime || time - this.lastTime > 200.0) {
                this.lastTime = time;
                if (direction != 0 && crossingFe > 0 && flowing) {
                    this.lastDirection = direction;
                }
                return;
            }
            final double dt = time - this.lastTime;
            this.lastTime = time;
            if (direction != 0 && flowing && crossingFe > 0) {
                this.lastDirection = direction;
                this.lastPeriod = periodTicks(crossingFe);
                step(dt / this.lastPeriod);
                return;
            }
            final double left = indexLeft(periodFraction(this.q, this.lastDirection > 0));
            if (left > 0.0) {
                step(Math.min(dt / this.lastPeriod, left));
            }
        }

        static double indexLeft(double t) {
            if (t >= CONTACT_END) {
                return 1.0 - t + CONTACT_START;
            }
            return t < CONTACT_START ? CONTACT_START - t : 0.0;
        }

        private void step(double dp) {
            final double a = periodFraction(this.q, this.lastDirection > 0);
            this.q += dp * this.lastDirection;
            final double b = a + dp;
            this.swingsBegun += (int) (Math.floor(b) - Math.floor(a));
            this.seated += (int) (Math.floor(b - SWING) - Math.floor(a - SWING));
            this.broken += (int) (Math.floor(b - CONTACT_END) - Math.floor(a - CONTACT_END));
        }
    }

    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static double smooth(double u) {
        final double t = clamp(u, 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    public static double easeOutBack(double u, double s) {
        final double t = clamp(u, 0.0, 1.0) - 1.0;
        return 1.0 + (s + 1.0) * t * t * t + s * t * t;
    }
}
