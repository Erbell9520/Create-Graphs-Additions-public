package com.createstats.book.layout;

import com.createstats.book.BookTokens;

// spread or single, picked from the canvas size alone
public final class BookGeometry {
    public enum Mode {
        SPREAD, SINGLE
    }

    public static final int TABS = 8;

    public final Mode mode;
    public final int width;
    public final int height;

    public final int guiScale;

    public final Rect binder;

    public final Rect[] sheets;

    public final Rect plate;
    public final int[] ringY;

    public final int tabTop;
    public final int tabPitch;
    public final int tabH;

    private BookGeometry(Mode mode, int width, int height, int guiScale, Rect binder, Rect[] sheets, Rect plate,
                         int[] ringY, int tabTop, int tabPitch, int tabH) {
        this.mode = mode;
        this.width = width;
        this.height = height;
        this.guiScale = guiScale;
        this.binder = binder;
        this.sheets = sheets;
        this.plate = plate;
        this.ringY = ringY;
        this.tabTop = tabTop;
        this.tabPitch = tabPitch;
        this.tabH = tabH;
    }

    public static BookGeometry of(int width, int height, int guiScale) {
        final int m = BookTokens.CANVAS_MARGIN;
        final int spreadW = Math.min(BookTokens.SPREAD_SHEET_MAX_W,
                (width - 2 * m - 2 * BookTokens.BINDER_SIDE - BookTokens.GUTTER) / 2);
        final int sheetH = Math.min(BookTokens.SHEET_MAX_H,
                height - 2 * m - BookTokens.BINDER_TOP - BookTokens.BINDER_BOTTOM);
        if (spreadW >= BookTokens.SPREAD_SHEET_MIN_W && sheetH >= BookTokens.SPREAD_SHEET_MIN_H) {
            return spread(width, height, guiScale, spreadW, sheetH);
        }
        return single(width, height, guiScale);
    }

    private static BookGeometry spread(int width, int height, int guiScale, int sheetW, int sheetH) {
        final int bw = 2 * sheetW + BookTokens.GUTTER + 2 * BookTokens.BINDER_SIDE;
        final int bh = sheetH + BookTokens.BINDER_TOP + BookTokens.BINDER_BOTTOM;
        final int bx = (width - bw) / 2;
        final int by = (height - bh) / 2;
        final Rect binder = new Rect(bx, by, bw, bh);
        final int top = by + BookTokens.BINDER_TOP;
        final Rect left = new Rect(bx + BookTokens.BINDER_SIDE, top, sheetW, sheetH);
        final Rect right = new Rect(left.right() + BookTokens.GUTTER, top, sheetW, sheetH);
        final Rect plate = new Rect(left.right(), top + 16, BookTokens.GUTTER, sheetH - 32);
        final int[] rings = {top + 64, top + sheetH / 2, top + sheetH - 64};
        final int pitch = Math.min(BookTokens.TAB_PITCH, (sheetH - 8) / TABS);
        return new BookGeometry(Mode.SPREAD, width, height, guiScale, binder, new Rect[]{left, right}, plate,
                rings, top + 4, pitch, pitch - 4);
    }

    private static BookGeometry single(int width, int height, int guiScale) {
        final int m = 10;
        final int sheetW = Math.max(160, Math.min(BookTokens.SINGLE_SHEET_MAX_W,
                width - 2 * m - BookTokens.SINGLE_RING_ZONE - BookTokens.BINDER_SIDE));
        final int sheetH = Math.max(120, Math.min(BookTokens.SHEET_MAX_H, height - 2 * m - 12));
        final int bw = sheetW + BookTokens.SINGLE_RING_ZONE + BookTokens.BINDER_SIDE;
        final int bh = sheetH + 12;
        final int bx = (width - bw) / 2;
        final int by = (height - bh) / 2;
        final Rect binder = new Rect(bx, by, bw, bh);
        final Rect sheet = new Rect(bx + BookTokens.SINGLE_RING_ZONE, by + 6, sheetW, sheetH);
        final Rect plate = new Rect(bx + 8, sheet.y() + 14, 22, sheetH - 28);
        final int inset = Math.min(54, sheetH / 5);
        final int[] rings = {sheet.y() + inset, sheet.y() + sheetH / 2, sheet.bottom() - inset};
        final int pitch = Math.min(BookTokens.TAB_PITCH, (sheetH - 4) / TABS);
        return new BookGeometry(Mode.SINGLE, width, height, guiScale, binder, new Rect[]{sheet}, plate, rings,
                sheet.y() + 2, pitch, pitch - 3);
    }

    public boolean spread() {
        return mode == Mode.SPREAD;
    }

    public int perView() {
        return sheets.length;
    }

    public Rect content(int sheet) {
        final Rect s = sheets[sheet];
        final int top = BookTokens.BAND_H + BookTokens.PAD_TOP;
        final int bottom = BookTokens.FOOTER_H + BookTokens.PAD_BOTTOM;
        if (mode == Mode.SINGLE) {
            return s.inset(BookTokens.PAD_PUNCHED, top, BookTokens.PAD_SINGLE_OPEN, bottom);
        }
        return sheet == 0
                ? s.inset(BookTokens.PAD_OPEN, top, BookTokens.PAD_CURL, bottom)
                : s.inset(BookTokens.PAD_CURL, top, BookTokens.PAD_OPEN, bottom);
    }

    public int bandLeft(int sheet) {
        return content(sheet).x();
    }

    public int bandRight(int sheet) {
        return content(sheet).right();
    }

    public Rect tab(int section, int open) {
        final int y = tabTop + section * tabPitch;
        if (mode == Mode.SINGLE) {
            final Rect s = sheets[0];
            final int x = s.right() - 2 + (section == open ? BookTokens.TAB_PULL : 0);
            return new Rect(x, y, BookTokens.TAB_W, tabH);
        }
        if (section <= open) {
            return new Rect(sheets[0].x() - BookTokens.TAB_W, y, BookTokens.TAB_W, tabH);
        }
        return new Rect(sheets[1].right(), y, BookTokens.TAB_W, tabH);
    }

    public Rect tabVisible(int section, int open) {
        final Rect t = tab(section, open);
        if (mode == Mode.SINGLE) {
            final int x0 = sheets[0].right();
            return new Rect(x0, t.y(), t.right() - x0, t.h());
        }
        return t;
    }
}
