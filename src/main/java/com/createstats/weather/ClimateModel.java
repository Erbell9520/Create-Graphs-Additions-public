package com.createstats.weather;

public final class ClimateModel {
    private static final float SCALE = 19.0f;

    private static final float SNOW_LINE = 0.15f;

    private static final float LAPSE_PER_BLOCK = 0.00125f;
    private static final int LAPSE_FROM_Y = 80;

    private static final float PHASE = 0.30f;
    private static final float WET_COOLING = 6.0f;

    private ClimateModel() {
    }

    public static float wetnessOf(SimState sim) {
        if (!sim.raining()) {
            return 0.0f;
        }
        return sim.thundering() ? 1.0f : 0.7f;
    }

    public static float amplitude(WeatherSnapshot s) {
        return 3.0f + 5.0f * (1.0f - clamp01(s.downfall()));
    }

    public static float temperatureAt(WeatherSnapshot s, float dayFraction, float wet) {
        final float adjusted = s.biomeTemperature()
                - (s.y() > LAPSE_FROM_Y ? (s.y() - LAPSE_FROM_Y) * LAPSE_PER_BLOCK : 0.0f);
        final float base = SCALE * (adjusted - SNOW_LINE);
        final float diurnal = (float) Math.sin(2.0 * Math.PI * (dayFraction - PHASE));
        return base + amplitude(s) * diurnal - WET_COOLING * wet;
    }

    public static float temperatureC(WeatherSnapshot s, float wet) {
        final float t = temperatureAt(s, s.dayFraction(), wet);
        final Condition condition = s.condition();
        if (condition == Condition.SNOW) {
            return Math.min(t, 0.0f);
        }
        if (condition == Condition.RAIN || condition == Condition.STORM) {
            return Math.max(t, 1.0f);
        }
        return t;
    }

    public static float humidity(WeatherSnapshot s, float wet) {
        final float diurnal = (float) Math.sin(2.0 * Math.PI * (s.dayFraction() - PHASE));
        final float h = 25.0f + 55.0f * clamp01(s.downfall())
                + 20.0f * wet
                + 10.0f * (s.sim().thundering() && s.sim().raining() ? 1.0f : 0.0f)
                - 12.0f * diurnal;
        return Math.max(5.0f, Math.min(100.0f, h));
    }

    public static float toFahrenheit(float celsius) {
        return celsius * 9.0f / 5.0f + 32.0f;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
