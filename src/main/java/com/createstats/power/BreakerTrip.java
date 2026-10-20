package com.createstats.power;

public final class BreakerTrip {
    public static final float MAGNETIC_MULTIPLE = 8.0f;

    public static float riseAt(float overload) {
        if (overload <= 1.0f) {
            return 0.0f;
        }
        return Math.min(64.0f, (overload * overload - 1.0f) / 3.0f);
    }

    public static int ticksToTrip(float overload, int tripTicks, float heatUnits) {
        if (tripTicks <= 0) {
            return -1;
        }
        if (overload >= MAGNETIC_MULTIPLE) {
            return 0;
        }
        final float rise = riseAt(overload);
        if (rise <= 0.0f) {
            return -1;
        }
        return (int) Math.ceil(Math.max(0.0f, tripTicks - heatUnits) / rise);
    }

    private float heatUnits;

    public boolean note(float overload, int tripTicks) {
        if (tripTicks <= 0) {
            heatUnits = 0.0f;
            return false;
        }
        if (overload >= MAGNETIC_MULTIPLE) {
            heatUnits = 0.0f;
            return true;
        }
        final float rise = riseAt(overload);
        if (rise <= 0.0f) {
            heatUnits = Math.max(0.0f, heatUnits - 1.0f);
            return false;
        }
        heatUnits += rise;
        if (heatUnits < tripTicks) {
            return false;
        }

        heatUnits = 0.0f;
        return true;
    }

    public void reset() {
        heatUnits = 0.0f;
    }

    public float heatUnits() {
        return heatUnits;
    }

    public void setHeatUnits(float units) {
        heatUnits = Float.isNaN(units) ? 0.0f : Math.max(0.0f, units);
    }

    public float heat(int tripTicks) {
        if (tripTicks <= 0 || heatUnits <= 0.0f) {
            return 0.0f;
        }
        return Math.min(1.0f, heatUnits / tripTicks);
    }
}
