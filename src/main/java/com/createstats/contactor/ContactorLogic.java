package com.createstats.contactor;

public final class ContactorLogic {
    private ContactorLogic() {
    }

    public static final int LEFT = 0;
    public static final int MIDDLE = 1;
    public static final int RIGHT = 2;
    public static final int POLES = 3;

    public static final int STUDS = 6;

    public static boolean isStud(int stud) {
        return stud >= 0 && stud < STUDS;
    }

    public static int poleOf(int stud) {
        return Math.floorMod(stud, POLES);
    }

    public static boolean isLine(int stud) {
        return stud >= 0 && stud < POLES;
    }

    public static int pairedStud(int stud) {
        return isStud(stud) ? (stud + POLES) % STUDS : -1;
    }

    public static String studName(int stud) {
        return (isLine(stud) ? "L" : "T") + (poleOf(stud) + 1);
    }

    public static boolean operated(int pole, boolean leftIn, boolean rightIn) {
        return switch (pole) {
            case LEFT -> leftIn;
            case RIGHT -> rightIn;
            case MIDDLE -> leftIn && rightIn;
            default -> false;
        };
    }

    public static boolean poleClosed(int pole, boolean leftIn, boolean rightIn, boolean normallyClosed) {
        return operated(pole, leftIn, rightIn) != normallyClosed;
    }

    public static boolean[] poles(boolean leftIn, boolean rightIn, boolean normallyClosed) {
        final boolean[] out = new boolean[POLES];
        for (int pole = 0; pole < POLES; pole++) {
            out[pole] = poleClosed(pole, leftIn, rightIn, normallyClosed);
        }
        return out;
    }

    public enum Flag {
        I, O
    }

    public static Flag flag(boolean poleClosed) {
        return poleClosed ? Flag.I : Flag.O;
    }

    public static int comparatorLevel(boolean[] closed, int[] carried, int[] rating) {
        boolean any = false;
        double share = 0.0;
        for (int pole = 0; pole < POLES; pole++) {
            if (!closed[pole]) {
                continue;
            }
            any = true;
            if (carried[pole] <= 0) {
                continue;
            }
            share = Math.max(share, rating[pole] <= 0 ? 1.0 : Math.min(1.0, carried[pole] / (double) rating[pole]));
        }
        if (!any) {
            return 0;
        }
        return 1 + (int) Math.round(14.0 * share);
    }

    public static int sparksPerPole(int carried) {
        if (carried <= 0) {
            return 0;
        }
        return Math.min(MAX_SPARKS_PER_POLE, 1 + log2(carried) / 3);
    }

    public static final int MAX_SPARKS_PER_POLE = 6;

    public static double sparkSpeed(int carried) {
        if (carried <= 0) {
            return 0.0;
        }
        return 0.06 + 0.012 * Math.min(16, log2(carried));
    }

    public static int chutePuffs(int carried) {
        if (carried < 1_024) {
            return 0;
        }
        return carried < 8_192 ? 1 : 2;
    }

    static int log2(int n) {
        return 31 - Integer.numberOfLeadingZeros(Math.max(1, n));
    }

    public static final long NEVER = Long.MIN_VALUE;

    public static final int DWELL_TICKS = 4;

    public enum Step {
        STAY,

        MOVE,

        WAIT
    }

    public static Step step(boolean coil, boolean armatureIn, long lastMoveTick, long now) {
        if (coil == armatureIn) {
            return Step.STAY;
        }
        return due(lastMoveTick, now, DWELL_TICKS) ? Step.MOVE : Step.WAIT;
    }

    public static int ticksToDwellEnd(long lastMoveTick, long now) {
        if (lastMoveTick == NEVER) {
            return 1;
        }
        final long left = lastMoveTick + DWELL_TICKS - now;
        return (int) Math.max(1L, Math.min(DWELL_TICKS, left));
    }

    public static boolean due(long lastTick, long now, int gapTicks) {
        if (lastTick == NEVER) {
            return true;
        }
        final long since = now - lastTick;
        return since < 0 || since >= gapTicks;
    }

    public static final int LOAD_SYNC_TICKS = 10;

    public static boolean worthSending(int sent, int now) {
        if (sent == now) {
            return false;
        }
        if (sent <= 0 || now <= 0) {
            return true;
        }
        return Math.abs((long) now - sent) * 50L >= Math.max(sent, now);
    }
}
