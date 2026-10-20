package com.createstats.inventory.core;

public final class Eased {
    private float from;
    private float to;
    private double start;
    private float value;
    private boolean set;

    public float at(float target, double now, float ticks) {
        if (!this.set || Float.isNaN(this.value)) {
            this.from = target;
            this.to = target;
            this.value = target;
            this.start = now;
            this.set = true;
            return target;
        }
        if (target != this.to) {
            this.from = this.value;
            this.to = target;
            this.start = now;
        }
        final double t = ticks <= 0.0f ? 1.0 : Math.max(0.0, Math.min(1.0, (now - this.start) / ticks));
        this.value = (float) (this.from + (this.to - this.from) * (t * t * (3.0 - 2.0 * t)));
        return this.value;
    }

    public float value() {
        return this.value;
    }

    public boolean isSet() {
        return this.set;
    }
}
