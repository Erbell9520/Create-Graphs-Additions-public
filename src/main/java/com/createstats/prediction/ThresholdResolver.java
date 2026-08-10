package com.createstats.prediction;

import com.createstats.data.MetricType;

public final class ThresholdResolver
{
    private ThresholdResolver() {
    }
    
    public static Threshold resolve(MetricType metricType, boolean slopePositive, double alertThreshold) {
        if (!Double.isNaN(alertThreshold)) {
            return new Threshold(alertThreshold, "Target reached");
        }
        return switch (metricType) {
            case STRESS_PERCENT -> slopePositive ? new Threshold(100.0, "Overstress hit") : new Threshold(0.0, "Load fades out");
            case FILL_LEVEL -> slopePositive ? new Threshold(100.0, "Storage full") : new Threshold(0.0, "Storage empty");
            case FLUID_MB -> slopePositive ? null : new Threshold(0.0, "Tank runs dry");
            case ENERGY_FE -> slopePositive ? null : new Threshold(0.0, "Energy empties");
            case ENERGY_FE_PER_TICK -> slopePositive ? null : new Threshold(0.0, "Energy flow stops");
            case ITEMS_PER_MIN,  ITEMS_PER_SEC,  ITEMS_PER_HR -> slopePositive ? null : new Threshold(0.0, "Output dries up");
            case RPM -> slopePositive ? new Threshold(256.0, "Speed cap hit") : new Threshold(0.0, "Rotation stops");
            case HEAT_LEVEL -> slopePositive ? new Threshold(18.0, "Heat maxed") : new Threshold(0.0, "Heat dies out");
            case STRESS_SU -> slopePositive ? null : new Threshold(0.0, "Load fades out");
            case REDSTONE_POWER -> slopePositive ? new Threshold(15.0, "Signal maxed") : new Threshold(0.0, "Signal off");
            case BOOLEAN_FLAG -> null;
            default -> null;
        };
    }
    
    record Threshold(double value, String label) {}
}
