package com.createstats.book.layout;

import com.createstats.book.BookTokens;
import com.createstats.book.content.Section;

import java.util.List;
import java.util.Locale;

public final class BookPainter {
    public record Turn(int from, int to, float progress) {
        public boolean forward() {
            return to > from;
        }
    }

    private BookPainter() {
    }

    public static void paint(Ctx x, List<Page> pages, int at, Turn turn, int ribbonPage) {
        final BookGeometry geo = x.geo;
        final BookCanvas c = x.c;
        final int shown = turn == null ? at : turn.progress() < 0.5f ? turn.from() : turn.to();
        final int open = shown >= 0 && shown < pages.size() ? pages.get(shown).section().ordinal() : 0;

        binder(c, geo);
        tabs(x, open);
        if (turn == null) {
            for (int slot = 0; slot < geo.perView(); slot++) {
                sheet(x, pages, at + slot, slot);
            }
        } else if (geo.spread()) {
            spreadTurn(x, pages, turn);
        } else {
            singleTurn(x, pages, turn);
        }
        rings(c, geo);
        if (turn == null && ribbonPage >= at && ribbonPage < at + geo.perView()) {
            ribbon(x, ribbonAt(x, pages, at, ribbonPage - at));
        }
    }

    public record Ribbon(int slot, Rect rect) {
    }

    public static Ribbon ribbonAt(Ctx x, List<Page> pages, int at, int slot) {
        final Rect own = longRibbon(x, pages.get(at + slot), slot);
        if (own != null) {
            return new Ribbon(slot, own);
        }
        if (x.geo.spread()) {
            final int other = 1 - slot;
            if (at + other < pages.size()) {
                final Rect facing = longRibbon(x, pages.get(at + other), other);
                if (facing != null) {
                    return new Ribbon(other, facing);
                }
            }
        }
        return new Ribbon(slot, shortRibbon(x, pages.get(at + slot), slot));
    }

    private static void binder(BookCanvas c, BookGeometry geo) {
        final Rect b = geo.binder;
        c.fill(b.x(), b.y(), b.right(), b.bottom(), BookTokens.VINYL_OUTLINE);
        c.vinyl(b.x() + 1, b.y() + 1, b.right() - 1, b.bottom() - 1);
        c.fill(b.x() + 1, b.y() + 1, b.right() - 1, b.y() + 2, BookTokens.VINYL_LIT);
        c.fill(b.x() + 1, b.y() + 1, b.x() + 2, b.bottom() - 1, BookTokens.VINYL_LIT);
    }

    private static void tabs(Ctx x, int open) {
        final BookCanvas c = x.c;
        final BookGeometry geo = x.geo;
        for (final Section s : Section.values()) {
            final int i = s.ordinal();
            final Rect t = geo.tab(i, open);
            final String id = "tab:" + i;
            final int col = x.lit(id) ? BookTokens.lerp(s.colour(), BookTokens.WHITE, 0.14f) : s.colour();
            c.fill(t.x(), t.y(), t.right(), t.bottom(), col);
            c.fill(t.x(), t.y(), t.right(), t.y() + 1, BookTokens.tabLit(col));
            c.fill(t.x(), t.bottom() - 1, t.right(), t.bottom(), BookTokens.tabShade(col));

            final Rect v = geo.tabVisible(i, open);
            final int cx = v.x() + v.w() / 2;
            if (t.h() >= BookTokens.TAB_NUMBERED_MIN_H) {
                final boolean tall = t.h() >= 50;
                c.textCentre(String.valueOf(s.number()), cx + 1, t.y() + (tall ? 5 : 3), BookTokens.WHITE, true);
                final int wy = t.y() + (tall ? 18 : 13);
                c.fill(cx - 8, wy, cx + 8, wy + 16, BookTokens.well(s.colour()));
                c.item(s.icon(), cx - 8, wy);
            } else {
                final int wy = t.y() + (t.h() - 16) / 2;
                c.fill(cx - 8, wy, cx + 8, wy + 16, BookTokens.well(s.colour()));
                c.item(s.icon(), cx - 8, wy);
            }
            c.hot(id, v.x(), v.y(), v.right(), v.bottom());
        }
    }

    public static void paintPage(Ctx x, List<Page> pages, int index, int slot) {
        sheet(x, pages, index, slot);
    }

    private static void sheet(Ctx x, List<Page> pages, int index, int slot) {
        final BookCanvas c = x.c;
        final BookGeometry geo = x.geo;
        final Rect s = geo.sheets[slot];
        c.fill(s.x(), s.y(), s.right(), s.bottom(), BookTokens.PAPER_EDGE);
        c.paper(s.x(), s.y(), s.right() - 1, s.bottom() - 1);
        final int[] curl = BookTokens.CURL_ALPHA;
        final int step = BookTokens.CURL_STEP;
        if (geo.spread()) {
            for (int i = 0; i < curl.length; i++) {
                if (slot == 0) {
                    c.fill(s.right() - step * (i + 1), s.y(), s.right() - step * i, s.bottom(), curl[i] << 24);
                } else {
                    c.fill(s.x() + step * i, s.y(), s.x() + step * (i + 1), s.bottom(), curl[i] << 24);
                }
            }
        }
        for (final int ry : geo.ringY) {
            final int hx = geo.spread() && slot == 0 ? s.right() - 12 : s.x() + 4;
            c.fill(hx, ry - 3, hx + 8, ry + 5, BookTokens.HOLE_RIM);
            c.fill(hx + 1, ry - 2, hx + 7, ry + 4, BookTokens.HOLE);
        }
        if (index < 0 || index >= pages.size()) {
            return;
        }
        printed(x, pages, pages.get(index), index, slot);
    }

    private static void printed(Ctx x, List<Page> pages, Page page, int index, int slot) {
        final BookCanvas c = x.c;
        final BookGeometry geo = x.geo;
        final Rect s = geo.sheets[slot];
        final Rect content = geo.content(slot);
        x.section = page.section();
        x.sheet = page.sheet();
        final int col = page.section().colour();

        c.fill(s.x(), s.y(), s.right() - 1, s.y() + BookTokens.BAND_H, col);
        final String[] band = band(x, page);
        c.text(band[0], content.x(), s.y() + 4, BookTokens.WHITE, true);
        c.textRight(band[1], content.right(), s.y() + 4, BookTokens.WHITE, true);

        x.contentTop = content.y();
        for (final Page.Placed p : page.blocks()) {
            p.block().paint(x, content.x(), content.y() + p.top(), content.w());
        }

        final int fy = s.bottom() - BookTokens.FOOTER_H;
        c.hline(content.x(), content.right(), fy, BookTokens.RULE);
        final String foot = x.t(geo.spread() && slot == 1 ? "create_stats.book.footer.mod" : "create_stats.book.footer.book");
        c.text(foot, content.x(), fy + 4, BookTokens.MUTE, false);
        c.textRight(page.label(), content.right(), fy + 4, BookTokens.INK, true);

        final boolean back = geo.spread() ? slot == 0 && index > 0 : index > 0;
        final boolean on = geo.spread() ? slot == 1 && index + 1 < pages.size() : index + 1 < pages.size();
        final int mid = (content.x() + content.right()) / 2;
        if (back) {
            final int ax = geo.spread() ? mid - 11 : mid - 30;
            arrow(x, "nav:prev", "widget/page_backward", ax, fy + 2);
        }
        if (on) {
            final int ax = geo.spread() ? mid - 11 : mid + 8;
            arrow(x, "nav:next", "widget/page_forward", ax, fy + 2);
        }
    }

    private static void arrow(Ctx x, String id, String sprite, int ax, int ay) {
        x.c.sprite(x.lit(id) ? sprite + "_highlighted" : sprite, ax, ay, 23, 13);
        x.c.hot(id, ax, ay, ax + 23, ay + 13);
    }

    static String[] band(Ctx x, Page page) {
        final String section = x.t(page.section().nameKey());
        final String head = page.section().number() + "  " + section;
        String left;
        String right;
        switch (page.kind()) {
            case CONTENTS -> {
                left = page.index() == 0 ? head : x.t(page.bandKey() != null ? page.bandKey()
                        : "create_stats.book.kind.contents_continued");
                right = page.index() == 0 ? x.t("create_stats.book.kind.contents") : section;
            }
            case DATA -> {
                final boolean block = page.sheet().coversBlocks();
                final String title = x.t(page.sheet().titleKey()).toUpperCase(Locale.ROOT);
                if (IndexSearch.isIndex(page.sheet())) {
                    left = page.index() == 0 ? head : IndexPage.bandTitle(x);
                    right = page.index() == 0 ? x.t("create_stats.book.kind.index") : IndexPage.bandCount(x);
                } else if (page.index() == 0) {
                    left = head;
                    right = block ? x.t("create_stats.book.kind.data_sheet") : title;
                } else {
                    left = page.bandKey() != null ? x.t(page.bandKey(), page.bandArgsOrNone())
                            : block ? x.t("create_stats.book.kind.data_sheet_continued")
                            : x.t("create_stats.book.continued", title);
                    right = block ? title : section;
                }
            }
            default -> {
                left = x.t("create_stats.book.part.notes");
                right = section;
            }
        }
        if (page.kind() != Page.Kind.NOTES && page.count() > x.geo.perView()) {
            right = right + "  " + x.t("create_stats.book.band.of", page.index() + 1, page.count());
        }
        return fit(x, left, right);
    }

    private static final int BAND_GAP = 30;

    static String[] fit(Ctx x, String left, String right) {
        final int room = x.geo.content(0).w() - BAND_GAP;
        if (x.c.width(left, true) + x.c.width(right, true) <= room) {
            return new String[]{left, right};
        }
        final int count = right.lastIndexOf("  ");
        final String title = count > 0 ? right.substring(0, count) : right;
        final String tail = count > 0 ? right.substring(count) : "";
        final int comma = title.indexOf(',');
        final String shortTitle = comma > 0 ? title.substring(0, comma) : title;
        for (final String trial : new String[]{shortTitle + tail, shortTitle}) {
            if (x.c.width(left, true) + x.c.width(trial, true) <= room) {
                return new String[]{left, trial};
            }
        }
        String cut = shortTitle;
        while (cut.length() > 1 && x.c.width(left, true) + x.c.width(cut, true) > room) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return new String[]{left, cut.strip()};
    }

    private static void spreadTurn(Ctx x, List<Page> pages, Turn t) {
        final BookGeometry geo = x.geo;
        final float p = ease(t.progress());
        if (t.forward()) {
            sheet(x, pages, t.from(), 0);
            sheet(x, pages, t.to() + 1, 1);
            if (p < 0.5f) {
                leaf(x, pages, t.from() + 1, 1, geo.sheets[1].x(), 1.0f - 2.0f * p);
            } else {
                leaf(x, pages, t.to(), 0, geo.sheets[0].right(), 2.0f * p - 1.0f);
            }
        } else {
            sheet(x, pages, t.to(), 0);
            sheet(x, pages, t.from() + 1, 1);
            if (p < 0.5f) {
                leaf(x, pages, t.from(), 0, geo.sheets[0].right(), 1.0f - 2.0f * p);
            } else {
                leaf(x, pages, t.to() + 1, 1, geo.sheets[1].x(), 2.0f * p - 1.0f);
            }
        }
    }

    private static void singleTurn(Ctx x, List<Page> pages, Turn t) {
        final float p = ease(t.progress());
        final int pivot = x.geo.sheets[0].x();
        if (t.forward()) {
            sheet(x, pages, t.to(), 0);
            leaf(x, pages, t.from(), 0, pivot, 1.0f - p);
        } else {
            sheet(x, pages, t.from(), 0);
            leaf(x, pages, t.to(), 0, pivot, p);
        }
    }

    private static void leaf(Ctx x, List<Page> pages, int index, int slot, int pivot, float scaleX) {
        if (scaleX <= 0.02f) {
            return;
        }
        final BookCanvas c = x.c;
        final Rect s = x.geo.sheets[slot];
        c.pushFlip(pivot, scaleX);
        sheet(x, pages, index, slot);
        c.fill(s.x(), s.y(), s.right(), s.bottom(), Math.round((1.0f - scaleX) * 0x48) << 24);
        c.popFlip();
    }

    static float ease(float t) {
        final float k = Math.max(0.0f, Math.min(1.0f, t));
        return k * k * (3.0f - 2.0f * k);
    }

    private static void rings(BookCanvas c, BookGeometry geo) {
        final Rect p = geo.plate;
        c.fill(p.x(), p.y(), p.right(), p.bottom(), BookTokens.PLATE);
        c.fill(p.x() + 1, p.y(), p.x() + 2, p.bottom(), BookTokens.PLATE_LIGHT);
        c.fill(p.right() - 2, p.y(), p.right() - 1, p.bottom(), BookTokens.PLATE_DARK);
        for (final int ry : geo.ringY) {
            final int x0;
            final int x1;
            if (geo.spread()) {
                x0 = geo.sheets[0].right() - 9;
                x1 = geo.sheets[1].x() + 9;
            } else {
                x0 = p.x() + 4;
                x1 = geo.sheets[0].x() + 9;
            }
            c.fill(x0, ry - 1, x1, ry + 3, BookTokens.RING);
            c.fill(x0, ry - 1, x1, ry, BookTokens.RING_LIT);
            c.fill(x0, ry + 2, x1, ry + 3, BookTokens.RING_SHADE);
        }
    }

    // the ribbon must never cover text. fussy, yes
    private static Rect longRibbon(Ctx x, Page page, int slot) {
        final BookCanvas c = x.c;
        final BookGeometry geo = x.geo;
        final Rect s = geo.sheets[slot];
        final Rect content = geo.content(slot);
        final String[] band = band(x, page);
        final int leftEnd = content.x() + c.width(band[0], true) + 8;
        final int rightStart = content.right() - c.width(band[1], true) - 8;
        final int rx = preferredRibbonX(leftEnd, rightStart);
        final int top = geo.binder.y() + 3;
        final int longFoot = s.y() + BookTokens.BAND_H + 12;
        final InkRecorder ink = topInk(x, page, slot, longFoot + RIBBON_FORK);
        if (!ink.covers(clearance(tail(rx, s, longFoot)))) {
            return new Rect(rx - 1, top, 12, longFoot + RIBBON_FORK - top);
        }
        for (int d = 1; d < content.w(); d++) {
            boolean inGap = false;
            for (final int cand : new int[]{rx - d, rx + d}) {
                if (cand - 1 >= leftEnd && cand + 11 <= rightStart) {
                    inGap = true;
                    if (!ink.covers(clearance(tail(cand, s, longFoot)))) {
                        return new Rect(cand - 1, top, 12, longFoot + RIBBON_FORK - top);
                    }
                }
            }
            if (!inGap) {
                break;
            }
        }
        return null;
    }

    private static Rect shortRibbon(Ctx x, Page page, int slot) {
        final BookCanvas c = x.c;
        final Rect content = x.geo.content(slot);
        final String[] band = band(x, page);
        final int rx = preferredRibbonX(content.x() + c.width(band[0], true) + 8,
                content.right() - c.width(band[1], true) - 8);
        final int top = x.geo.binder.y() + 3;
        final int shortFoot = content.y() - 2 - RIBBON_FORK;
        return new Rect(rx - 1, top, 12, shortFoot + RIBBON_FORK - top);
    }

    private static int preferredRibbonX(int leftEnd, int rightStart) {
        final int rx = rightStart - 22;
        return rx < leftEnd ? (leftEnd + rightStart) / 2 - 5 : rx;
    }

    private static final int RIBBON_FORK = 5;

    private static final int RIBBON_CLEAR = 4;

    private static Rect clearance(Rect tail) {
        return new Rect(tail.x() - RIBBON_CLEAR, tail.y(), tail.w() + 2 * RIBBON_CLEAR, tail.h());
    }

    private static Rect tail(int rx, Rect sheet, int foot) {
        final int y0 = sheet.y() + BookTokens.BAND_H;
        return new Rect(rx - 1, y0, 12, foot + RIBBON_FORK - y0);
    }

    private static InkRecorder topInk(Ctx x, Page page, int slot, int bottom) {
        final InkRecorder ink = new InkRecorder(x.c);
        final Ctx r = x.with(ink);
        final Rect content = x.geo.content(slot);
        r.section = page.section();
        r.sheet = page.sheet();
        r.contentTop = content.y();
        for (final Page.Placed p : page.blocks()) {
            if (content.y() + p.top() < bottom) {
                p.block().paint(r, content.x(), content.y() + p.top(), content.w());
            }
        }
        return ink;
    }

    private static void ribbon(Ctx x, Ribbon where) {
        final BookCanvas c = x.c;
        final Rect r = where.rect();
        final int rx = r.x() + 1;
        final int top = r.y();
        final int foot = r.bottom() - RIBBON_FORK;
        c.raise(2);
        c.fill(rx - 1, top, rx + 11, foot, BookTokens.RIBBON_EDGE);
        c.fill(rx, top, rx + 10, foot, BookTokens.RIBBON);
        c.fill(rx, top, rx + 2, foot, BookTokens.RIBBON_LIT);

        for (int i = 0; i < 5; i++) {
            c.fill(rx, foot + i, rx + 4 - i / 2, foot + 1 + i, BookTokens.RIBBON);
            c.fill(rx + 6 + i / 2, foot + i, rx + 10, foot + 1 + i, BookTokens.RIBBON);
        }
        c.fill(rx, foot, rx + 2, foot + 5, BookTokens.RIBBON_LIT);
        c.raise(0);
    }
}
