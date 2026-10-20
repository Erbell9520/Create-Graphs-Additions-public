package com.createstats.alarm.annunciator;

// blink from time, not stored state. stored blink = blockstate write = remesh, several times a second. the tower light did that once
public enum LampPattern {
    OFF(0),

    FAST_FLASH(6),

    STEADY(0),

    SLOW_FLASH(20);

    private final int halfPeriodTicks;

    LampPattern(int halfPeriodTicks) {
        this.halfPeriodTicks = halfPeriodTicks;
    }

    public boolean isFlashing() {
        return this.halfPeriodTicks > 0;
    }

    public boolean isLit(long gameTime) {
        if (this == OFF) {
            return false;
        }
        if (this == STEADY) {
            return true;
        }
        return Math.floorMod(gameTime, this.halfPeriodTicks * 2L) < this.halfPeriodTicks;
    }

    public float intensity(long gameTime, float partialTicks) {
        if (this == OFF) {
            return 0.0f;
        }
        if (this == STEADY) {
            return 1.0f;
        }
        final float period = this.halfPeriodTicks * 2.0f;
        final float phase = (Math.floorMod(gameTime, (long) period) + partialTicks) / period;

        return (float) (0.5 - 0.5 * Math.cos(phase * 2.0 * Math.PI));
    }
}
