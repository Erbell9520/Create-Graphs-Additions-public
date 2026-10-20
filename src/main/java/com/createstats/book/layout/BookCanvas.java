package com.createstats.book.layout;

import com.createstats.book.BookTokens;

public interface BookCanvas extends Measure {
    void fill(int x0, int y0, int x1, int y1, int argb);

    void text(String s, int x, int y, int argb, boolean bold);

    void textScaled(String s, int x, int y, int argb, int scale);

    void item(String registryId, int x, int y);

    void sprite(String spriteId, int x, int y, int w, int h);

    void paper(int x0, int y0, int x1, int y1);

    void vinyl(int x0, int y0, int x1, int y1);

    void specimen(String specimenId, int x, int y, int num, int den, int mouseX, int mouseY);

    void hot(String id, int x0, int y0, int x1, int y1);

    void pushFlip(int pivotX, float scaleX);

    void popFlip();

    void raise(int z);

    default void printed(int x0, int y0, int x1, int y1) {
    }

    default void hline(int x0, int x1, int y, int argb) {
        fill(x0, y, x1, y + 1, argb);
    }

    default void vline(int x, int y0, int y1, int argb) {
        fill(x, y0, x + 1, y1, argb);
    }

    default void outline(int x0, int y0, int x1, int y1, int argb) {
        fill(x0, y0, x1, y0 + 1, argb);
        fill(x0, y1 - 1, x1, y1, argb);
        fill(x0, y0 + 1, x0 + 1, y1 - 1, argb);
        fill(x1 - 1, y0 + 1, x1, y1 - 1, argb);
    }

    default void shadowText(String s, int x, int y, int argb) {
        text(s, x + 1, y + 1, BookTokens.BUTTON_SHADOW, false);
        text(s, x, y, argb, false);
    }

    default void textRight(String s, int right, int y, int argb, boolean bold) {
        text(s, right - width(s, bold), y, argb, bold);
    }

    default void textCentre(String s, int cx, int y, int argb, boolean bold) {
        text(s, cx - width(s, bold) / 2, y, argb, bold);
    }

    default void line(int x0, int y0, int x1, int y1, int argb) {
        final int dx = Math.abs(x1 - x0);
        final int dy = -Math.abs(y1 - y0);
        final int sx = x0 < x1 ? 1 : -1;
        final int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        int x = x0;
        int y = y0;
        while (true) {
            fill(x, y, x + 1, y + 1, argb);
            if (x == x1 && y == y1) {
                return;
            }
            final int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y += sy;
            }
        }
    }

    default void dots(int x0, int x1, int y, int argb, int step) {
        for (int x = x0; x < x1; x += step) {
            fill(x, y, x + 1, y + 1, argb);
        }
    }
}
