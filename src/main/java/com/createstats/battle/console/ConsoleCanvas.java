package com.createstats.battle.console;

public interface ConsoleCanvas {
    void fill(int x0, int y0, int x1, int y1, int argb);

    void text(String text, int x, int y, int argb);

    default void text(String text, int x, int y, int argb, int scale, boolean bold) {
        text(text, x, y, argb);
    }

    void layer();
}
