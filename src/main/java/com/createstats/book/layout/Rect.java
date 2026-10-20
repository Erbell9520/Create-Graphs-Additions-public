package com.createstats.book.layout;

// right/bottom exclusive like fill
public record Rect(int x, int y, int w, int h) {
    public int right() {
        return x + w;
    }

    public int bottom() {
        return y + h;
    }

    public boolean contains(int px, int py) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    public boolean containsRect(Rect o) {
        return o.x >= x && o.y >= y && o.right() <= right() && o.bottom() <= bottom();
    }

    public Rect inset(int left, int top, int right, int bottom) {
        return new Rect(x + left, y + top, w - left - right, h - top - bottom);
    }
}
