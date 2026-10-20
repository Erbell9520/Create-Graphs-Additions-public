package com.createstats.block;

// real slats now, the old one just squashed the leaf toward the head
public final class GarageDoorCurl {
    public static final int PX = 16;

    public static final int SLAT_PITCH = 4;

    public static final int SLAT_FACE = 3;

    public static final float SLAT_THICKNESS = 0.75f;

    public static final float DRUM_R0 = 3.0f;

    public static final float HOOD_BORE_PX = 15.0f;

    private GarageDoorCurl() {
    }

    public static int slatCount(int height) {
        return height * PX / SLAT_PITCH;
    }

    public static float lintelPx(int height) {
        return (float) height * PX;
    }

    public static float bottomBarPx(float openness, int height) {
        return clamp01(openness) * lintelPx(height);
    }

    public static float slatBasePx(int index, float openness, int height) {
        return bottomBarPx(openness, height) + (float) index * SLAT_PITCH;
    }

    public static float visibleFace(int index, float openness, int height) {
        final float base = slatBasePx(index, openness, height);
        final float lintel = lintelPx(height);
        if (base >= lintel) {
            return 0.0f;
        }
        return Math.min(SLAT_FACE, lintel - base);
    }

    public static boolean visible(int index, float openness, int height) {
        return visibleFace(index, openness, height) > 0.0f;
    }

    public static int visionCourse(int height) {
        return Math.max(1, Math.min(EYE_COURSE, Math.round(slatCount(height) * 2.0f / 3.0f)));
    }

    public static final int EYE_COURSE = 7;

    public static float barrelRadiusAt(float openness, int height) {
        return barrelRadiusPx(woundPx(openness, height));
    }

    public static float woundPx(float openness, int height) {
        return bottomBarPx(openness, height);
    }

    public static float barrelRadiusPx(float wound) {
        final float w = Math.max(0.0f, wound);
        return (float) Math.sqrt(DRUM_R0 * DRUM_R0 + w * SLAT_THICKNESS / Math.PI);
    }

    public static float barrelAngle(float wound) {
        return (float) (2.0 * Math.PI / SLAT_THICKNESS) * (barrelRadiusPx(wound) - DRUM_R0);
    }

    public static float barrelDiameterPx(int height) {
        return 2.0f * barrelRadiusPx(lintelPx(height));
    }

    public static boolean fitsHood(int height) {
        return barrelDiameterPx(height) <= HOOD_BORE_PX;
    }

    public static int moveTicks(int height) {
        return BASE_TICKS + PER_BLOCK_TICKS * height;
    }

    private static final int BASE_TICKS = 8;

    private static final int PER_BLOCK_TICKS = 6;

    private static float clamp01(float v) {
        return v < 0.0f ? 0.0f : (v > 1.0f ? 1.0f : v);
    }
}
