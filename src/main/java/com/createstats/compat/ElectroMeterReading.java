package com.createstats.compat;

public final class ElectroMeterReading {
    public static final double SHUNT_OHMS = 0.01;

    private ElectroMeterReading() {
    }

    public static double volts(double voltage, double scale) {
        return Math.abs(voltage * scale);
    }

    public static double amps(double shuntVoltage, double scale) {
        return Math.abs(shuntVoltage / SHUNT_OHMS) * Math.abs(scale);
    }

    public static double reading(boolean voltmeter, double voltage, double scale) {
        return voltmeter ? volts(voltage, scale) : amps(voltage, scale);
    }
}
