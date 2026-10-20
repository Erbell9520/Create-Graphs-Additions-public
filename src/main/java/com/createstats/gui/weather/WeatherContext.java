package com.createstats.gui.weather;

import com.createstats.blockentity.WeatherPanelBE;
import com.createstats.renderer.WeatherFaceTextureManager;
import com.createstats.weather.ClimateModel;
import com.createstats.weather.Forecast;
import net.minecraft.client.gui.Font;

public record WeatherContext(WeatherPanelBE panel, WeatherAnim anim, WeatherTheme.Palette palette,
                             float nightness, float partialTick, float elapsedSinceSync,
                             Font font, boolean fahrenheit, boolean stale) {
    public Forecast forecast() {
        return panel.forecast();
    }

    public float temperature() {
        return anim.temperature();
    }

    public float toDisplayUnits(float celsius) {
        return fahrenheit ? ClimateModel.toFahrenheit(celsius) : celsius;
    }

    public String unit() {
        return fahrenheit ? "F" : "C";
    }

    public int ticksToNextChange() {
        final int synced = panel.ticksToNextChange();
        return synced < 0 ? -1 : Math.max(0, synced - Math.round(elapsedSinceSync));
    }

    public static boolean fahrenheitPreference() {
        return WeatherFaceTextureManager.fahrenheit();
    }
}
