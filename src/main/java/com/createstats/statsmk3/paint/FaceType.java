package com.createstats.statsmk3.paint;

import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Text;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// size and importance are two decisions, don't multiply them into one scale
@OnlyIn(Dist.CLIENT)
public final class FaceType {
    public final float unit;

    public final float hero;

    public final float title;

    public final float head;

    public final float row;

    public final float meta;

    public final float micro;

    public final float nano;

    public final float pad;

    public final float gap;

    public final float hair;

    private FaceType(float unit, float hero, float title, float head, float row, float meta,
                     float micro, float nano, float pad, float gap, float hair) {
        this.unit = unit;
        this.hero = hero;
        this.title = title;
        this.head = head;
        this.row = row;
        this.meta = meta;
        this.micro = micro;
        this.nano = nano;
        this.pad = pad;
        this.gap = gap;
        this.hair = hair;
    }

    public static FaceType of(Rect area) {
        return of(Math.min(area.w, area.h));
    }

    public static FaceType of(float shortSide) {
        final float unit = clamp((float) Math.sqrt(Math.max(1.0f, shortSide) / 92.0f), 0.6f, 2.4f);
        return new FaceType(unit,
                clamp(unit * 3.1f, 1.5f, 6.0f),
                clamp(unit * 1.45f, 0.85f, 3.0f),
                clamp(unit * 1.05f, 0.7f, 1.9f),
                clamp(unit * 0.92f, 0.62f, 1.75f),
                clamp(unit * 0.76f, 0.55f, 1.4f),
                clamp(unit * 0.62f, 0.50f, 1.1f),
                clamp(unit * 0.50f, 0.44f, 0.9f),
                Math.max(3.0f, Math.round(3.0f + 4.0f * unit)),
                Math.max(1.0f, Math.round(2.0f * unit)),
                Math.max(0.6f, 0.55f * unit));
    }

    public boolean hasNano() {
        return this.unit >= 0.82f;
    }

    public static float lh(float scale) {
        return Text.lineHeight(scale);
    }

    public float twoLineRow() {
        return lh(this.row) + lh(this.micro) + this.gap;
    }

    public float oneLineRow() {
        return lh(this.row) + this.gap * 1.5f;
    }

    private static float clamp(float v, float lo, float hi) {
        return Float.isNaN(v) ? lo : v < lo ? lo : Math.min(v, hi);
    }
}
