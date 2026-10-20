package com.createstats.vccmk4.air.core;

public record PressureModel(double y0, double p0, double lapse) {
    public static final double SABLE_LAPSE = 0.004;

    public static final double MAX_PRESSURE = 1.5;

    public static final double PROBE = 16.0;

    public static PressureModel sable(double seaLevel) {
        return new PressureModel(seaLevel, 1.0, SABLE_LAPSE);
    }

    public static PressureModel measured(double y, double pressure, double pressureAbove, double probe, double seaLevel) {
        if (!(pressure > 1.0e-6) || !Double.isFinite(pressure) || !Double.isFinite(y)) return sable(seaLevel);
        double lapse = SABLE_LAPSE;
        if (pressureAbove > 1.0e-6 && Double.isFinite(pressureAbove) && probe > 0.0) {
            double l = Math.log(pressure / pressureAbove) / probe;
            if (Double.isFinite(l) && l > 1.0e-5 && l < 0.1) lapse = l;
        }
        return new PressureModel(y, pressure, lapse);
    }

    public double at(double y) {
        return Math.min(MAX_PRESSURE, p0 * Math.exp(-lapse * (y - y0)));
    }

    public double altitudeOf(double p) {
        if (!(p > 0.0) || p > MAX_PRESSURE || !(lapse > 0.0) || !(p0 > 0.0)) return Double.NaN;
        return y0 + Math.log(p0 / p) / lapse;
    }
}
