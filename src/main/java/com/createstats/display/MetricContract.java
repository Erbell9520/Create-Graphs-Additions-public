package com.createstats.display;

import com.createstats.data.MetricType;
import com.createstats.data.TimeSeriesStore;

/** [1] */
public final class MetricContract {

    private MetricContract() {}

    /**
     * Fixed canonical {@code [min, max]} for metrics that have a well-defined range, or {@code null}
     * for metrics that should auto-range from observed data.
     */
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
                return new float[]{0.0f, 256.0f}; // Create's default rotation speed cap
            case BOOLEAN_FLAG:
                return new float[]{0.0f, 1.0f};
            case HEAT_LEVEL:
                return new float[]{0.0f, 18.0f}; // Create boiler heat levels
            case ANGLE_DEGREES:
                return new float[]{0.0f, 360.0f};
            default:
                return null;
        }
    }

    /**
     * A sane default alarm to apply the FIRST time an alarm is armed on a channel, instead of the old
     * "above 0" default (which made any positive value, e.g. full RPM, trip immediately and could
     * not be quieted). Percent-style metrics arm at their danger band; booleans alarm when on;
     * everything else arms just above the recent peak so enabling an alarm never instantly fires on
     * normal positive data.
     *
     * @return {@code [threshold, above ? 1f : 0f]}
     */
    public static float[] defaultAlarm(MetricType type, TimeSeriesStore store) {
        if (type != null) {
            switch (type) {
                case STRESS_PERCENT:
                case FILL_LEVEL:
                case EFFICIENCY_SCORE:
                    return new float[]{90.0f, 1.0f}; // alarm above 90%
                case BOOLEAN_FLAG:
                    return new float[]{0.5f, 1.0f};  // alarm when on
                default:
                    break;
            }
        }
        final float peak = store != null ? store.getMax() : 0.0f;
        final float threshold = peak > 0.001f ? peak * 1.05f : 1.0f;
        return new float[]{threshold, 1.0f}; // arm just above the recent peak
    }

    /** [2] */
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
