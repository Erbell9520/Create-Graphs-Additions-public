package com.createstats.display;

import com.createstats.data.MetricType;
import com.createstats.data.TimeSeriesStore;

// one contract per metric so the scale never flips with the config
public final class MetricContract {
    private MetricContract() {}

    public static float[] fixedRange(MetricType type) {
        if (type == null) {
            return null;
        }
        switch (type) {
            case STRESS_PERCENT:
            case FILL_LEVEL:
            case EFFICIENCY_SCORE:
                return new float[]{0.0f, 100.0f};
            case REDSTONE_POWER:
                return new float[]{0.0f, 15.0f};
            case RPM:
                return new float[]{0.0f, 256.0f};
            case BOOLEAN_FLAG:
                return new float[]{0.0f, 1.0f};
            case HEAT_LEVEL:
                return new float[]{0.0f, 18.0f};
            case ANGLE_DEGREES:
                return new float[]{0.0f, 360.0f};
            default:
                return null;
        }
    }

    public static float[] defaultAlarm(MetricType type, TimeSeriesStore store) {
        if (type != null) {
            switch (type) {
                case STRESS_PERCENT:
                case FILL_LEVEL:
                case EFFICIENCY_SCORE:
                    return new float[]{90.0f, 1.0f};
                case BOOLEAN_FLAG:
                    return new float[]{0.5f, 1.0f};
                default:
                    break;
            }
        }
        final float peak = store != null ? store.getMax() : 0.0f;
        final float threshold = peak > 0.001f ? peak * 1.05f : 1.0f;
        return new float[]{threshold, 1.0f};
    }

    public static float[] bands(MetricType type) {
        if (type == null) {
            return null;
        }
        switch (type) {
            case STRESS_PERCENT:
            case FILL_LEVEL:
            case EFFICIENCY_SCORE:
                return new float[]{0.70f, 0.90f};
            case RPM:
                return new float[]{0.83f, 0.94f};
            default:
                return null;
        }
    }
}
