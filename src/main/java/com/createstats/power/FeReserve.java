package com.createstats.power;

public interface FeReserve {
    int MIN_RANK = 1;

    int MAX_RANK = 9;

    int DEFAULT_RANK = 5;

    int reserveRank();

    default long reservePoolKey() {
        return NOT_POOLED;
    }

    long NOT_POOLED = Long.MIN_VALUE;

    static int clamp(int rank) {
        return Math.max(MIN_RANK, Math.min(MAX_RANK, rank));
    }

    static String describe(int rank) {
        final int clamped = clamp(rank);
        if (clamped == MIN_RANK) {
            return "Rank 1, core (drains last, fills first)";
        }
        if (clamped == MAX_RANK) {
            return "Rank " + clamped + ", outermost (drains first, fills last)";
        }
        return "Rank " + clamped + ", reserve (drains before rank " + (clamped - 1) + ")";
    }
}
