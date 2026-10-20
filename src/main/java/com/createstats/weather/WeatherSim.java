package com.createstats.weather;

import java.util.random.RandomGenerator;

// line for line advanceWeatherCycle. a version bump has one place to check
public final class WeatherSim {
    public static final int RAIN_DELAY_MIN = 12000;
    public static final int RAIN_DELAY_MAX = 180000;
    public static final int RAIN_DURATION_MIN = 12000;
    public static final int RAIN_DURATION_MAX = 24000;
    public static final int THUNDER_DELAY_MIN = 12000;
    public static final int THUNDER_DELAY_MAX = 180000;
    public static final int THUNDER_DURATION_MIN = 3600;
    public static final int THUNDER_DURATION_MAX = 15600;

    private WeatherSim() {
    }

    private static int sample(RandomGenerator rng, int min, int max) {
        return rng.nextInt(max - min + 1) + min;
    }

    public static SimState step(SimState s, RandomGenerator rng) {
        int i = s.clearWeatherTime();
        int j = s.thunderTime();
        int k = s.rainTime();
        boolean thundering = s.thundering();
        boolean raining = s.raining();

        if (i > 0) {
            i--;
            j = thundering ? 0 : 1;
            k = raining ? 0 : 1;
            thundering = false;
            raining = false;
        } else {
            if (j > 0) {
                if (--j == 0) {
                    thundering = !thundering;
                }
            } else if (thundering) {
                j = sample(rng, THUNDER_DURATION_MIN, THUNDER_DURATION_MAX);
            } else {
                j = sample(rng, THUNDER_DELAY_MIN, THUNDER_DELAY_MAX);
            }

            if (k > 0) {
                if (--k == 0) {
                    raining = !raining;
                }
            } else if (raining) {
                k = sample(rng, RAIN_DURATION_MIN, RAIN_DURATION_MAX);
            } else {
                k = sample(rng, RAIN_DELAY_MIN, RAIN_DELAY_MAX);
            }
        }
        return new SimState(i, j, k, thundering, raining);
    }

    public static SimState advanceTo(SimState s, int ticks, RandomGenerator rng) {
        int left = ticks;
        while (left > 0) {
            if (s.clearWeatherTime() > 0) {
                final int jump = Math.min(left, s.clearWeatherTime());

                s = new SimState(s.clearWeatherTime() - jump,
                        s.thundering() ? 0 : 1, s.raining() ? 0 : 1, false, false);
                left -= jump;
                continue;
            }
            if (s.thunderTime() <= 0 || s.rainTime() <= 0) {
                s = step(s, rng);
                left--;
                continue;
            }
            final int jump = Math.min(left, Math.min(s.thunderTime(), s.rainTime()));
            int j = s.thunderTime() - jump;
            int k = s.rainTime() - jump;
            boolean thundering = s.thundering();
            boolean raining = s.raining();
            if (j == 0) {
                thundering = !thundering;
            }
            if (k == 0) {
                raining = !raining;
            }
            s = new SimState(0, j, k, thundering, raining);
            left -= jump;
        }
        return s;
    }

    public static boolean isWet(SimState s) {
        return s.raining();
    }

    public static boolean isStorm(SimState s) {
        return s.raining() && s.thundering();
    }
}
