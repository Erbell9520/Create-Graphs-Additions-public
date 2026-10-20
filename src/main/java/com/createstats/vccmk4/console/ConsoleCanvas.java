package com.createstats.vccmk4.console;

public interface ConsoleCanvas {
    void fill(int x0, int y0, int x1, int y1, int argb);

    void text(String text, int x, int y, int argb);

    void quad(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int argb);

    void layer();
}
