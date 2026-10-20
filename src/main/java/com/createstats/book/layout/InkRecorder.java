package com.createstats.book.layout;

import java.util.ArrayList;
import java.util.List;

// records where ink would land. the ribbon covered long titles before this
final class InkRecorder implements BookCanvas {
    private final Measure measure;
    final List<Rect> ink = new ArrayList<>();

    InkRecorder(Measure measure) {
        this.measure = measure;
    }

    @Override
    public int width(String s, boolean bold) {
        return measure.width(s, bold);
    }

    @Override
    public void fill(int x0, int y0, int x1, int y1, int argb) {
    }

    @Override
    public void text(String s, int x, int y, int argb, boolean bold) {
        if (s != null && !s.isEmpty()) {
            ink.add(new Rect(x, y, width(s, bold), 8));
        }
    }

    @Override
    public void textScaled(String s, int x, int y, int argb, int scale) {
        if (s != null && !s.isEmpty()) {
            ink.add(new Rect(x, y, width(s, false) * scale, 8 * scale));
        }
    }

    @Override
    public void item(String registryId, int x, int y) {
        ink.add(new Rect(x, y, 16, 16));
    }

    @Override
    public void printed(int x0, int y0, int x1, int y1) {
        ink.add(new Rect(x0, y0, x1 - x0, y1 - y0));
    }

    @Override
    public void sprite(String spriteId, int x, int y, int w, int h) {
        ink.add(new Rect(x, y, w, h));
    }

    @Override
    public void paper(int x0, int y0, int x1, int y1) {
    }

    @Override
    public void vinyl(int x0, int y0, int x1, int y1) {
    }

    @Override
    public void specimen(String specimenId, int x, int y, int num, int den, int mouseX, int mouseY) {
    }

    @Override
    public void hot(String id, int x0, int y0, int x1, int y1) {
    }

    @Override
    public void pushFlip(int pivotX, float scaleX) {
    }

    @Override
    public void popFlip() {
    }

    @Override
    public void raise(int z) {
    }

    boolean covers(Rect r) {
        for (final Rect m : ink) {
            if (m.x() < r.right() && r.x() < m.right() && m.y() < r.bottom() && r.y() < m.bottom()) {
                return true;
            }
        }
        return false;
    }
}
