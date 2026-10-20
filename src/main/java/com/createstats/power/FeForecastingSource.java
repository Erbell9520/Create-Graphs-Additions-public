package com.createstats.power;

public interface FeForecastingSource {
    int potentialFePerTickIn(int ticks);

    default boolean weatherSensitive() {
        return false;
    }

    default boolean burnsFuel() {
        return false;
    }

    default int relayedWeatherShareFePerTick() {
        return 0;
    }

    default boolean relays() {
        return false;
    }
}
