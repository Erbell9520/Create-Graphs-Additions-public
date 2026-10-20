package com.createstats.gui.meter.plate;

public final class HoldGesture {
    public static final int TICKS = 20;

    private boolean holding;
    private int ticks;

    public void press() {
        holding = true;
        ticks = 0;
    }

    public void release() {
        holding = false;
        ticks = 0;
    }

    public boolean tick() {
        if (!holding) {
            return false;
        }
        if (++ticks >= TICKS) {
            holding = false;
            ticks = 0;
            return true;
        }
        return false;
    }

    public boolean holding() {
        return holding;
    }

    public float progress(float partialTick) {
        return holding ? Math.min(1.0f, (ticks + partialTick) / TICKS) : 0.0f;
    }
}
