package com.createstats.gui.meter.plate;

public final class PlateGeometry {
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

        public boolean overlaps(Rect o) {
            return x < o.right() && o.x < right() && y < o.bottom() && o.y < bottom();
        }

        public Rect glass() {
            return new Rect(x + 2, y + 2, w - 4, h - 4);
        }
    }

    public record Line(String legend, int legendX, Rect window) {
    }

    public static final int MAX_W = 352;
    public static final int MAX_H = 220;
    public static final int MIN_W = 300;
    public static final int MIN_H = 196;

    public static final int TITLE_Y = 8;
    public static final int ICON_Y = 18;
    public static final int NAME_Y = 22;

    public static final int RATE_W = 176;
    public static final int RATE_H = 32;
    public static final int SLOT_H = 7;

    public static final int KEY_W = 22;
    public static final int KEY_H = 16;
    public static final int KEY_PITCH = 19;

    public static final int ROW_H = 13;
    public static final int STUD = 16;

    public static final int LABEL_BAND = 12;

    public final int x;
    public final int y;
    public final int w;
    public final int h;

    public final Rect rate;
    public final int lineY;
    public final int faceY;
    public final Rect slotIn;
    public final Rect slotOut;

    public final Rect terminal;
    public final Rect gland;

    public final Rect jewelIn;
    public final Rect jewelOut;

    public final Rect trace;
    public final Rect[] keys;

    public final int footY;
    public final Line peak;
    public final Line carried;
    public final Line comparator;
    public final Line load;

    public final Rect stud;

    public final Rect studZone;

    public final int bracketX;
    public final int bracketStudY;
    public final int bracketRow1;
    public final int bracketRow2;
    public final int bracketTop;

    private PlateGeometry(int screenW, int screenH) {
        this.w = Math.max(Math.min(MAX_W, screenW - 12), Math.min(MIN_W, screenW - 4));
        this.h = Math.max(Math.min(MAX_H, screenH - 12), Math.min(MIN_H, screenH - 4));
        this.x = (screenW - w) / 2;
        this.y = (screenH - h) / 2;

        this.rate = new Rect((w - RATE_W) / 2, 38, RATE_W, RATE_H);
        this.lineY = rate.y() + RATE_H / 2;
        this.faceY = lineY + 11;
        this.terminal = new Rect(8, lineY - 7, 14, 14);
        this.gland = new Rect(w - 24, lineY - 7, 16, 14);
        this.slotIn = new Rect(23, lineY - 3, rate.x() - 1 - 23, SLOT_H);
        final int outX = rate.right() + 1;
        this.slotOut = new Rect(outX, lineY - 3, (w - 25) - outX, SLOT_H);
        this.jewelIn = new Rect(terminal.x() + 16, lineY - 11, 5, 5);
        this.jewelOut = new Rect(gland.x() - 7, lineY - 11, 5, 5);

        this.footY = h - 50;
        this.trace = new Rect(40, 79, w - 48, footY - 8 - 79);
        this.keys = new Rect[3];
        for (int i = 0; i < 3; i++) {
            keys[i] = new Rect(14, trace.y() + 2 + i * KEY_PITCH, KEY_W, KEY_H);
        }

        final int row1 = footY + 2;
        final int row2 = footY + 19;
        final int leg1X = 49;
        final int leg1W = 47;
        final int w1 = Math.max(56, Math.min(74, Math.round((w - 185) / 2.0f)));
        final int win1X = leg1X + leg1W;
        final int leg2X = win1X + w1 + 8;
        final int win2X = leg2X + 69;
        final int w2 = w - 12 - win2X;
        this.peak = new Line("PEAK", leg1X, new Rect(win1X, row1, w1, ROW_H));
        this.carried = new Line("CARRIED", leg1X, new Rect(win1X, row2, w1, ROW_H));
        this.comparator = new Line("COMPARATOR", leg2X, new Rect(win2X, row1, w2, ROW_H));
        this.load = new Line("LOAD BUFFER", leg2X, new Rect(win2X, row2, w2, ROW_H));

        this.stud = new Rect(17, footY + 9, STUD, STUD);
        this.studZone = new Rect(11, footY - 1, 30, 36);
        this.bracketX = 43;
        this.bracketStudY = stud.y() + STUD / 2;
        this.bracketRow1 = row1 + ROW_H / 2;
        this.bracketRow2 = row2 + ROW_H / 2;
        this.bracketTop = trace.bottom();
    }

    public static PlateGeometry of(int screenW, int screenH) {
        return new PlateGeometry(screenW, screenH);
    }

    public int plotHeight() {
        return trace.glass().h() - LABEL_BAND - 2;
    }
}
