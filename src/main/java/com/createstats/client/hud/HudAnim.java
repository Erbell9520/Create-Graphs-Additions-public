package com.createstats.client.hud;

import net.minecraft.util.Mth;

// wall clock so it keeps breathing while paused
public final class HudAnim {
    private HudAnim() {}

    public static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * clamp01(t);
    }

    public static float easeInOutCubic(float t) {
        t = clamp01(t);
        return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3f) / 2f;
    }

    public static float easeOutCubic(float t) {
        t = clamp01(t);
        final float p = 1f - t;
        return 1f - p * p * p;
    }

    public static float smoothstep(float t) {
        t = clamp01(t);
        return t * t * (3f - 2f * t);
    }

    public static float pulse(long timeMs, long periodMs) {
        if (periodMs <= 0L) return 0f;
        final double phase = (timeMs % periodMs) / (double) periodMs;
        return (float) (0.5 + 0.5 * Math.sin(phase * Math.PI * 2.0));
    }

    public static float triangle(long timeMs, long periodMs) {
        if (periodMs <= 0L) return 0f;
        final float p = (float) ((timeMs % periodMs) / (double) periodMs);
        return p < 0.5f ? p * 2f : 2f - p * 2f;
    }

    public static float spring(float current, float target, float[] velocity, float dt, float halfLife) {
        if (halfLife <= 0f) return target;
        final float k = (float) (Math.log(2.0) / halfLife);
        final float v = velocity != null && velocity.length > 0 ? velocity[0] : 0f;
        final float dx = current - target;
        final float exp = (float) Math.exp(-k * dt);
        final float newPos = target + (dx + (v + k * dx) * dt) * exp;
        if (velocity != null && velocity.length > 0) {
            velocity[0] = (v - k * (dx + (v + k * dx) * dt)) * exp;
        }
        return newPos;
    }

    public static float approach(float current, float target, float maxStep) {
        if (maxStep <= 0f) return current;
        if (current < target) return Math.min(target, current + maxStep);
        if (current > target) return Math.max(target, current - maxStep);
        return current;
    }

    public static int blendArgb(int a, int b, float t) {
        t = clamp01(t);
        final int aa = (int) (((a >>> 24) & 0xFF) * (1 - t) + ((b >>> 24) & 0xFF) * t);
        final int ar = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        final int ag = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        final int ab = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (aa << 24) | (ar << 16) | (ag << 8) | ab;
    }

    public static int withAlpha(int argb, int alpha) {
        alpha = Mth.clamp(alpha, 0, 255);
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    public static float mapClamped(float x, float a, float b, float c, float d) {
        if (Math.abs(b - a) < 1e-6f) return c;
        final float t = clamp01((x - a) / (b - a));
        return c + (d - c) * t;
    }
}
