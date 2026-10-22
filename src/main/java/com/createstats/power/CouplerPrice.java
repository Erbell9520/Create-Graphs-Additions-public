package com.createstats.power;

public final class CouplerPrice {
    private CouplerPrice() {
    }

    public static int pushPrice(int rating, int accepts, boolean forcedOff, boolean looped) {
        if (forcedOff || looped) {
            return 0;
        }
        return clamp(accepts, rating);
    }

    public static int newAgeSupply(int rating, int pulledGeneration, int theirDemand) {
        return clamp((long) Math.max(0, pulledGeneration) - Math.max(0, theirDemand), rating);
    }

    public static int feedSupply(int rating, int couldGive) {
        return clamp(couldGive, rating);
    }

    private static int clamp(long v, int rating) {
        return (int) Math.max(0L, Math.min(Math.max(0, rating), v));
    }

    public static final class Smoothed {
        private int value;
        private long tick = Long.MIN_VALUE;

        public int value() {
            return this.value;
        }

        public int observe(int sample, long now) {
            final int s = Math.max(0, sample);
            if (now == this.tick) {
                this.value = Math.max(this.value, s);
                return this.value;
            }
            this.tick = now;
            if (s >= this.value) {
                this.value = s;
            } else {
                final int fall = Math.max(1, (this.value - s) / 4);
                this.value = Math.max(s, this.value - fall);
            }
            return this.value;
        }

        public void reset() {
            this.value = 0;
            this.tick = Long.MIN_VALUE;
        }
    }
}
