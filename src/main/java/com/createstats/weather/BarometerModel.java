package com.createstats.weather;

// derived from the same forecast so the needle can't disagree with the strip
public final class BarometerModel {
    public static final float MIN = 950.0f;
    public static final float MAX = 1050.0f;

    private static final float BASE = 1013.0f;
    private static final float WET_DROP = 22.0f;
    private static final float APPROACH_DROP = 20.0f;
    private static final float CLEARING_RISE = 10.0f;
    private static final float PER_BLOCK = 0.11f;
    private static final int SEA_LEVEL = 64;

    private static final int LOOKAHEAD_TICKS = 8000;

    private BarometerModel() {
    }

    public static float pressure(WeatherSnapshot s, Forecast f) {
        if (!s.hasSkyLight()) {
            return Float.NaN;
        }
        final float wet = ClimateModel.wetnessOf(s.sim());
        float p = BASE - WET_DROP * wet;
        p -= APPROACH_DROP * closeness(ticksToWorsening(s, f));
        p += CLEARING_RISE * closeness(ticksToClearing(s, f));
        p -= (s.y() - SEA_LEVEL) * PER_BLOCK;
        return Math.max(MIN, Math.min(MAX, p));
    }

    private static float closeness(int ticks) {
        if (ticks < 0) {
            return 0.0f;
        }
        final float f = 1.0f - ticks / (float) LOOKAHEAD_TICKS;
        return f < 0f ? 0f : (f > 1f ? 1f : f);
    }

    private static int ticksToWorsening(WeatherSnapshot s, Forecast f) {
        final int nowRank = rank(s.condition());
        for (Forecast.Band b : f.bands()) {
            if (b.startTick() > 0 && rank(b.condition()) > nowRank) {
                return b.startTick();
            }
        }
        if (!s.sim().raining()) {
            return s.sim().rainTime();
        }
        return -1;
    }

    private static int ticksToClearing(WeatherSnapshot s, Forecast f) {
        final int nowRank = rank(s.condition());
        for (Forecast.Band b : f.bands()) {
            if (b.startTick() > 0 && rank(b.condition()) < nowRank) {
                return b.startTick();
            }
        }
        if (s.sim().raining()) {
            return s.sim().rainTime();
        }
        return -1;
    }

    private static int rank(Condition c) {
        return switch (c) {
            case STORM -> 3;
            case RAIN, SNOW -> 2;
            case DRY -> 1;
            default -> 0;
        };
    }
}
