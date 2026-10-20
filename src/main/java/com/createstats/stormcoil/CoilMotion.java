package com.createstats.stormcoil;

// no stepping, ever
public final class CoilMotion {
    public static final int BASE_TICKS = 8;
    public static final int TICKS_PER_STAGE = 10;

    public static final double OVERSHOOT = 1.25;
    public static final int SETTLE_TICKS = 14;

    public static final int GLOW_TICKS = 10;

    public static final int STUDS = 8;

    public static final double FULL_DEGREES_PER_TICK = 22.5;

    public static final double IDLE_SPEED = 0.12;

    public static final int GAP_UP_TICKS = 10;

    public static final int GAP_DOWN_TICKS = 60;

    public static final double SPARK_FROM = 0.6;

    public static final int FIGHT_HOLD = 30;

    public static final int NUDGE_EVERY = 80;

    public static final int NUDGE_TICKS = 16;

    public static final double NUDGE = 0.07;

    public static final double CHARGE_EASE = 0.3;

    public static final int DUCK_TICKS = 8;

    public static final double LOAD_EASE = 0.2;

    public enum Gap {
        STILL,

        IDLE,

        RUN
    }

    private boolean started;
    private double from = 1;
    private double to = 1;
    private int age;
    private int duration;
    private int settleAge = SETTLE_TICKS;
    private double glow;
    private double glowBefore;

    private Gap gapWant = Gap.STILL;
    private double gapSpeed;
    private double gapSpeedBefore;
    private double gapAngle;
    private double gapAngleBefore;

    private boolean blocked;
    private int blockedAge;
    private int blockedAgeBefore;

    private boolean duckStarted;
    private boolean ducking;
    private double duck;
    private double duckBefore;

    private boolean loadStarted;
    private double loadWant;
    private double load;
    private double loadBefore;

    private boolean chargeStarted;
    private double capWant;
    private double bankWant;
    private double cap;
    private double capBefore;
    private double bank;
    private double bankBefore;

    public void aim(int stage) {
        final int target = Math.max(1, Math.min(4, stage));
        if (!started) {
            started = true;
            from = target;
            to = target;
            age = 0;
            duration = 0;
            settleAge = SETTLE_TICKS;
            return;
        }
        if (target == to) {
            return;
        }
        from = height(0f);
        to = target;
        age = 0;
        duration = BASE_TICKS + (int) Math.round(TICKS_PER_STAGE * Math.abs(to - from));
        settleAge = SETTLE_TICKS;
    }

    public void gap(Gap want) {
        gapWant = want == null ? Gap.STILL : want;
    }

    public void blocked(boolean b) {
        blocked = b;
    }

    public void headroom(boolean clear) {
        ducking = !clear;
        if (!duckStarted) {
            duckStarted = true;
            duck = duckBefore = ducking ? 1.0 : 0.0;
        }
    }

    public double duck(float partial) {
        return duckBefore + (duck - duckBefore) * partial;
    }

    public void load(double share) {
        loadWant = clamp01(share);
        if (!loadStarted) {
            loadStarted = true;
            load = loadBefore = loadWant;
        }
    }

    public double load(float partial) {
        return loadBefore + (load - loadBefore) * partial;
    }

    public void charge(double capacitor, double stormBank) {
        capWant = clamp01(capacitor);
        bankWant = clamp01(stormBank);
        if (!chargeStarted) {
            chargeStarted = true;
            cap = capBefore = capWant;
            bank = bankBefore = bankWant;
        }
    }

    public void tick(boolean lit) {
        if (age < duration) {
            age++;
            if (age >= duration) {
                settleAge = 0;
            }
        } else if (settleAge < SETTLE_TICKS) {
            settleAge++;
        }
        glowBefore = glow;
        glow = Math.max(0.0, Math.min(1.0, glow + (lit ? 1.0 : -1.0) / GLOW_TICKS));

        gapSpeedBefore = gapSpeed;
        gapAngleBefore = gapAngle;
        final double want = gapTarget(gapWant);
        gapSpeed = want > gapSpeed ? Math.min(want, gapSpeed + 1.0 / GAP_UP_TICKS)
                : Math.max(want, gapSpeed - 1.0 / GAP_DOWN_TICKS);
        if (Math.abs(gapSpeed - want) < 1e-9) {
            gapSpeed = want;
        }
        gapAngle += FULL_DEGREES_PER_TICK * (gapSpeedBefore + gapSpeed) / 2.0;
        if (gapAngle >= 3600.0) {
            gapAngle -= 3600.0;
            gapAngleBefore -= 3600.0;
        }

        blockedAgeBefore = blockedAge;
        blockedAge = blocked && !moving() ? blockedAge + 1 : 0;

        loadBefore = load;
        load += (loadWant - load) * LOAD_EASE;
        duckBefore = duck;
        duck = Math.max(0.0, Math.min(1.0, duck + (ducking ? 1.0 : -1.0) / DUCK_TICKS));

        capBefore = cap;
        bankBefore = bank;
        cap += (capWant - cap) * CHARGE_EASE;
        bank += (bankWant - bank) * CHARGE_EASE;
    }

    public double height(float partial) {
        if (duration <= 0 || age >= duration) {
            return to;
        }
        final double u = Math.min(1.0, (age + partial) / duration);
        return Math.max(1.0, Math.min(4.0, from + (to - from) * easeOutBack(u)));
    }

    public int target() {
        return (int) to;
    }

    public boolean moving() {
        return age < duration;
    }

    public double settle(float partial) {
        if (settleAge >= SETTLE_TICKS) {
            return 0.0;
        }
        final double t = settleAge + partial;
        return Math.sin(t * Math.PI / 3.0) * Math.exp(-t / 4.0);
    }

    public double glow(float partial) {
        return glowBefore + (glow - glowBefore) * partial;
    }

    public double gapSpeed() {
        return gapSpeed;
    }

    public double gapSpeed(float partial) {
        return gapSpeedBefore + (gapSpeed - gapSpeedBefore) * partial;
    }

    public double gapAngle(float partial) {
        return gapAngleBefore + (gapAngle - gapAngleBefore) * partial;
    }

    public double gapSpark(float partial) {
        return spark(gapAngle(partial), gapSpeed(partial));
    }

    public static double spark(double angle, double speed) {
        final double spacing = 360.0 / STUDS;
        final double f = (angle % spacing + spacing) % spacing / spacing;
        final double flash = f < 0.5 ? 1.0 - f / 0.5 : 0.0;
        final double fast = Math.max(0.0, Math.min(1.0, (speed - SPARK_FROM) / (1.0 - SPARK_FROM)));
        return flash * fast;
    }

    public static Gap gapFor(boolean alive, boolean drawing, boolean windingUp, long now, long lastZap) {
        final boolean fighting = lastZap != Long.MIN_VALUE && lastZap <= now && now - lastZap < FIGHT_HOLD;
        if (drawing || windingUp || fighting) {
            return Gap.RUN;
        }
        return alive ? Gap.IDLE : Gap.STILL;
    }

    public static double gapTarget(Gap want) {
        return switch (want) {
            case RUN -> 1.0;
            case IDLE -> IDLE_SPEED;
            case STILL -> 0.0;
        };
    }

    public double nudge(float partial) {
        if (blockedAge <= 0) {
            return 0.0;
        }
        final double t = blockedAgeBefore + (blockedAge - blockedAgeBefore) * partial;
        return NUDGE * nudgeShape(t);
    }

    public static double nudgeShape(double age) {
        final double into = age % NUDGE_EVERY - (NUDGE_EVERY - NUDGE_TICKS);
        if (age < NUDGE_EVERY - NUDGE_TICKS || into < 0) {
            return 0.0;
        }
        final double u = into / NUDGE_TICKS;
        final double peak = 0.18;
        if (u < peak) {
            return Math.sin(Math.PI / 2 * u / peak);
        }
        final double v = (u - peak) / (1.0 - peak);
        return Math.exp(-4.5 * v) * Math.cos(1.5 * Math.PI * v);
    }

    public double charge(float partial) {
        return capBefore + (cap - capBefore) * partial;
    }

    public double bankShown(float partial) {
        return bankBefore + (bank - bankBefore) * partial;
    }

    static double easeOutBack(double u) {
        final double v = u - 1.0;
        return 1.0 + (OVERSHOOT + 1.0) * v * v * v + OVERSHOOT * v * v;
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
