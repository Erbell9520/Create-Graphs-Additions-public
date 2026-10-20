package com.createstats.statsmk3.paint;

import com.createstats.client.DisplayPrefs;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class Lod {
    public static final Lod FULL = new Lod(0, 1.0f, 1.0f, 0.0f, 120, false);

    public static final Lod REDUCED = new Lod(1, 0.0f, 1.0f, 1.0f, 48, true);

    public static final Lod GLYPH = new Lod(2, 0.0f, 0.0f, 0.0f, 0, true);

    private final int tier;
    private final float labelAlpha;
    private final float headerAlpha;
    private final float heroAmount;
    private final int historyBudget;
    private final boolean collapse;

    private Lod(int tier, float labelAlpha, float headerAlpha, float heroAmount,
                int historyBudget, boolean collapse) {
        this.tier = tier;
        this.labelAlpha = clamp01(labelAlpha);
        this.headerAlpha = clamp01(headerAlpha);
        this.heroAmount = clamp01(heroAmount);
        this.historyBudget = Math.max(0, historyBudget);
        this.collapse = collapse;
    }

    public static Lod[] values() {
        return new Lod[]{FULL, REDUCED, GLYPH};
    }

    public static Lod preview(int historyBudget) {
        return new Lod(0, 1.0f, 1.0f, 0.0f, historyBudget, false);
    }

    public static Lod forView(double distanceSq, int multiblockWidth, int multiblockHeight) {
        return forView(distanceSq, multiblockWidth, multiblockHeight, 1.0f);
    }

    public static Lod forView(double distanceSq, int multiblockWidth, int multiblockHeight,
                              float faceScale) {
        final float legible = !Float.isFinite(faceScale) || faceScale <= 0.0f
                ? 1.0f : Math.min(1.5f, faceScale);
        final float full = DisplayPrefs.fullRangeFor(multiblockWidth, multiblockHeight) * legible;

        if (Float.isInfinite(full)) {
            return FULL;
        }
        final float glyph = DisplayPrefs.glyphRangeFor(multiblockWidth, multiblockHeight) * legible;
        final float fade = DisplayPrefs.crossFade() ? DisplayPrefs.FADE_BAND : 0.0f;
        final boolean keepHeader = DisplayPrefs.keepHeader();

        final float fullSolid = full * (1.0f - fade);
        if (distanceSq <= fullSolid * (double) fullSolid) {
            return FULL;
        }

        final double d = Math.sqrt(distanceSq);

        if (d <= full) {
            final float t = ramp(d, fullSolid, full);
            return new Lod(0, 1.0f - t, 1.0f, 0.0f, lerpInt(120, 48, t), false);
        }

        final float glyphSolid = glyph * (1.0f - fade);
        if (d <= glyphSolid) {
            return REDUCED;
        }

        if (d <= glyph) {
            final float t = ramp(d, glyphSolid, glyph);
            return new Lod(1, 0.0f, keepHeader ? 1.0f : 1.0f - t, 1.0f, lerpInt(48, 12, t), true);
        }

        return keepHeader ? new Lod(2, 0.0f, 1.0f, 0.0f, 0, true) : GLYPH;
    }

    private static float ramp(double value, float from, float to) {
        final float span = to - from;
        if (span <= 1.0e-4f) {
            return value >= to ? 1.0f : 0.0f;
        }
        final float t = clamp01((float) ((value - from) / span));
        return t * t * (3.0f - 2.0f * t);
    }

    private static int lerpInt(int a, int b, float t) {
        return Math.round(a + (b - a) * clamp01(t));
    }

    private static float clamp01(float v) {
        return Float.isNaN(v) ? 0.0f : v < 0.0f ? 0.0f : Math.min(v, 1.0f);
    }

    public float labelAlpha() {
        return this.labelAlpha;
    }

    public float headerAlpha() {
        return this.headerAlpha;
    }

    public float heroAmount() {
        return this.heroAmount;
    }

    public boolean showsLabels() {
        return this.labelAlpha > 0.02f;
    }

    public boolean showsHeader() {
        return this.headerAlpha > 0.02f;
    }

    public boolean showsHero() {
        return this.heroAmount > 0.02f;
    }

    public boolean showsGrid() {
        return this.labelAlpha > 0.02f;
    }

    public boolean isGlyph() {
        return this.tier == 2;
    }

    public boolean collapsesToPrimary() {
        return this.collapse && this.tier == 1;
    }

    public int historyBudget() {
        return this.historyBudget;
    }
}
