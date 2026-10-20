package com.createstats.chunkloader.core;

// k-th chunk costs base*growth^(k-1). 1024 for one, ~100k for 25. exponential on purpose
public record CrownLaw(int baseFePerTick, int growthPercent, int maxPerStation, int maxPerPlayer, int rideSeconds) {
    public static final int MAX_FORM = 25;

    public static final CrownLaw DEFAULT = new CrownLaw(1_024, 10, MAX_FORM, 50, 5);

    public CrownLaw {
        baseFePerTick = Math.max(1, baseFePerTick);
        growthPercent = Math.max(0, growthPercent);
        maxPerStation = Math.max(1, Math.min(MAX_FORM, maxPerStation));
        maxPerPlayer = Math.max(1, maxPerPlayer);
        rideSeconds = Math.max(1, rideSeconds);
    }

    public double growth() {
        return 1.0 + growthPercent / 100.0;
    }

    public double chunkCost(int k) {
        return k < 1 ? 0.0 : baseFePerTick * Math.pow(growth(), k - 1);
    }

    public int totalDraw(int n) {
        if (n <= 0) {
            return 0;
        }
        final double g = growth();
        final double exact = growthPercent == 0 ? (double) baseFePerTick * n
                : baseFePerTick * (Math.pow(g, n) - 1.0) / (g - 1.0);
        return exact >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.round(exact);
    }

    public int nextPrice(int held) {
        if (held >= maxPerStation) {
            return 0;
        }
        final long d = (long) totalDraw(held + 1) - totalDraw(held);
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, d));
    }

    public long rideTicks() {
        return rideSeconds * 20L;
    }

    public long storeCapacity(int picked) {
        return rideTicks() * totalDraw(picked);
    }

    public int lightLevel(int held) {
        if (held <= 0) {
            return 0;
        }
        final double full = totalDraw(maxPerStation);
        final double share = full <= 0 ? 1.0 : Math.min(1.0, totalDraw(held) / full);
        return Math.max(1, Math.min(15, (int) Math.round(15.0 * share)));
    }
}
