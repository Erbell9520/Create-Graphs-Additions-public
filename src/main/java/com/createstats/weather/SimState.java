package com.createstats.weather;

// swapped rain/thunder timers would look plausible. a record
public record SimState(int clearWeatherTime, int thunderTime, int rainTime,
                       boolean thundering, boolean raining) {
}
