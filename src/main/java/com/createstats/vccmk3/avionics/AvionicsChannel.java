package com.createstats.vccmk3.avionics;

public record AvionicsChannel(double value, SensorSource source) {
    public static final AvionicsChannel NONE = new AvionicsChannel(Double.NaN, SensorSource.NONE);

    public AvionicsChannel {
        if (source == null) source = SensorSource.NONE;
        if (source == SensorSource.NONE) value = Double.NaN;
    }

    public static AvionicsChannel sensor(double value) {
        return Double.isFinite(value) ? new AvionicsChannel(value, SensorSource.SENSOR) : NONE;
    }

    public static AvionicsChannel derived(double value) {
        return Double.isFinite(value) ? new AvionicsChannel(value, SensorSource.DERIVED) : NONE;
    }

    public static AvionicsChannel resolve(double sensorValue, double derivedValue) {
        AvionicsChannel measured = sensor(sensorValue);
        return measured.present() ? measured : derived(derivedValue);
    }

    public boolean present() {
        return source.hasReading() && Double.isFinite(value);
    }

    public double orElse(double fallback) {
        return present() ? value : fallback;
    }

    public String format(int decimals) {
        if (!present()) return "---";
        return String.format(java.util.Locale.ROOT, "%." + Math.max(0, decimals) + "f", value);
    }

    public String format(int decimals, String unit) {
        return present() ? format(decimals) + unit : "---";
    }
}
