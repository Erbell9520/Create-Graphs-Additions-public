package com.createstats.dashboard;

import com.createstats.data.MetricType;
import com.createstats.data.TimeSeriesStore;
import com.createstats.blockentity.StatsDisplayPanelBE;

public final class ChannelPriorityScorer
{
    private ChannelPriorityScorer() {
    }
    
    public static float computePriority(StatsDisplayPanelBE.DataChannel channel) {
        float score = 0.0f;
        final TimeSeriesStore store = channel.getStore();
        if (channel.isAlertTriggered()) {
            score += 1000.0f;
        }
        if (channel.isAlertEnabled()) {
            score += 100.0f;
        }
        score += Math.min(200.0f, store.getSampleCount() / 10.0f);
        score += getMetricTypePriority(store.getMetricType());
        final float roc = Math.abs(store.getRateOfChange());
        score += Math.min(50.0f, roc * 5.0f);
        return score;
    }
    
    static float getMetricTypePriority(MetricType type) {
        return switch (type) {
            case STRESS_PERCENT -> 80.0f;
            case STRESS_SU -> 70.0f;
            case FILL_LEVEL -> 60.0f;
            case HEAT_LEVEL -> 55.0f;
            case RPM -> 50.0f;
            case WATER_SUPPLY -> 50.0f;
            case ENERGY_FE_PER_TICK -> 50.0f;
            case ENERGY_FE -> 42.0f;
            case ITEMS_PER_MIN -> 45.0f;
            case ITEMS_PER_SEC -> 45.0f;
            case ITEMS_PER_HR -> 40.0f;
            case FLUID_MB -> 35.0f;
            case ITEM_COUNT -> 25.0f;
            case ENGINE_COUNT -> 20.0f;
            case REDSTONE_POWER -> 15.0f;
            case BOOLEAN_FLAG -> 10.0f;
            default -> 30.0f;
        };
    }
}
