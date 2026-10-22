package com.createstats.power;

public final class CouplerRating {
    private CouplerRating() {
    }

    public static int rating(int ourBase, long theirBest, int store) {
        final long best = Math.max(Math.max(0, ourBase), Math.max(0L, theirBest));
        return (int) Math.max(1L, Math.min(Math.max(1, store), best));
    }

    public static int binding(int rating, int wireRate) {
        return wireRate < 0 ? rating : Math.max(0, Math.min(rating, wireRate));
    }
}
