package com.createstats.chunkloader.core;

// every held tick is paid, can't pay = drop everything same tick. no grace counter
public final class CrownHold {
    public enum Phase {
        DARK,

        FED,

        RIDING;

        public static Phase of(int ordinal) {
            final Phase[] all = values();
            return ordinal >= 0 && ordinal < all.length ? all[ordinal] : DARK;
        }
    }

    public enum Verdict {
        STAY_DARK,

        HOLD,

        ACQUIRE,

        RELEASE_ALL
    }

    public static final int REACQUIRE_TICKS = 10;

    private long stored;
    private long arrived;
    private long lastArrived;
    private Phase phase = Phase.DARK;

    private int sinceAcquire = REACQUIRE_TICKS;

    public long stored() {
        return stored;
    }

    public Phase phase() {
        return phase;
    }

    public long lastArrived() {
        return lastArrived;
    }

    public long room(long capacity, long draw) {
        return Math.max(0L, capacity + Math.max(0L, draw) - stored);
    }

    public long accept(long offered, long capacity, long draw) {
        final long take = Math.min(Math.max(0L, offered), room(capacity, draw));
        if (take > 0L) {
            stored += take;
            arrived += take;
        }
        return take;
    }

    public Verdict step(int held, int picked, CrownLaw law, boolean mayHold) {
        final long in = arrived;
        arrived = 0L;
        lastArrived = in;
        if (sinceAcquire < REACQUIRE_TICKS) {
            sinceAcquire++;
        }
        if (!mayHold) {
            phase = Phase.DARK;
            return held > 0 ? Verdict.RELEASE_ALL : Verdict.STAY_DARK;
        }
        if (held > 0) {
            final long draw = law.totalDraw(held);
            if (stored < draw) {
                phase = Phase.DARK;
                sinceAcquire = 0;
                return Verdict.RELEASE_ALL;
            }
            stored -= draw;
            phase = in >= draw ? Phase.FED : Phase.RIDING;
            return held < picked && readyFor(held + 1, law) ? Verdict.ACQUIRE : Verdict.HOLD;
        }
        phase = Phase.DARK;
        return picked > 0 && readyFor(1, law) ? Verdict.ACQUIRE : Verdict.STAY_DARK;
    }

    private boolean readyFor(int count, CrownLaw law) {
        if (sinceAcquire < REACQUIRE_TICKS || stored < law.rideTicks() * law.totalDraw(count)) {
            return false;
        }
        sinceAcquire = 0;
        return true;
    }

    public long rideThroughTicks(long draw) {
        return draw <= 0L ? 0L : stored / draw;
    }

    public void restore(long storedFe, Phase savedPhase) {
        stored = Math.max(0L, storedFe);
        phase = savedPhase == null ? Phase.DARK : savedPhase;
        arrived = 0L;
        lastArrived = 0L;
        sinceAcquire = REACQUIRE_TICKS;
    }
}
