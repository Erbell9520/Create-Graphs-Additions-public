package com.createstats.statsmk3.ui;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// float, rounding every step makes motion stutter
@OnlyIn(Dist.CLIENT)
public final class Rect {
    public static final Rect EMPTY = new Rect(0, 0, 0, 0);

    public final float x;
    public final float y;
    public final float w;
    public final float h;

    public Rect(float x, float y, float w, float h) {
        this.x = x;
        this.y = y;
        this.w = Math.max(0.0f, w);
        this.h = Math.max(0.0f, h);
    }

    public static Rect of(float x, float y, float w, float h) {
        return new Rect(x, y, w, h);
    }

    public float right() {
        return this.x + this.w;
    }

    public float bottom() {
        return this.y + this.h;
    }

    public float centerX() {
        return this.x + this.w * 0.5f;
    }

    public float centerY() {
        return this.y + this.h * 0.5f;
    }

    public boolean contains(double px, double py) {
        return px >= this.x && px < this.x + this.w && py >= this.y && py < this.y + this.h;
    }

    public Rect inset(float amount) {
        return new Rect(this.x + amount, this.y + amount, this.w - amount * 2.0f, this.h - amount * 2.0f);
    }

    public Rect inset(float left, float top, float right, float bottom) {
        return new Rect(this.x + left, this.y + top, this.w - left - right, this.h - top - bottom);
    }

    public Rect offset(float dx, float dy) {
        return new Rect(this.x + dx, this.y + dy, this.w, this.h);
    }

    public Rect withHeight(float height) {
        return new Rect(this.x, this.y, this.w, height);
    }

    public Rect withWidth(float width) {
        return new Rect(this.x, this.y, width, this.h);
    }

    public Rect top(float height) {
        return new Rect(this.x, this.y, this.w, Math.min(height, this.h));
    }

    public Rect below(float height) {
        final float used = Math.min(height, this.h);
        return new Rect(this.x, this.y + used, this.w, this.h - used);
    }

    public Rect left(float width) {
        return new Rect(this.x, this.y, Math.min(width, this.w), this.h);
    }

    public Rect afterLeft(float width) {
        final float used = Math.min(width, this.w);
        return new Rect(this.x + used, this.y, this.w - used, this.h);
    }

    public Rect intersect(Rect other) {
        final float nx = Math.max(this.x, other.x);
        final float ny = Math.max(this.y, other.y);
        final float nr = Math.min(this.right(), other.right());
        final float nb = Math.min(this.bottom(), other.bottom());
        if (nr <= nx || nb <= ny) {
            return EMPTY;
        }
        return new Rect(nx, ny, nr - nx, nb - ny);
    }

    public boolean isEmpty() {
        return this.w <= 0.0f || this.h <= 0.0f;
    }

    @Override
    public String toString() {
        return "Rect[" + this.x + ", " + this.y + ", " + this.w + " x " + this.h + "]";
    }
}
