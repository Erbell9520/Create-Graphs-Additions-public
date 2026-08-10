package com.createstats.data;

public enum MetricType
{
    RPM("RPM", "rpm", "Rotations per minute"), 
    STRESS_SU("Stress (SU)", "su", "Stress units used"), 
    STRESS_PERCENT("Stress %", "%", "Percentage of network capacity used"), 
    ITEMS_PER_MIN("Items/min", "items/min", "Item throughput per minute"), 
    ITEMS_PER_SEC("Items/sec", "items/s", "Item throughput per second"), 
    ITEMS_PER_HR("Items/hr", "items/hr", "Item throughput per hour"), 
    ITEM_COUNT("Items", "", "Item count"), 
    FILL_LEVEL("Fill Level", "%", "Container fill percentage"), 
    FLUID_MB("Fluid (mB)", "mB", "Fluid amount in millibuckets"), 
    FLUID_MB_PER_TICK("Flow Rate", "mB/t", "Fluid throughput per tick"), 
    ENERGY_FE("Energy (FE)", "FE", "Forge Energy stored or transferred"),
    ENERGY_FE_PER_TICK("Energy Rate", "FE/t", "Forge Energy transferred per tick"),
    HEAT_LEVEL("Heat Level", "", "Boiler heat level"), 
    ENGINE_COUNT("Engines", "", "Attached engine count"), 
    WATER_SUPPLY("Water Supply", "mB/t", "Water supply rate"), 
    REDSTONE_POWER("Redstone", "", "Redstone signal strength"), 
    BURN_TIME("Burn Time", "t", "Fuel burn time in ticks"),
    VELOCITY("Velocity", "", "Adjusted signed velocity"),
    ANGLE_DEGREES("Angle", "°", "Angle in degrees"),
    BOOLEAN_FLAG("State", "", "On/Off state"), 
    EFFICIENCY_SCORE("Efficiency", "/100", "Network efficiency score (0-100)"), 
    ENDURANCE_TICKS("Endurance", "t", "Ticks of runtime left at the current net drain"),
    GENERIC("Value", "", "Generic numeric value");
    
    private final String displayName;
    private final String unit;
    private final String description;
    
    private MetricType(String displayName, String unit, String description) {
        this.displayName = displayName;
        this.unit = unit;
        this.description = description;
    }
    
    public String getDisplayName() {
        return this.displayName;
    }
    
    public String getUnit() {
        return this.unit;
    }
    
    public String getDescription() {
        return this.description;
    }
    
    public String formatValue(float value) {
        if (this == MetricType.STRESS_PERCENT || this == MetricType.FILL_LEVEL) {
            return String.format("%.1f%s", value, this.unit);
        }
        if (this == MetricType.RPM || this == MetricType.ITEMS_PER_MIN || this == MetricType.ITEMS_PER_SEC || this == MetricType.ITEMS_PER_HR) {
            return String.format("%.0f %s", value, this.unit);
        }
        if (this == MetricType.ITEM_COUNT || this == MetricType.ENGINE_COUNT || this == MetricType.REDSTONE_POWER) {
            return String.format("%.0f", value);
        }
        if (this == MetricType.BURN_TIME) {
            return String.format("%.0f %s", value, this.unit);
        }
        if (this == MetricType.ENERGY_FE) {
            return formatEnergy(value, false);
        }
        if (this == MetricType.ENERGY_FE_PER_TICK) {
            return formatEnergy(value, true);
        }
        if (this == MetricType.FLUID_MB || this == MetricType.WATER_SUPPLY || this == MetricType.FLUID_MB_PER_TICK) {
            if (Math.abs(value) >= 1000.0f) {
                return String.format("%.1fB", value / 1000.0f);
            }
            return String.format("%.0f %s", value, this.unit);
        }
        if (this == MetricType.VELOCITY) {
            return (value > 0.0f ? "+" : "") + String.format("%.2f", value);
        }
        if (this == MetricType.ANGLE_DEGREES) {
            return String.format("%.1f%s", value, this.unit);
        }
        else {
            if (this == MetricType.HEAT_LEVEL) {
                return String.format("Lvl %.0f", value);
            }
            if (this == MetricType.ENGINE_COUNT) {
                return String.format("%.0f", value);
            }
            if (this == MetricType.BOOLEAN_FLAG) {
                return (value > 0.5f) ? "ON" : "OFF";
            }
            if (this == MetricType.EFFICIENCY_SCORE) {
                return String.format("%.1f", value);
            }
            return String.format("%.1f %s", value, this.unit).trim();
        }
    }

    private static String formatEnergy(float value, boolean rate) {
        final float abs = Math.abs(value);
        final String sign = rate && value > 0.0f ? "+" : (value < 0.0f ? "-" : "");
        final float display = abs >= 1_000_000.0f ? abs / 1_000_000.0f : (abs >= 1_000.0f ? abs / 1_000.0f : abs);
        final String suffix = abs >= 1_000_000.0f ? "M" : (abs >= 1_000.0f ? "k" : "");
        final String number = abs >= 1_000.0f ? String.format("%.1f", display) : String.format("%.0f", display);
        return sign + number + suffix + (rate ? " FE/t" : " FE");
    }
}
