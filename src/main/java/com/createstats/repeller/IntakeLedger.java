package com.createstats.repeller;

public final class IntakeLedger {
    private long tick = Long.MIN_VALUE;
    private int sum;
    private long previousTick = Long.MIN_VALUE;
    private int previousSum;

    public void record(long now, int fe) {
        if (fe <= 0) {
            return;
        }
        roll(now);
        sum = sum + fe < 0 ? Integer.MAX_VALUE : sum + fe;
    }

    public int completedBefore(long now) {
        roll(now);
        return previousTick == now - 1 ? previousSum : 0;
    }

    private void roll(long now) {
        if (now == tick) {
            return;
        }
        if (now > tick) {
            previousTick = tick;
            previousSum = sum;
        } else {
            previousTick = Long.MIN_VALUE;
            previousSum = 0;
        }
        tick = now;
        sum = 0;
    }
}
