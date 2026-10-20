package com.createstats.power;

public final class FeChargePolicy {
    private FeChargePolicy() {
    }

    public static final int DEFAULT_MAX_CLAIM_PCT = 60;

    public static int claim(int generation, int socPct, int capacity, FeReserveBand.Band band,
                            int maxClaimPct, int headroom) {
        if (generation <= 0 || capacity <= 0 || headroom <= 0) {
            return 0;
        }
        final FeReserveBand.Band shape = band == null ? FeReserveBand.Band.DEFAULT : band;
        final int soc = Math.max(0, Math.min(100, socPct));
        final int ceiling = Math.max(0, Math.min(100, maxClaimPct));
        if (ceiling <= 0 || soc >= shape.healthyPct()) {
            return 0;
        }

        final float fraction;
        if (soc <= shape.floorPct()) {
            fraction = ceiling / 100.0f;
        } else {
            final int span = Math.max(1, shape.healthyPct() - shape.floorPct());
            final float above = (soc - shape.floorPct()) / (float) span;
            fraction = (ceiling / 100.0f) * (1.0f - above);
        }

        final int claimed = Math.round(generation * fraction);

        return Math.max(0, Math.min(Math.min(claimed, headroom), Math.max(0, generation - 1)));
    }

    public static int claimable(int bus, int fromReserve, int criticalWant) {
        return Math.max(0, FeRates.plus(Math.max(0, bus), Math.max(0, fromReserve)) - Math.max(0, criticalWant));
    }
}
