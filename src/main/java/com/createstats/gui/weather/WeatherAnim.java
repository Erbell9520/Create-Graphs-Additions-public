package com.createstats.gui.weather;

import com.createstats.gui.UiFx;
import com.createstats.weather.Condition;
import com.createstats.weather.Forecast;

// hides the ~1Hz sync
public final class WeatherAnim {
    private static final float SPEED_TEMPERATURE = 0.25f;
    private static final float SPEED_PRESSURE = 0.35f;
    private static final float SPEED_HUMIDITY = 0.25f;

    private static final float SPEED_CLOUD = 0.06f;
    private static final float SPEED_RAIN = 0.15f;

    private static final float SPEED_PALETTE = 0.12f;
    private static final float SPEED_GLYPH = 0.18f;
    private static final float SPEED_DAY_SHIFT = 0.16f;
    private static final float SPEED_POP = 0.18f;

    public static final float DIGIT_ROLL_TICKS = 3.0f;

    private boolean primed;

    private float temperature;
    private float targetTemperature;
    private float pressure;
    private float targetPressure;
    private float humidity;
    private float targetHumidity;
    private float cloudiness;
    private float targetCloudiness;
    private float rainLevel;
    private float targetRainLevel;

    private Condition from = Condition.CLEAR;
    private Condition to = Condition.CLEAR;
    private float paletteMix = 1.0f;
    private float glyphMix = 1.0f;

    private final float[] pop = new float[WeatherTheme.DAY_CELLS];
    private final float[] targetPop = new float[WeatherTheme.DAY_CELLS];

    private float dayShift;
    private int lastTodayIndex = Integer.MIN_VALUE;

    private float sinceDigitChange = DIGIT_ROLL_TICKS;

    private double lastUpdate = Double.NaN;
    private int shownWholeTemperature;
    private int previousWholeTemperature;

    public void sync(Condition condition, float temperature, float pressureHpa, float humidity,
                     float cloudiness, float rainLevel, Forecast forecast) {
        targetTemperature = temperature;
        targetPressure = Float.isNaN(pressureHpa) ? targetPressure : pressureHpa;
        targetHumidity = humidity;
        targetCloudiness = UiFx.clamp01(cloudiness);
        targetRainLevel = UiFx.clamp01(rainLevel);

        for (int i = 0; i < targetPop.length; i++) {
            targetPop[i] = i < forecast.days().size() ? forecast.days().get(i).popPercent() : 0f;
        }
        if (!forecast.days().isEmpty()) {
            noteTodayIndex(forecast.days().get(0).dayIndex());
        }
        if (condition != to) {
            from = to;
            to = condition;
            paletteMix = 0f;
            glyphMix = 0f;
        }
        if (!primed) {
            primed = true;
            this.temperature = targetTemperature;
            this.pressure = targetPressure;
            this.humidity = targetHumidity;
            this.cloudiness = targetCloudiness;
            this.rainLevel = targetRainLevel;
            System.arraycopy(targetPop, 0, pop, 0, pop.length);
            from = condition;
            to = condition;
            paletteMix = 1f;
            glyphMix = 1f;
            dayShift = 0f;
            shownWholeTemperature = Math.round(this.temperature);
            previousWholeTemperature = shownWholeTemperature;
        }
    }

    private void noteTodayIndex(int todayIndex) {
        if (lastTodayIndex != Integer.MIN_VALUE && todayIndex > lastTodayIndex) {
            dayShift = 1.0f;
        }
        lastTodayIndex = todayIndex;
    }

    public void updateTo(double nowTicks) {
        if (Double.isNaN(lastUpdate)) {
            lastUpdate = nowTicks;
            return;
        }
        final float dt = (float) (nowTicks - lastUpdate);
        if (dt <= 0f) {
            return;
        }
        lastUpdate = nowTicks;

        update(Math.min(4f, dt));
    }

    public void update(float dtTicks) {
        if (!primed) {
            return;
        }
        temperature = UiFx.approach(temperature, targetTemperature, dtTicks, SPEED_TEMPERATURE);
        pressure = UiFx.approach(pressure, targetPressure, dtTicks, SPEED_PRESSURE);
        humidity = UiFx.approach(humidity, targetHumidity, dtTicks, SPEED_HUMIDITY);
        cloudiness = UiFx.approach(cloudiness, targetCloudiness, dtTicks, SPEED_CLOUD);
        rainLevel = UiFx.approach(rainLevel, targetRainLevel, dtTicks, SPEED_RAIN);
        paletteMix = UiFx.approach(paletteMix, 1.0f, dtTicks, SPEED_PALETTE);
        glyphMix = UiFx.approach(glyphMix, 1.0f, dtTicks, SPEED_GLYPH);
        dayShift = UiFx.approach(dayShift, 0.0f, dtTicks, SPEED_DAY_SHIFT);
        for (int i = 0; i < pop.length; i++) {
            pop[i] = UiFx.approach(pop[i], targetPop[i], dtTicks, SPEED_POP);
        }

        final int whole = Math.round(temperature);
        if (whole != shownWholeTemperature) {
            previousWholeTemperature = shownWholeTemperature;
            shownWholeTemperature = whole;
            sinceDigitChange = 0f;
        } else {
            sinceDigitChange += dtTicks;
        }
    }

    public boolean isPrimed() {
        return primed;
    }

    public float temperature() {
        return temperature;
    }

    public float pressure() {
        return pressure;
    }

    public float humidity() {
        return humidity;
    }

    public float cloudiness() {
        return cloudiness;
    }

    public float rainLevel() {
        return rainLevel;
    }

    public float pop(int day) {
        return day >= 0 && day < pop.length ? pop[day] : 0f;
    }

    public float dayShift() {
        return dayShift;
    }

    public Condition fromCondition() {
        return from;
    }

    public Condition toCondition() {
        return to;
    }

    public float glyphMix() {
        return UiFx.easeInOut(glyphMix);
    }

    public WeatherTheme.Palette palette(float nightness) {
        final WeatherTheme.Palette a = WeatherTheme.paletteFor(from, nightness);
        final WeatherTheme.Palette b = WeatherTheme.paletteFor(to, nightness);
        return WeatherTheme.guard(WeatherTheme.crossfade(a, b, UiFx.easeInOut(paletteMix)));
    }

    public int wholeTemperature() {
        return shownWholeTemperature;
    }

    public int previousWholeTemperature() {
        return previousWholeTemperature;
    }

    public float digitRoll() {
        return UiFx.clamp01(sinceDigitChange / DIGIT_ROLL_TICKS);
    }
}
