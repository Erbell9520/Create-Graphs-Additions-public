package com.createstats.power;

public final class FeSupplyMemory {
    public static final int BUCKETS = 24;

    public static final int DAY_TICKS = SolarCurve.DAY_TICKS;

    public static final int BUCKET_TICKS = DAY_TICKS / BUCKETS;

    public static final float ALPHA = 0.02f;

    public static final int WARMUP_SAMPLES = 8;

    public static final int UNKNOWN = -1;

    private final float[] learned = new float[BUCKETS];
    private final int[] samples = new int[BUCKETS];

    public static int bucketOf(long dayTime) {
        return (int) (Math.floorMod(dayTime, (long) DAY_TICKS) / BUCKET_TICKS);
    }

    public void observe(long dayTime, int fePerTick) {
        final int bucket = bucketOf(dayTime);
        final float value = Math.max(0, fePerTick);
        if (samples[bucket] == 0) {
            learned[bucket] = value;
        } else {
            learned[bucket] += (value - learned[bucket]) * ALPHA;
        }
        if (samples[bucket] < Integer.MAX_VALUE) {
            samples[bucket]++;
        }
    }

    public int expectedFePerTick(long dayTime) {
        final int bucket = bucketOf(dayTime);
        return samples[bucket] < WARMUP_SAMPLES ? UNKNOWN : Math.round(learned[bucket]);
    }

    public int warmBuckets() {
        int warm = 0;
        for (int i = 0; i < BUCKETS; i++) {
            if (samples[i] >= WARMUP_SAMPLES) {
                warm++;
            }
        }
        return warm;
    }

    public boolean isWarm() {
        return warmBuckets() >= BUCKETS / 2;
    }

    public int projectedFeOver(long dayTime, int ticks) {
        if (ticks <= 0) {
            return 0;
        }
        int total = 0;
        long at = Math.floorMod(dayTime, (long) DAY_TICKS);
        int left = ticks;
        while (left > 0) {
            final int bucket = bucketOf(at);
            final int intoBucket = (int) (at % BUCKET_TICKS);
            final int span = Math.min(left, BUCKET_TICKS - intoBucket);
            final int rate = samples[bucket] >= WARMUP_SAMPLES ? Math.round(learned[bucket]) : 0;
            total = FeRates.plus(total, (int) Math.min(Integer.MAX_VALUE, (long) rate * span));
            at = (at + span) % DAY_TICKS;
            left -= span;
        }
        return total;
    }

    public int expectedFloorOver(long dayTime, int ticks) {
        if (ticks <= 0) {
            return expectedFePerTick(dayTime);
        }
        int floor = Integer.MAX_VALUE;
        long at = Math.floorMod(dayTime, (long) DAY_TICKS);
        int left = ticks;
        while (left > 0) {
            final int rate = expectedFePerTick(at);
            if (rate == UNKNOWN) {
                return UNKNOWN;
            }
            floor = Math.min(floor, rate);
            final int intoBucket = (int) (at % BUCKET_TICKS);
            final int span = Math.min(left, BUCKET_TICKS - intoBucket);
            at = (at + span) % DAY_TICKS;
            left -= span;
        }
        return floor == Integer.MAX_VALUE ? UNKNOWN : floor;
    }

    public int[] snapshotRates() {
        final int[] out = new int[BUCKETS];
        for (int i = 0; i < BUCKETS; i++) {
            out[i] = Math.round(learned[i]);
        }
        return out;
    }

    public int[] snapshotSamples() {
        return samples.clone();
    }

    public void restore(int[] rates, int[] counts) {
        if (rates == null || counts == null || rates.length != BUCKETS || counts.length != BUCKETS) {
            return;
        }
        for (int i = 0; i < BUCKETS; i++) {
            learned[i] = Math.max(0, rates[i]);
            samples[i] = Math.max(0, counts[i]);
        }
    }

    public void clear() {
        java.util.Arrays.fill(learned, 0.0f);
        java.util.Arrays.fill(samples, 0);
    }
}
