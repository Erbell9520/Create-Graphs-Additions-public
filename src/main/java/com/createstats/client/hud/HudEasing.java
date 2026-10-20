package com.createstats.client.hud;

import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class HudEasing {
    private HudEasing() {
    }

    public static float lerp(float t, float a, float b) {
        return a + (b - a) * Mth.clamp(t, 0.0f, 1.0f);
    }

    public static float easeOutCubic(float t) {
        final float inv = 1.0f - Mth.clamp(t, 0.0f, 1.0f);
        return 1.0f - inv * inv * inv;
    }

    public static float easeInOutCubic(float t) {
        final float clamped = Mth.clamp(t, 0.0f, 1.0f);
        if (clamped < 0.5f) {
            return 4.0f * clamped * clamped * clamped;
        }
        final float f = 2.0f * clamped - 2.0f;
        return 1.0f + 0.5f * f * f * f;
    }

    public static float expSmooth(float current, float target, float tauSec, float dtSec) {
        if (!Float.isFinite(target)) return current;
        if (!Float.isFinite(current)) return target;
        if (tauSec <= 0.0f) return target;
        final float alpha = 1.0f - (float) Math.exp(-Math.max(0.0f, dtSec) / tauSec);
        return current + (target - current) * Mth.clamp(alpha, 0.0f, 1.0f);
    }

    public static float approach(float current, float target, float maxStep) {
        if (!Float.isFinite(target)) return current;
        if (!Float.isFinite(current)) return target;
        final float diff = target - current;
        if (Math.abs(diff) <= maxStep) return target;
        return current + Math.signum(diff) * maxStep;
    }

    public static int blendArgb(int colorA, int colorB, float t) {
        final float k = Mth.clamp(t, 0.0f, 1.0f);
        final int aA = (colorA >>> 24) & 0xFF;
        final int rA = (colorA >>> 16) & 0xFF;
        final int gA = (colorA >>> 8) & 0xFF;
        final int bA = colorA & 0xFF;
        final int aB = (colorB >>> 24) & 0xFF;
        final int rB = (colorB >>> 16) & 0xFF;
        final int gB = (colorB >>> 8) & 0xFF;
        final int bB = colorB & 0xFF;
        final int a = Math.round(aA + (aB - aA) * k);
        final int r = Math.round(rA + (rB - rA) * k);
        final int g = Math.round(gA + (gB - gA) * k);
        final int b = Math.round(bA + (bB - bA) * k);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
