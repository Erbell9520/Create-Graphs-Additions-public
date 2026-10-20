package com.createstats.gui.battery;

public interface BatteryCanvas {
    void fill(int x0, int y0, int x1, int y1, int argb);

    void text(String s, int x, int y, int argb);

    int width(String s);

    default void outline(int x, int y, int w, int h, int argb) {
        fill(x, y, x + w, y + 1, argb);
        fill(x, y + h - 1, x + w, y + h, argb);
        fill(x, y + 1, x + 1, y + h - 1, argb);
        fill(x + w - 1, y + 1, x + w, y + h - 1, argb);
    }

    default String fit(String s, int max) {
        if (s == null || max <= 0) {
            return "";
        }
        if (width(s) <= max) {
            return s;
        }
        final int dots = width("...");
        int end = s.length();
        while (end > 0 && width(s.substring(0, end)) + dots > max) {
            end--;
        }
        return end <= 0 ? "" : s.substring(0, end).stripTrailing() + "...";
    }

    default void textRight(String s, int right, int y, int argb) {
        text(s, right - width(s), y, argb);
    }

    default void bold(String s, int x, int y, int argb) {
        text(s, x, y, argb);
        text(s, x + 1, y, argb);
    }

    default int boldWidth(String s) {
        return s == null ? 0 : width(s) + s.length();
    }

    default void textCentred(String s, int cx, int y, int argb) {
        text(s, cx - width(s) / 2, y, argb);
    }
}
