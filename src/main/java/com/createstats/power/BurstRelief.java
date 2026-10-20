package com.createstats.power;

public final class BurstRelief {
    private int total;

    public void add(int wanted, int got, boolean burstSource, boolean reachable) {
        if (burstSource || !reachable) {
            return;
        }
        total = FeRates.plus(total, Math.max(0, Math.max(0, wanted) - Math.max(0, got)));
    }

    public int total() {
        return total;
    }
}
