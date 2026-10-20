package com.createstats.home;

// sleeping jumps the day clock, /time set moves it. careful
public final class DayMeter {
    public static final int HOURS = 24;
    public static final int TICKS_PER_HOUR = 1000;

    private final long[] fe = new long[HOURS];

    private long lastHour = -1;

    public void add(long dayTime, long amount) {
        final long hour = Math.max(0, dayTime) / TICKS_PER_HOUR;
        roll(hour);
        final int slot = (int) (hour % HOURS);
        fe[slot] = saturatingAdd(fe[slot], Math.max(0, amount));
    }

    public void advanceTo(long dayTime) {
        roll(Math.max(0, dayTime) / TICKS_PER_HOUR);
    }

    private void roll(long hour) {
        if (lastHour < 0) {
            lastHour = hour;
            return;
        }
        if (hour < lastHour) {
            java.util.Arrays.fill(fe, 0);
        } else if (hour > lastHour) {
            final long steps = Math.min(HOURS, hour - lastHour);
            for (long h = 1; h <= steps; h++) {
                fe[(int) ((lastHour + h) % HOURS)] = 0;
            }
        }
        lastHour = hour;
    }

    public int[] chart(long dayTime) {
        advanceTo(dayTime);
        final long now = Math.max(0, dayTime) / TICKS_PER_HOUR;
        final int[] out = new int[HOURS];
        for (int i = 0; i < HOURS; i++) {
            final long hour = now - (HOURS - 1) + i;
            out[i] = hour < 0 ? 0 : (int) Math.min(Integer.MAX_VALUE, fe[(int) (hour % HOURS)]);
        }
        return out;
    }

    public static int hourOfDay(long dayTime) {
        return (int) ((Math.max(0, dayTime) / TICKS_PER_HOUR) % HOURS);
    }

    public long[] raw() {
        return fe.clone();
    }

    public long lastHour() {
        return lastHour;
    }

    public void load(long[] saved, long savedLastHour) {
        java.util.Arrays.fill(fe, 0);
        if (saved != null && saved.length == HOURS) {
            for (int i = 0; i < HOURS; i++) {
                fe[i] = Math.max(0, saved[i]);
            }
            lastHour = savedLastHour;
        } else {
            lastHour = -1;
        }
    }

    static long saturatingAdd(long a, long b) {
        final long sum = a + b;
        return sum < a ? Long.MAX_VALUE : sum;
    }
}
