package com.createstats.util;

import com.createstats.data.MetricType;
import com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlockEntity;
import net.minecraft.util.Mth;

public final class StockpileSwitchMetrics
{
    private StockpileSwitchMetrics() {
    }

    public static Readout read(ThresholdSwitchBlockEntity tsw) {
        final float min = Math.max(0, tsw.getMinLevel());
        final float current = Math.max(0, tsw.getStockLevel());
        final float capacity = Math.max(0, tsw.getMaxLevel());
        return new Readout(percentOfRange(current, min, capacity), current, min, capacity, tsw.offWhenBelow, tsw.onWhenAbove, detectMetricType(tsw));
    }

    private static MetricType detectMetricType(ThresholdSwitchBlockEntity tsw) {
        return switch (tsw.getTypeOfCurrentTarget()) {
            case ITEM -> MetricType.ITEM_COUNT;
            case FLUID -> MetricType.FLUID_MB;
            default -> MetricType.GENERIC;
        };
    }

    private static float percentOfRange(float value, float min, float max) {
        final float range = max - min;
        if (range <= 0.001f) {
            return 0.0f;
        }
        return Mth.clamp((value - min) * 100.0f / range, 0.0f, 100.0f);
    }

    public record Readout(float fillPercent, float currentAmount, float minAmount, float capacityAmount, float lowerThreshold, float upperThreshold, MetricType amountMetricType)
    {
        public float lowerThresholdPercent() {
            return StockpileSwitchMetrics.percentOfRange(this.lowerThreshold, this.minAmount, this.capacityAmount);
        }

        public float upperThresholdPercent() {
            return StockpileSwitchMetrics.percentOfRange(this.upperThreshold, this.minAmount, this.capacityAmount);
        }
    }
}
