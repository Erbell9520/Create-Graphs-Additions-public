package com.createstats.power;

import javax.annotation.Nullable;

/** [1] */
public final class PduStudWiring {

    private PduStudWiring() {
    }

    /**
     * One end of a wire span: a Power Connector, or one output stud on a panel.
     *
     * @param pos  the packed position of the connector, or of the panel when this is a stud
     * @param stud the stud index for a panel end, or {@link #CONNECTOR} for a connector end
     */
    public record Endpoint(long pos, int stud) {

        /** Stud index meaning "this end is a Power Connector, not a stud". */
        public static final int CONNECTOR = -1;

        public static Endpoint connector(long pos) {
            return new Endpoint(pos, CONNECTOR);
        }

        public static Endpoint stud(long pos, int stud) {
            return new Endpoint(pos, stud);
        }

        /** [2] */
        public boolean isStud() {
            return stud != CONNECTOR;
        }

        /** True when both ends are literally the same terminal, which is the cancel gesture. */
        public boolean sameAs(Endpoint other) {
            return other != null && pos == other.pos && stud == other.stud;
        }
    }

    /** Whether a proposed span may be strung, and when it may not, why. */
    public enum Verdict {
        OK,
        /** Both clicks landed on the same terminal. The item reads this as cancelling. */
        SAME_POINT,
        /** Stud to stud. A panel feeds machines, it does not feed itself. */
        BOTH_STUDS,
        /** The stud index is not one of the eight, so the click resolved to no terminal at all. */
        BAD_STUD,
        TOO_FAR,
        ALREADY_LINKED,
        /** One end has no free wire slot left. */
        FULL
    }

    /** True when this index names one of the panel's real output studs. */
    public static boolean isStudIndex(int stud) {
        return stud >= 0 && stud < PduStuds.COUNT;
    }

    /**
     * Judges a proposed span between two endpoints.
     *
     * <p>Order matters here in the same way it does on the leg rows: the most specific thing wrong is
     * what gets reported, so a player is never told a wire is too long when the real problem is that
     * they clicked the same stud twice.</p>
     *
     * @param from          the endpoint clicked first
     * @param to            the endpoint clicked second
     * @param distanceSq    squared distance between the two block positions
     * @param range         how many blocks a wire reaches
     * @param alreadyLinked whether these two terminals are already joined
     * @param fromLinks     wires already on the first endpoint
     * @param toLinks       wires already on the second endpoint
     * @param maxLinks      wires one terminal may hold
     */
    public static Verdict check(Endpoint from, Endpoint to, double distanceSq, int range,
                                boolean alreadyLinked, int fromLinks, int toLinks, int maxLinks) {
        if (from == null || to == null) {
            return Verdict.BAD_STUD;
        }
        if (from.sameAs(to)) {
            return Verdict.SAME_POINT;
        }
        if (from.isStud() && !isStudIndex(from.stud())
                || to.isStud() && !isStudIndex(to.stud())) {
            return Verdict.BAD_STUD;
        }
        if (from.isStud() && to.isStud()) {
            // [3]
            return Verdict.BOTH_STUDS;
        }
        if (distanceSq > (double) range * range) {
            return Verdict.TOO_FAR;
        }
        if (alreadyLinked) {
            return Verdict.ALREADY_LINKED;
        }
        if (fromLinks >= maxLinks || toLinks >= maxLinks) {
            return Verdict.FULL;
        }
        return Verdict.OK;
    }

    /**
     * What one leg can actually carry.
     *
     * <p>The stud itself is a built in terminal at the best grade a connector reaches, so the panel is
     * never the reason an upgraded run is capped. What limits a leg is the wire strung from it, and a
     * leg with no wire on it carries nothing at all: that zero is what the panel reads as "not wired",
     * so it has to be a real zero rather than a fallback to the stud's own rating.</p>
     *
     * @param studRate what one stud can push
     * @param baseRate the rate one plain connector on plain copper moves
     * @param best     the best grade strung to this stud, or null when nothing is
     */
    public static int ceiling(int studRate, int baseRate, @Nullable WireTier best) {
        if (best == null) {
            return 0;
        }
        return Math.min(studRate, baseRate * best.multiplier());
    }

    /** [4] */
    @Nullable
    public static WireTier best(Iterable<WireTier> tiers) {
        WireTier best = null;
        for (WireTier tier : tiers) {
            if (tier != null && (best == null || tier.multiplier() > best.multiplier())) {
                best = tier;
            }
        }
        return best;
    }
}
