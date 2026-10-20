package com.createstats.statsmk3;

import java.util.ArrayDeque;
import java.util.Deque;

import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;

public final class CountingSurface implements PanelSurface {
    public int triangles;
    public int textCalls;
    public int clipChanges;

    private final Deque<Rect> clips = new ArrayDeque<>();

    public void reset() {
        this.triangles = 0;
        this.textCalls = 0;
        this.clipChanges = 0;
        this.clips.clear();
    }

    public int vertices() {
        return this.triangles * 3;
    }

    @Override
    public void tri(float x1, float y1, int c1, float x2, float y2, int c2,
                    float x3, float y3, int c3) {
        this.triangles++;
    }

    @Override
    public void text(String s, float x, float y, int color, float scale) {
        if (s != null && !s.isEmpty()) {
            this.textCalls++;
        }
    }

    @Override
    public void textCenter(String s, float centerX, float y, int color, float scale) {
        text(s, centerX, y, color, scale);
    }

    @Override
    public void textRight(String s, float rightX, float y, int color, float scale) {
        text(s, rightX, y, color, scale);
    }

    @Override
    public float textWidth(String s, float scale) {
        return advance(s, scale);
    }

    public static float advance(String s, float scale) {
        if (s == null) {
            return 0.0f;
        }
        int width = 0;
        for (int i = 0; i < s.length(); i++) {
            width += switch (s.charAt(i)) {
                case 'i', '!', ',', '.', ':', ';', '|', '\'' -> 2;
                case 'l', '`' -> 3;
                case ' ', 't', 'I', '[', ']' -> 4;
                case 'f', 'k', '(', ')', '{', '}', '<', '>', '"', '*' -> 5;
                case '@' -> 7;
                default -> 6;
            };
        }
        return width * scale;
    }

    @Override
    public void clip(Rect r) {
        this.clipChanges++;
        final Rect current = this.clips.peek();
        this.clips.push(current == null ? r : current.intersect(r));
    }

    @Override
    public void unclip() {
        if (!this.clips.isEmpty()) {
            this.clipChanges++;
            this.clips.pop();
        }
    }

    @Override
    public boolean culled(Rect r) {
        final Rect current = this.clips.peek();
        return r.isEmpty() || (current != null && current.intersect(r).isEmpty());
    }
}
