package com.createstats.weather;

public record WeatherSnapshot(SimState sim, long worldSeed, long dayTime, long gameTime,
                              int dayIndex, float biomeTemperature, float downfall,
                              Condition localPrecipitation, int y, boolean hasSkyLight,
                              boolean weatherCycle) {
    public float dayFraction() {
        return ((dayTime + 6000L) % 24000L) / 24000.0f;
    }

    public Condition condition() {
        if (!hasSkyLight) {
            return Condition.NONE;
        }
        if (!sim.raining()) {
            return Condition.CLEAR;
        }
        return switch (localPrecipitation) {
            case SNOW -> Condition.SNOW;
            case DRY, NONE -> Condition.DRY;
            default -> sim.thundering() ? Condition.STORM : Condition.RAIN;
        };
    }
}
