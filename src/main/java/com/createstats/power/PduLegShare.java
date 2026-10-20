package com.createstats.power;

// walking legs in index order handed leg 0 the whole buffer the moment things got tight
public final class PduLegShare {
    private PduLegShare() {
    }

    public static int[] split(int[] want, int pool, int cursor) {
        return split(want, null, pool, cursor);
    }

    public static int[] split(int[] want, int[] legTier, int pool, int cursor) {
        final int[] budget = new int[want == null ? 0 : want.length];
        if (want == null || want.length == 0 || pool <= 0) {
            return budget;
        }
        if (legTier == null || legTier.length != want.length) {
            shareBand(want, budget, null, 0, pool, cursor);
            return budget;
        }
        int remaining = pool;
        for (int band = 0; band < FePriority.values().length && remaining > 0; band++) {
            remaining -= shareBand(want, budget, legTier, band, remaining, cursor);
        }
        return budget;
    }

    private static int shareBand(int[] want, int[] budget, int[] legTier, int band, int pool,
                                 int cursor) {
        if (pool <= 0) {
            return 0;
        }
        int claimants = 0;
        for (int leg = 0; leg < want.length; leg++) {
            if (want[leg] > 0 && (legTier == null || legTier[leg] == band)) {
                claimants++;
            }
        }
        if (claimants == 0) {
            return 0;
        }
        final int start = Math.floorMod(cursor, want.length);

        final int share = Math.max(1, pool / claimants);
        int remaining = pool;

        for (int i = 0; i < want.length && remaining > 0; i++) {
            final int leg = (start + i) % want.length;
            if (want[leg] <= 0 || (legTier != null && legTier[leg] != band)) {
                continue;
            }
            final int take = Math.min(Math.min(share, want[leg]), remaining);
            budget[leg] = take;
            remaining -= take;
        }
        for (int i = 0; i < want.length && remaining > 0; i++) {
            final int leg = (start + i) % want.length;
            if (want[leg] <= budget[leg] || (legTier != null && legTier[leg] != band)) {
                continue;
            }
            final int take = Math.min(want[leg] - budget[leg], remaining);
            budget[leg] += take;
            remaining -= take;
        }
        return pool - remaining;
    }

    public static int addRates(int a, int b) {
        return FeRates.plus(Math.max(0, a), Math.max(0, b));
    }
}
