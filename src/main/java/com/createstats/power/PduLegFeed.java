package com.createstats.power;

public final class PduLegFeed {
    private PduLegFeed() {
    }

    public static int price(boolean ownGenerator, int potential, int simulated, int cap) {
        final int offered = ownGenerator ? potential : simulated;
        return Math.max(0, Math.min(offered, Math.max(0, cap)));
    }

    public static int addToLeg(int legSoFar, int price, int legRating) {
        final int room = Math.max(0, legRating - Math.max(0, legSoFar));
        return Math.max(0, legSoFar) + Math.min(Math.max(0, price), room);
    }
}
