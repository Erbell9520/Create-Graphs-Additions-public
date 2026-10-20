package com.createstats.statsmk3.ui;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class Scroll {
    private static final float WHEEL_STEP = 42.0f;
    private static final float RUBBER = 0.38f;

    private final Spring offset = Spring.ui(0.0f);
    private float contentHeight;
    private float viewportHeight;
    private float target;

    private boolean dragging;
    private double lastDragY;

    public void setMetrics(float contentHeight, float viewportHeight) {
        this.contentHeight = Math.max(0.0f, contentHeight);
        this.viewportHeight = Math.max(0.0f, viewportHeight);
        this.target = clampTarget(this.target);
    }

    public float max() {
        return Math.max(0.0f, this.contentHeight - this.viewportHeight);
    }

    public boolean scrollable() {
        return max() > 0.5f;
    }

    public float value() {
        return this.offset.value();
    }

    public void jumpTo(float v) {
        this.target = clampTarget(v);
        this.offset.jump(this.target);
    }

    public void update(float dt) {
        this.offset.set(this.target).step(dt);
    }

    public boolean wheel(double amount) {
        if (!scrollable()) {
            return false;
        }
        this.target = clampTarget(this.target - (float) amount * WHEEL_STEP);
        return true;
    }

    public boolean pressBar(Rect track, double mx, double my) {
        // wider hit than the drawn bar

        final Rect hit = new Rect(track.x - 4.0f, track.y, track.w + 8.0f, track.h);
        if (!scrollable() || !hit.contains(mx, my)) {
            return false;
        }
        this.dragging = true;
        this.lastDragY = my;
        seekTo(track, my);
        return true;
    }

    public boolean dragBar(Rect track, double my) {
        if (!this.dragging) {
            return false;
        }
        this.lastDragY = my;
        seekTo(track, my);
        return true;
    }

    public void endDrag() {
        this.dragging = false;
        this.target = clampTarget(this.target);
    }

    public boolean dragging() {
        return this.dragging;
    }

    private void seekTo(Rect track, double my) {
        final float ratio = this.contentHeight <= 0.0f ? 1.0f : this.viewportHeight / this.contentHeight;
        final float thumbH = Math.max(24.0f, track.h * ratio);
        final float usable = Math.max(1.0f, track.h - thumbH);
        final float t = (float) ((my - track.y - thumbH * 0.5f) / usable);
        this.target = clampTarget(Easing.clamp01(t) * max());
    }

    private float clampTarget(float v) {
        final float m = max();
        if (v < 0.0f) {
            return 0.0f;
        }
        return Math.min(v, m);
    }

    public void drawBar(PanelSurface ctx, Rect track) {
        if (!scrollable()) {
            return;
        }
        final float ratio = this.viewportHeight / this.contentHeight;
        final float thumbH = Math.max(24.0f, track.h * ratio);
        final float t = max() <= 0.0f ? 0.0f : Easing.clamp01(this.offset.value() / max());
        final float thumbY = track.y + (track.h - thumbH) * t;
        ctx.roundedRect(new Rect(track.x, track.y, track.w, track.h), track.w * 0.5f,
                Tokens.alpha(Tokens.LINE, 0.5f));
        ctx.roundedRect(new Rect(track.x, thumbY, track.w, thumbH), track.w * 0.5f, Tokens.LINE_HI);
    }
}
