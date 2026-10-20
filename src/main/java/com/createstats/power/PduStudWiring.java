package com.createstats.power;

import javax.annotation.Nullable;

public final class PduStudWiring {
    private PduStudWiring() {
    }

    public record Endpoint(long pos, int stud) {
        public static final int CONNECTOR = -1;

        public static Endpoint connector(long pos) {
            return new Endpoint(pos, CONNECTOR);
        }

        public static Endpoint stud(long pos, int stud) {
            return new Endpoint(pos, stud);
        }

        public boolean isStud() {
            return stud != CONNECTOR;
        }

        public boolean sameAs(Endpoint other) {
            return other != null && pos == other.pos && stud == other.stud;
        }
    }

    public enum Verdict {
        OK,

        SAME_POINT,

        BOTH_STUDS,

        BAD_STUD,
        TOO_FAR,
        ALREADY_LINKED,

        FULL
    }

    public static boolean isStudIndex(int stud) {
        return stud >= 0 && stud < PduStuds.COUNT;
    }

    public static Verdict check(Endpoint from, Endpoint to, double distanceSq, int range,
                                boolean alreadyLinked, int fromLinks, int toLinks,
                                int fromMaxLinks, int toMaxLinks) {
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
            return Verdict.BOTH_STUDS;
        }
        if (distanceSq > (double) range * range) {
            return Verdict.TOO_FAR;
        }
        if (alreadyLinked) {
            return Verdict.ALREADY_LINKED;
        }
        if (fromLinks >= fromMaxLinks || toLinks >= toMaxLinks) {
            return Verdict.FULL;
        }
        return Verdict.OK;
    }

    public static int ceiling(int studRate, int baseRate, @Nullable WireTier best) {
        if (best == null) {
            return 0;
        }
        return Math.min(studRate, baseRate * best.multiplier());
    }

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
