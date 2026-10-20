package com.createstats.statsmk3.ui;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class Spring {
    private static final float FIXED_STEP = 1.0f / 240.0f;
    private static final float MAX_DELTA = 0.05f;

    private float value;
    private float target;
    private float velocity;
    private final float stiffness;
    private final float damping;
    private final float mass;

    public Spring(float initial, float stiffness, float damping, float mass) {
        this.value = initial;
        this.target = initial;
        this.stiffness = stiffness;
        this.damping = damping;
        this.mass = mass <= 0.0f ? 1.0f : mass;
    }

    public static Spring ui(float initial) {
        return new Spring(initial, Motion.UI_STIFFNESS, Motion.UI_DAMPING, 1.0f);
    }

    public static Spring needle(float initial) {
        return new Spring(initial, Motion.NEEDLE_STIFFNESS, Motion.NEEDLE_DAMPING, 1.0f);
    }

    public float value() {
        return this.value;
    }

    public float target() {
        return this.target;
    }

    public float velocity() {
        return this.velocity;
    }

    public Spring set(float target) {
        this.target = target;
        return this;
    }

    public Spring jump(float v) {
        this.value = v;
        this.target = v;
        this.velocity = 0.0f;
        return this;
    }

    public boolean settled(float epsilon) {
        return Math.abs(this.value - this.target) < epsilon && Math.abs(this.velocity) < epsilon;
    }

    public float step(float dt) {
        if (Motion.reducedMotion()) {
            this.value = this.target;
            this.velocity = 0.0f;
            return this.value;
        }
        float remaining = Math.min(dt, MAX_DELTA);
        while (remaining > 0.0f) {
            final float h = Math.min(remaining, FIXED_STEP);
            remaining -= h;
            final float force = -this.stiffness * (this.value - this.target) - this.damping * this.velocity;
            this.velocity += (force / this.mass) * h;
            this.value += this.velocity * h;
        }
        return this.value;
    }
}
