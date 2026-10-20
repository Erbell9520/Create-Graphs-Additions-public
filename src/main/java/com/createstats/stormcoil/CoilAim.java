package com.createstats.stormcoil;

import java.util.Arrays;
import java.util.function.IntPredicate;

// wind up before every volley so you can see it think
public final class CoilAim {
    public static final int RETRY = 10;

    public static final int MAX = 4;

    public enum Step {
        WAIT,

        LOOK,

        FIRE
    }

    private long aimAt = Long.MIN_VALUE;
    private long nextLook;
    private int[] ids = new int[0];

    public Step step(long now) {
        if (aiming()) {
            return now >= fireAt(aimAt) ? Step.FIRE : Step.WAIT;
        }
        return now >= nextLook ? Step.LOOK : Step.WAIT;
    }

    public void aim(long now, int[] targets) {
        aimAt = now;
        ids = Arrays.copyOf(targets, Math.min(MAX, targets.length));
    }

    public void nothing(long now) {
        nextLook = now + RETRY;
    }

    public void fired(long now, int hit) {
        aimAt = Long.MIN_VALUE;
        ids = new int[0];
        nextLook = hit > 0 ? nextAim(now) : now + RETRY;
    }

    public void reset() {
        aimAt = Long.MIN_VALUE;
        ids = new int[0];
        nextLook = 0;
    }

    public boolean aiming() {
        return aimAt != Long.MIN_VALUE;
    }

    public long aimAt() {
        return aimAt;
    }

    public int[] ids() {
        return ids.clone();
    }

    public static long fireAt(long aimAt) {
        return aimAt + CoilLaw.WIND_UP;
    }

    public static long nextAim(long firedAt) {
        return firedAt + CoilLaw.ZAP_INTERVAL - CoilLaw.WIND_UP;
    }

    public static double progress(double t, long aimAt) {
        if (aimAt == Long.MIN_VALUE) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, (t - aimAt) / CoilLaw.WIND_UP));
    }

    public static int[] kept(int[] aimed, IntPredicate stillThere) {
        return Arrays.stream(aimed).filter(stillThere).toArray();
    }
}
