package com.createstats.statsmk3.page;

import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;

final class ZoomSurface implements PanelSurface {
    private final PanelSurface d;
    private final float k;

    ZoomSurface(PanelSurface d, float k) {
        this.d = d;
        this.k = k;
    }

    @Override
    public void tri(float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3) {
        d.tri(x1 * k, y1 * k, c1, x2 * k, y2 * k, c2, x3 * k, y3 * k, c3);
    }

    @Override
    public void text(String s, float x, float y, int color, float scale) {
        d.text(s, x * k, y * k, color, scale * k);
    }

    @Override
    public void textCenter(String s, float centerX, float y, int color, float scale) {
        d.textCenter(s, centerX * k, y * k, color, scale * k);
    }

    @Override
    public void textRight(String s, float rightX, float y, int color, float scale) {
        d.textRight(s, rightX * k, y * k, color, scale * k);
    }

    @Override
    public float textWidth(String s, float scale) {
        return d.textWidth(s, scale * k) / k;
    }

    @Override
    public void clip(Rect r) {
        d.clip(new Rect(r.x * k, r.y * k, r.w * k, r.h * k));
    }

    @Override
    public void unclip() {
        d.unclip();
    }

    @Override
    public float feather() {
        return d.feather() / k;
    }

    @Override
    public boolean culled(Rect r) {
        return d.culled(new Rect(r.x * k, r.y * k, r.w * k, r.h * k));
    }
}
