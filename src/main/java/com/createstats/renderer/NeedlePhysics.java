package com.createstats.renderer;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class NeedlePhysics
{
    public static final float SPRING_K = 120.0f;
    public static final float DAMPING_C = 4.5f;
    public static final float DT = 0.05f;
    public static final float MAX_ALPHA = 200.0f;
    public static final float BOUNCE_RESTITUTION = 0.3f;
    public static final float SWEEP_RADIANS = 4.712389f;
    public static final float THETA_MIN = 0.0f;
    public static final float THETA_MAX = 4.712389f;
    private float theta;
    private float thetaPrev;
    private float omega;
    private float thetaTarget;
    
    public NeedlePhysics() {
        this.theta = 0.0f;
        this.thetaPrev = 0.0f;
        this.omega = 0.0f;
        this.thetaTarget = 0.0f;
    }
    
    public void setTarget(float target) {
        this.thetaTarget = Math.max(0.0f, Math.min(4.712389f, target));
    }
    
    public void tick() {
        this.thetaPrev = this.theta;
        final float displacement = this.theta - this.thetaTarget;
        final float springForce = -120.0f * displacement;
        final float effectiveC = Math.max(4.5f, 0.5f);
        final float dampingForce = -effectiveC * this.omega;
        final float totalForce = springForce + dampingForce;
        final float alpha = Math.max(-200.0f, Math.min(200.0f, totalForce));
        this.omega += alpha * 0.05f;
        this.theta += this.omega * 0.05f;
        if (Float.isNaN(this.theta) || Float.isInfinite(this.theta)) {
            this.theta = this.thetaTarget;
            this.omega = 0.0f;
            return;
        }
        if (this.theta < 0.0f) {
            this.theta = 0.0f;
            this.omega = -this.omega * 0.3f;
        }
        if (this.theta > 4.712389f) {
            this.theta = 4.712389f;
            this.omega = -this.omega * 0.3f;
        }
    }
    
    public float getCurrentAngle() {
        return this.theta;
    }
    
    public float getPreviousAngle() {
        return this.thetaPrev;
    }
    
    public void reset(float angle) {
        this.theta = Math.max(0.0f, Math.min(4.712389f, angle));
        this.thetaPrev = this.theta;
        this.omega = 0.0f;
    }
}
