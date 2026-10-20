package com.createstats.book.specimen;

import com.createstats.book.BookTokens;
import com.createstats.book.content.Section;
import com.createstats.book.layout.BookCanvas;

import java.util.List;

// a data sheet about the data sheet
public final class SheetDrawing {
    public static final String ID = "data_sheet";
    public static final int W = 240;
    public static final int H = 150;

    private static final int LX0 = 11;
    private static final int LX1 = 117;
    private static final int RX0 = 123;
    private static final int RX1 = 229;
    private static final int SY0 = 5;
    private static final int SY1 = 145;
    private static final int[] RINGS = {35, 75, 115};

    private static final int OPEN = Section.POWER.ordinal();

    private SheetDrawing() {
    }

    public static SpecimenAnatomy anatomy() {
        return new SpecimenAnatomy(ID, W, H, List.of(
                new SpecimenAnatomy.Hot("band", LX0, SY0, LX1 - LX0 - 1, 5),
                new SpecimenAnatomy.Hot("ribbon", 81, 1, 7, 17),
                new SpecimenAnatomy.Hot("title", 14, 15, 43, 9),
                new SpecimenAnatomy.Hot("tabs", 3, 9, 8, 46),
                new SpecimenAnatomy.Hot("figure", 33, 33, 62, 36),
                new SpecimenAnatomy.Hot("ratings", 14, 79, 99, 23),
                new SpecimenAnatomy.Hot("application", 14, 106, 99, 16),
                new SpecimenAnatomy.Hot("see_also", 14, 126, 99, 7),
                new SpecimenAnatomy.Hot("controls", 127, 12, 100, 52),
                new SpecimenAnatomy.Hot("status", 127, 66, 100, 34),
                new SpecimenAnatomy.Hot("faults", 127, 103, 100, 28),
                new SpecimenAnatomy.Hot("page", 214, 138, 13, 5)), true);
    }

    public static void paint(BookCanvas c, int ox, int oy) {
        final Pen p = new Pen(c, ox, oy);
        binder(c, p, ox, oy);
        for (int i = 0; i < Section.values().length; i++) {
            tab(p, i);
        }
        sheet(c, p, LX0, true);
        sheet(c, p, RX0, false);
        rings(p);
        left(p);
        right(p);
        ribbon(p);
    }

    private record Pen(BookCanvas c, int ox, int oy) {
        void fill(int x0, int y0, int x1, int y1, int argb) {
            c.fill(ox + x0, oy + y0, ox + x1, oy + y1, argb);
        }

        void stroke(int x0, int x1, int y, int argb) {
            fill(x0, y, x1, y + 1, argb);
        }

        void line(int x0, int y0, int x1, int y1, int argb) {
            c.line(ox + x0, oy + y0, ox + x1, oy + y1, argb);
        }

        void dots(int x0, int x1, int y, int argb) {
            c.dots(ox + x0, ox + x1, oy + y, argb, 2);
        }
    }

    private static void binder(BookCanvas c, Pen p, int ox, int oy) {
        p.fill(0, 0, W, H, BookTokens.VINYL_OUTLINE);
        c.vinyl(ox + 1, oy + 1, ox + W - 1, oy + H - 1);
        p.fill(1, 1, W - 1, 2, BookTokens.VINYL_LIT);
        p.fill(1, 1, 2, H - 1, BookTokens.VINYL_LIT);
    }

    private static void tab(Pen p, int i) {
        final int col = Section.values()[i].colour();
        final int x = i <= OPEN ? 3 : RX1;
        final int y = 9 + i * 16;
        p.fill(x, y, x + 8, y + 14, col);
        p.fill(x, y, x + 8, y + 1, BookTokens.tabLit(col));
        p.fill(x, y + 13, x + 8, y + 14, BookTokens.tabShade(col));
        p.fill(x + 3, y + 2, x + 5, y + 4, BookTokens.WHITE);
        p.fill(x + 2, y + 6, x + 6, y + 11, BookTokens.well(col));
    }

    private static void sheet(BookCanvas c, Pen p, int x0, boolean left) {
        final int x1 = x0 + (LX1 - LX0);
        p.fill(x0, SY0, x1, SY1, BookTokens.PAPER_EDGE);
        c.paper(p.ox() + x0, p.oy() + SY0, p.ox() + x1 - 1, p.oy() + SY1 - 1);

        for (int i = 0; i < 3; i++) {
            final int a = BookTokens.CURL_ALPHA[i] << 24;
            if (left) {
                p.fill(x1 - 2 * (i + 1), SY0, x1 - 2 * i, SY1, a);
            } else {
                p.fill(x0 + 2 * i, SY0, x0 + 2 * (i + 1), SY1, a);
            }
        }
        for (final int ry : RINGS) {
            final int hx = left ? x1 - 6 : x0 + 2;
            p.fill(hx, ry - 2, hx + 4, ry + 2, BookTokens.HOLE_RIM);
            p.fill(hx + 1, ry - 1, hx + 3, ry + 1, BookTokens.HOLE);
        }

        final int col = Section.values()[OPEN].colour();
        p.fill(x0, SY0, x1 - 1, SY0 + 5, col);
        if (left) {
            p.stroke(x0 + 3, x0 + 5, SY0 + 2, BookTokens.WHITE);
            p.stroke(x0 + 7, x0 + 19, SY0 + 2, BookTokens.WHITE);
            p.stroke(x1 - 22, x1 - 4, SY0 + 2, BookTokens.WHITE);
        } else {
            p.stroke(x0 + 4, x0 + 38, SY0 + 2, BookTokens.WHITE);
            p.stroke(x1 - 25, x1 - 4, SY0 + 2, BookTokens.WHITE);
        }

        final int cx0 = left ? x0 + 3 : x0 + 4;
        final int cx1 = left ? x1 - 4 : x1 - 3;
        p.fill(cx0, 137, cx1, 138, BookTokens.RULE);
        p.stroke(cx0, cx0 + (left ? 24 : 32), 140, BookTokens.MUTE);
        p.fill(cx1 - 9, 140, cx1, 142, BookTokens.INK);
    }

    private static void rings(Pen p) {
        p.fill(LX1, 12, RX0, 138, BookTokens.PLATE);
        p.fill(LX1 + 1, 12, LX1 + 2, 138, BookTokens.PLATE_LIGHT);
        p.fill(RX0 - 2, 12, RX0 - 1, 138, BookTokens.PLATE_DARK);
        for (final int ry : RINGS) {
            p.fill(LX1 - 5, ry - 1, RX0 + 5, ry + 1, BookTokens.RING);
            p.fill(LX1 - 5, ry - 1, RX0 + 5, ry, BookTokens.RING_LIT);
        }
    }

    private static void left(Pen p) {
        final int col = Section.values()[OPEN].colour();

        p.fill(14, 16, 19, 21, BookTokens.PLATE_DARK);
        p.fill(21, 16, 56, 19, BookTokens.INK);
        p.stroke(21, 48, 22, BookTokens.MUTE);
        p.stroke(100, 113, 22, BookTokens.MUTE);
        p.stroke(14, 100, 25, BookTokens.INK);
        p.fill(14, 27, 113, 28, BookTokens.INK);

        p.stroke(14, 66, 30, BookTokens.INK);
        p.fill(33, 33, 95, 69, BookTokens.INK);
        p.fill(34, 34, 94, 68, BookTokens.RULE);
        p.fill(39, 38, 89, 61, BookTokens.HOLE);
        p.stroke(41, 49, 40, BookTokens.MARK_RUNNING);
        int py = 52;
        for (int x = 41; x < 87; x += 4) {
            final int ny = (x / 4) % 2 == 0 ? 47 : 56;
            p.line(x, py, x + 4, ny, BookTokens.MARK_RUNNING);
            py = ny;
        }
        p.fill(39, 63, 55, 66, BookTokens.HOLE);
        p.fill(60, 63, 76, 66, BookTokens.HOLE);
        for (final int y : new int[]{38, 46, 55, 63}) {
            p.fill(24, y, 28, y + 3, col);
            p.stroke(28, 33, y + 1, BookTokens.INK);
        }
        for (final int y : new int[]{37, 45, 54, 62}) {
            p.fill(100, y, 104, y + 3, col);
            p.stroke(95, 100, y + 1, BookTokens.INK);
        }
        p.stroke(33, 70, 72, BookTokens.MUTE);

        p.fill(14, 77, 113, 78, BookTokens.RULE);
        p.stroke(14, 34, 80, BookTokens.INK);
        for (int i = 0; i < 4; i++) {
            final int y = 84 + i * 4;
            p.stroke(14, 26 + (i % 2) * 6, y, BookTokens.INK);
            p.dots(34, 96, y, BookTokens.RULE);
            p.stroke(98, 106, y, BookTokens.INK);
            plus(p, 109, y - 1, col);
        }
        plus(p, 14, 99, col);
        p.stroke(18, 70, 100, BookTokens.MUTE);

        p.fill(14, 104, 113, 105, BookTokens.RULE);
        p.stroke(14, 44, 107, BookTokens.INK);
        for (int i = 0; i < 5; i++) {
            final int x = 22 + i * 16;
            p.fill(x, 110, x + 6, 116, i == 2 ? col : BookTokens.RULE);
            p.fill(x + 1, 111, x + 5, 115, BookTokens.WHITE);
            if (i < 4) {
                p.dots(x + 7, x + 15, 113, BookTokens.INK);
            }
        }
        p.stroke(14, 80, 120, BookTokens.MUTE);

        p.fill(14, 124, 113, 125, BookTokens.RULE);
        p.stroke(14, 28, 129, BookTokens.INK);
        for (final int x : new int[]{32, 66}) {
            p.fill(x, 126, x + 30, 133, BookTokens.RULE);
            p.fill(x + 1, 127, x + 29, 132, BookTokens.WHITE);
            p.stroke(x + 3, x + 18, 129, BookTokens.INK);
            p.stroke(x + 21, x + 27, 129, col);
        }
    }

    private static void right(Pen p) {
        final int col = Section.values()[OPEN].colour();
        p.stroke(127, 170, 13, BookTokens.INK);
        p.fill(127, 16, 226, 17, BookTokens.INK);
        p.stroke(127, 131, 19, BookTokens.MUTE);
        p.stroke(134, 142, 19, BookTokens.MUTE);
        p.stroke(160, 176, 19, BookTokens.MUTE);
        p.fill(127, 21, 226, 22, BookTokens.RULE);
        for (int i = 0; i < 10; i++) {
            final int y = 24 + i * 4;
            p.fill(127, y, 130, y + 2, col);
            p.stroke(133, 150 - (i % 3) * 3, y, BookTokens.INK);
            p.stroke(160, 222 - (i % 4) * 11, y, BookTokens.INK);
        }
        p.stroke(127, 160, 67, BookTokens.INK);
        p.fill(127, 70, 226, 71, BookTokens.INK);
        for (int i = 0; i < 6; i++) {
            final int y = 73 + i * 5;
            mark(p, i, 127, y);
            p.stroke(132, 148 - (i % 2) * 4, y + 1, BookTokens.INK);
            p.stroke(155, 215 - (i % 3) * 10, y + 1, BookTokens.INK);
        }
        p.stroke(127, 162, 104, BookTokens.INK);
        p.fill(127, 107, 226, 108, BookTokens.INK);
        box(p, 127, 111, 147, false, col);
        box(p, 155, 111, 177, false, col);
        box(p, 185, 111, 207, false, col);
        arrow(p, 148, 154, 114);
        arrow(p, 178, 184, 114);
        p.fill(160, 118, 161, 123, BookTokens.INK);
        box(p, 155, 124, 180, true, col);
    }

    private static void box(Pen p, int x0, int y0, int x1, boolean cause, int col) {
        p.fill(x0, y0, x1, y0 + 7, BookTokens.INK);
        p.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 6, cause ? col : BookTokens.WHITE);
        p.stroke(x0 + 3, x1 - 3, y0 + 3, cause ? BookTokens.WHITE : BookTokens.INK);
    }

    private static void arrow(Pen p, int x0, int x1, int y) {
        p.stroke(x0, x1, y, BookTokens.INK);
        p.fill(x1 - 1, y - 1, x1, y + 2, BookTokens.INK);
    }

    private static void mark(Pen p, int row, int x, int y) {
        switch (row) {
            case 0 -> p.fill(x, y, x + 3, y + 3, BookTokens.MARK_RUNNING);
            case 1 -> {
                p.fill(x, y, x + 3, y + 3, BookTokens.MARK_IDLE);
                p.fill(x + 1, y + 1, x + 2, y + 2, BookTokens.WHITE);
            }
            case 2 -> {
                p.fill(x + 1, y, x + 2, y + 1, BookTokens.MARK_CAUTION);
                p.fill(x, y + 1, x + 3, y + 3, BookTokens.MARK_CAUTION);
            }
            default -> p.fill(x, y, x + 3, y + 3, BookTokens.MARK_FAULT);
        }
    }

    private static void plus(Pen p, int x, int y, int col) {
        p.fill(x + 1, y, x + 2, y + 3, col);
        p.fill(x, y + 1, x + 3, y + 2, col);
    }

    private static void ribbon(Pen p) {
        p.fill(81, 1, 88, 15, BookTokens.RIBBON_EDGE);
        p.fill(82, 1, 87, 15, BookTokens.RIBBON);
        p.fill(82, 1, 83, 15, BookTokens.RIBBON_LIT);
        p.fill(82, 15, 84, 18, BookTokens.RIBBON);
        p.fill(85, 15, 87, 18, BookTokens.RIBBON);
    }
}
