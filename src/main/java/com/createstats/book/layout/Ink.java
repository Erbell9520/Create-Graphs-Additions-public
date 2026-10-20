package com.createstats.book.layout;

import com.createstats.book.BookTokens;
import com.createstats.book.content.Mark;

import java.util.List;

public final class Ink {
    private Ink() {
    }

    public static final int RULED_HEADING = 20;

    public static final int TABLE_HEADING = 15;

    public static void ruledHeading(BookCanvas c, int left, int top, int w, String title) {
        ruledHeading(c, left, top, w, title, true);
    }

    public static void ruledHeading(BookCanvas c, int left, int top, int w, String title, boolean rule) {
        if (rule) {
            c.hline(left, left + w, top, BookTokens.RULE);
        }
        c.text(title, left, top + 6, BookTokens.INK, true);
    }

    public static void tableHeading(BookCanvas c, int left, int top, int w, String title, String note) {
        c.text(title, left, top, BookTokens.INK, true);

        if (note != null && !note.isEmpty() && c.width(title, true) + 8 + c.width(note, false) <= w) {
            c.textRight(note, left + w, top, BookTokens.MUTE, false);
        }
        c.hline(left, left + w, top + 11, BookTokens.INK);
    }

    public static final int TAB_CHIP_H = 11;
    private static final int TAB_CHIP_GAP = 2;

    public static int tabStrip(BookCanvas c, int right, int top, List<String> names, int current, int colour,
                               int room) {
        List<String> labels = names;
        for (int level = 0; level < 3; level++) {
            labels = tabLabels(names, current, level);
            if (tabStripWidth(c, labels) <= room) {
                break;
            }
        }
        final int width = tabStripWidth(c, labels);
        int x = right - width;
        for (int i = 0; i < labels.size(); i++) {
            final String s = labels.get(i);
            final int cw = c.width(s, false) + 8;
            c.printed(x, top - 2, x + cw, top - 2 + TAB_CHIP_H);
            if (i == current) {
                c.fill(x, top - 2, x + cw, top - 2 + TAB_CHIP_H, colour);
                c.text(s, x + 4, top, BookTokens.WHITE, false);
            } else {
                c.outline(x, top - 2, x + cw, top - 2 + TAB_CHIP_H, BookTokens.RULE);
                c.text(s, x + 4, top, BookTokens.MUTE, false);
            }
            x += cw + TAB_CHIP_GAP;
        }
        return width;
    }

    static List<String> tabLabels(List<String> names, int current, int level) {
        final List<String> out = new java.util.ArrayList<>(names.size());
        for (int i = 0; i < names.size(); i++) {
            out.add(level == 0 || (level == 1 && i == current) ? names.get(i) : String.valueOf(i + 1));
        }
        return out;
    }

    static int tabStripWidth(Measure m, List<String> labels) {
        int w = 0;
        for (final String s : labels) {
            w += m.width(s, false) + 8 + TAB_CHIP_GAP;
        }
        return Math.max(0, w - TAB_CHIP_GAP);
    }

    public static void pin(BookCanvas c, int x, int y, int number, int colour, boolean lit) {
        final String n = String.valueOf(number);
        final int cx = x + BookTokens.PIN_W / 2 + 1;
        if (lit) {
            c.fill(x - 1, y - 1, x + BookTokens.PIN_W + 1, y + BookTokens.PIN_H + 1, colour);
            c.fill(x, y, x + BookTokens.PIN_W, y + BookTokens.PIN_H, BookTokens.WHITE);
            c.textCentre(n, cx, y + 1, colour, true);
        } else {
            c.fill(x, y, x + BookTokens.PIN_W, y + BookTokens.PIN_H, colour);
            c.textCentre(n, cx, y + 1, BookTokens.WHITE, false);
        }
    }

    public static void mark(BookCanvas c, Mark mark, int x, int y) {
        switch (mark) {
            case RUNNING -> {
                c.fill(x + 1, y, x + 6, y + 7, BookTokens.MARK_RUNNING);
                c.fill(x, y + 1, x + 7, y + 6, BookTokens.MARK_RUNNING);
            }
            case IDLE -> {
                final int k = BookTokens.MARK_IDLE;
                c.fill(x + 1, y, x + 6, y + 1, k);
                c.fill(x + 1, y + 6, x + 6, y + 7, k);
                c.fill(x, y + 1, x + 1, y + 6, k);
                c.fill(x + 6, y + 1, x + 7, y + 6, k);
            }
            case CAUTION -> {
                final int k = BookTokens.MARK_CAUTION;
                for (int i = 0; i < 4; i++) {
                    c.fill(x + 3 - i, y + 1 + i + i / 2, x + 4 + i, y + 2 + i + i / 2, k);
                }
                c.fill(x, y + 6, x + 7, y + 7, k);
            }
            case FAULT -> {
                c.fill(x, y, x + 7, y + 7, BookTokens.MARK_FAULT);
                c.fill(x + 3, y + 1, x + 4, y + 4, BookTokens.WHITE);
                c.fill(x + 3, y + 5, x + 4, y + 6, BookTokens.WHITE);
            }
        }
    }

    public static void plus(BookCanvas c, int x, int y, int colour) {
        c.fill(x + 1, y + 1, x + 4, y + 4, colour);
        c.fill(x + 2, y, x + 3, y + 5, colour);
        c.fill(x, y + 2, x + 5, y + 3, colour);
    }

    public static void corners(BookCanvas c, int x0, int y0, int x1, int y1, int colour) {
        final int len = 7;
        c.fill(x0, y0, x0 + len, y0 + 1, colour);
        c.fill(x0, y0, x0 + 1, y0 + len, colour);
        c.fill(x1 - len, y0, x1, y0 + 1, colour);
        c.fill(x1 - 1, y0, x1, y0 + len, colour);
        c.fill(x0, y1 - 1, x0 + len, y1, colour);
        c.fill(x0, y1 - len, x0 + 1, y1, colour);
        c.fill(x1 - len, y1 - 1, x1, y1, colour);
        c.fill(x1 - 1, y1 - len, x1, y1, colour);
    }

    public static void casing(BookCanvas c, int x0, int y0, int x1, int y1, int colour) {
        c.line(x0 - 1, y0, x1 - 1, y1, colour);
        c.line(x0 + 1, y0, x1 + 1, y1, colour);
        c.line(x0, y0 - 1, x1, y1 - 1, colour);
        c.line(x0, y0 + 1, x1, y1 + 1, colour);
    }

    public static void arrowRight(BookCanvas c, int x, int y, int colour) {
        c.fill(x - 1, y - 2, x, y + 3, colour);
        c.fill(x, y - 1, x + 1, y + 2, colour);
    }

    public static void arrowDown(BookCanvas c, int x, int y, int colour) {
        c.fill(x - 2, y - 1, x + 3, y, colour);
        c.fill(x - 1, y, x + 2, y + 1, colour);
    }
}
