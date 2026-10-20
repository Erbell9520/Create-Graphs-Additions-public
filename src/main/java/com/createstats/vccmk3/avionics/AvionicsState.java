package com.createstats.vccmk3.avionics;

public record AvionicsState(

        AvionicsChannel altitude,

        AvionicsChannel calibratedHeight,

        // pressure scales airflow, it's why thrust fades as you climb
        AvionicsChannel airPressure,

        AvionicsChannel pitchDeg,

        AvionicsChannel rollDeg,

        AvionicsChannel headingDeg,

        AvionicsChannel groundSpeed,

        AvionicsChannel airspeed,

        AvionicsChannel climbRate,

        AvionicsChannel windSpeed,

        AvionicsChannel windFromDeg,

        AvionicsChannel terrainClearance
) {
    public static final AvionicsState EMPTY = new AvionicsState(
            AvionicsChannel.NONE, AvionicsChannel.NONE, AvionicsChannel.NONE,
            AvionicsChannel.NONE, AvionicsChannel.NONE, AvionicsChannel.NONE,
            AvionicsChannel.NONE, AvionicsChannel.NONE, AvionicsChannel.NONE,
            AvionicsChannel.NONE, AvionicsChannel.NONE, AvionicsChannel.NONE);

    public AvionicsState {
        if (altitude == null) altitude = AvionicsChannel.NONE;
        if (calibratedHeight == null) calibratedHeight = AvionicsChannel.NONE;
        if (airPressure == null) airPressure = AvionicsChannel.NONE;
        if (pitchDeg == null) pitchDeg = AvionicsChannel.NONE;
        if (rollDeg == null) rollDeg = AvionicsChannel.NONE;
        if (headingDeg == null) headingDeg = AvionicsChannel.NONE;
        if (groundSpeed == null) groundSpeed = AvionicsChannel.NONE;
        if (airspeed == null) airspeed = AvionicsChannel.NONE;
        if (climbRate == null) climbRate = AvionicsChannel.NONE;
        if (windSpeed == null) windSpeed = AvionicsChannel.NONE;
        if (windFromDeg == null) windFromDeg = AvionicsChannel.NONE;
        if (terrainClearance == null) terrainClearance = AvionicsChannel.NONE;
    }

    public boolean hasAnySensor() {
        return altitude.source() == SensorSource.SENSOR
                || calibratedHeight.source() == SensorSource.SENSOR
                || airPressure.source() == SensorSource.SENSOR
                || pitchDeg.source() == SensorSource.SENSOR
                || rollDeg.source() == SensorSource.SENSOR
                || groundSpeed.source() == SensorSource.SENSOR
                || terrainClearance.source() == SensorSource.SENSOR;
    }

    public boolean isBlank() {
        return !altitude.present() && !pitchDeg.present() && !rollDeg.present()
                && !headingDeg.present() && !groundSpeed.present() && !climbRate.present();
    }

    public AvionicsChannel headwind() {
        if (!windSpeed.present() || !windFromDeg.present() || !headingDeg.present()) return AvionicsChannel.NONE;
        double relative = Math.toRadians(windFromDeg.value() - headingDeg.value());
        double component = windSpeed.value() * Math.cos(relative);
        SensorSource source = windSpeed.source() == SensorSource.SENSOR && headingDeg.source() == SensorSource.SENSOR
                ? SensorSource.SENSOR
                : SensorSource.DERIVED;
        return new AvionicsChannel(component, source);
    }

    public AvionicsChannel driftAngleDeg() {
        if (!airspeed.present() || !groundSpeed.present() || airspeed.value() < 1.0e-3) return AvionicsChannel.NONE;
        double ratio = Math.min(1.0, Math.abs(groundSpeed.value() - airspeed.value()) / airspeed.value());
        return AvionicsChannel.derived(Math.toDegrees(Math.asin(Math.min(1.0, ratio))));
    }
}
