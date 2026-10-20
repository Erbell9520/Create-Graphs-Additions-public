package com.createstats.statsmk3.page;

import java.util.ArrayDeque;
import java.util.Deque;

import com.createstats.statsmk3.CountingSurface;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Text;

final class AeroExtentSurface implements PanelSurface {
    static final class Extent {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        String worst = "";

        boolean empty() {
            return this.minX > this.maxX;
        }

        void add(float x, float y) {
            this.minX = Math.min(this.minX, x);
            this.minY = Math.min(this.minY, y);
            this.maxX = Math.max(this.maxX, x);
            this.maxY = Math.max(this.maxY, y);
        }

        float over(Rect r) {
            if (empty()) {
                return 0.0f;
            }
            return Math.max(Math.max(r.x - this.minX, this.maxX - r.right()),
                    Math.max(r.y - this.minY, this.maxY - r.bottom()));
        }
    }

    final Extent shapes = new Extent();
    final Extent text = new Extent();

    private final Rect bound;
    String escapedText;
    float escapedBy;

    AeroExtentSurface(Rect bound) {
        this.bound = bound;
    }

    @Override
    public void tri(float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3) {
        if ((c1 >>> 24) == 0 && (c2 >>> 24) == 0 && (c3 >>> 24) == 0) {
            return;
        }
        this.shapes.add(x1, y1);
        this.shapes.add(x2, y2);
        this.shapes.add(x3, y3);
    }

    @Override
    public void text(String s, float x, float y, int color, float scale) {
        if (s == null || s.isEmpty() || (color >>> 24) == 0) {
            return;
        }
        final float w = textWidth(s, scale);
        final float h = Text.lineHeight(scale);
        this.text.add(x, y);
        this.text.add(x + w, y + h);
        final float over = Math.max(Math.max(this.bound.x - x, x + w - this.bound.right()),
                Math.max(this.bound.y - y, y + h - this.bound.bottom()));
        if (over > this.escapedBy) {
            this.escapedBy = over;
            this.escapedText = s;
        }
    }

    @Override
    public void textCenter(String s, float centerX, float y, int color, float scale) {
        text(s, centerX - textWidth(s, scale) * 0.5f, y, color, scale);
    }

    @Override
    public void textRight(String s, float rightX, float y, int color, float scale) {
        text(s, rightX - textWidth(s, scale), y, color, scale);
    }

    @Override
    public float textWidth(String s, float scale) {
        return CountingSurface.advance(s, scale);
    }

    private final Deque<Rect> clips = new ArrayDeque<>();

    @Override
    public void clip(Rect r) {
        final Rect current = this.clips.peek();
        this.clips.push(current == null ? r : current.intersect(r));
    }

    @Override
    public void unclip() {
        if (!this.clips.isEmpty()) {
            this.clips.pop();
        }
    }

    @Override
    public boolean culled(Rect r) {
        final Rect current = this.clips.peek();
        return r.isEmpty() || (current != null && current.intersect(r).isEmpty());
    }
}
