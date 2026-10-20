package com.createstats.client.wire;

public interface WireCanvas {
    void fill(int x0, int y0, int x1, int y1, int argb);

    void text(String s, int x, int y, int argb);

    int width(String s);

    default void shadowText(String s, int x, int y, int argb) {
        text(s, x + 1, y + 1, (argb & 0xFCFCFC) >> 2 | argb & 0xFF000000);
        text(s, x, y, argb);
    }
}
