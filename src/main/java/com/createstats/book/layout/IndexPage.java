package com.createstats.book.layout;

import com.createstats.book.BookTokens;
import com.createstats.book.content.BackMatter;
import com.createstats.book.content.Book;
import com.createstats.book.content.FaultIndex;
import com.createstats.book.content.Section;
import com.createstats.book.content.Sheet;
import com.createstats.book.content.Symptom;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class IndexPage {
    private IndexPage() {
    }

    public static final String SEARCH = "search";

    public static final String RESULTS = "results";

    private static final int FIELD_H = 18;
    private static final int SYMPTOM_PITCH = 18;
    private static final int SYMPTOM_PITCH_COMPACT = 15;
    private static final int CHIP_H = 20;
    private static final int CHIP_PITCH = 24;
    private static final int PAGER_H = 16;

    private static final int COLUMN_MIN = 160;

    private static final int CAUSE_MIN = 150;

    static List<Block> blocks(Ctx x) {
        if (x.geo.spread()) {
            return List.of(new Query(), new SheetBlocks.Break(null), new Results());
        }
        return List.of(new Single());
    }

    public static String bandTitle(Ctx x) {
        final IndexState s = x.index;
        if (s.symptom() != null) {
            return x.t(s.symptom().nameKey()).toUpperCase(Locale.ROOT);
        }
        if (!s.query().isBlank()) {
            return x.t("create_stats.book.index.band.found");
        }
        return x.t("create_stats.book.index.band.all");
    }

    public static String bandCount(Ctx x) {
        final int n = x.results().total();
        return x.t(n == 1 ? "create_stats.book.index.band.entry" : "create_stats.book.index.band.entries", n);
    }

    static final class Query extends Block {
        @Override
        public int height(Ctx x, int w) {
            return x.pageH;
        }

        @Override
        public void paint(Ctx x, int left, int top, int w) {
            final int bottom = top + x.pageH;
            int y = head(x, left, top, w, true);
            field(x, left, y, w);
            y += FIELD_H + 12;
            y = symptoms(x, left, y, w, false);
            y += 8;
            y = sections(x, left, y, w, false, bottom);
            y += 10;
            y = note(x, left, y, w, bottom);
            back(x, left, y + 12, w, bottom);
        }
    }

    static final class Results extends Block {
        @Override
        public int height(Ctx x, int w) {
            return x.pageH;
        }

        @Override
        public void paint(Ctx x, int left, int top, int w) {
            results(x, left, top + 4, w, top + x.pageH, false);
        }
    }

    static final class Single extends Block {
        @Override
        public int height(Ctx x, int w) {
            return x.pageH;
        }

        @Override
        public void paint(Ctx x, int left, int top, int w) {
            final int bottom = top + x.pageH;
            int y = head(x, left, top, w, x.wide);
            field(x, left, y, w);
            y += FIELD_H + 10;
            final int colW = columnWidth(x, w);
            int ly = symptoms(x, left, y, colW, !x.wide);
            ly += 6;
            sections(x, left, ly, colW, true, bottom);
            final int rx = left + colW + 12;
            x.c.vline(rx - 6, y, bottom - 2, BookTokens.ROW_RULE);
            results(x, rx, y, left + w - rx, bottom, true);
        }
    }

    private static int columnWidth(Ctx x, int w) {
        int max = 0;
        for (final Symptom s : Symptom.values()) {
            max = Math.max(max, x.c.width(x.t(s.nameKey()), true));
        }
        return Math.max(COLUMN_MIN, Math.min(max + 30, w * 2 / 5));
    }

    private static int head(Ctx x, int left, int top, int w, boolean big) {
        final BookCanvas c = x.c;
        final String title = x.t("create_stats.book.index.head").toUpperCase(Locale.ROOT);
        final boolean twice = big && (c.width(title, false) + 1) * 2 <= w;
        if (twice) {
            c.textScaled(title, left, top, BookTokens.INK, 2);
        } else {
            c.text(title, left, top + 4, BookTokens.INK, true);
        }
        int y = top + 22;
        for (final String line : c.wrap(x.t(BackMatter.INDEX.hookKey()), w)) {
            c.text(line, left, y, BookTokens.MUTE, false);
            y += BookTokens.LEAD;
        }
        return y + 4;
    }

    private static void field(Ctx x, int left, int top, int w) {
        final BookCanvas c = x.c;
        final IndexState s = x.index;
        final boolean lit = x.lit(SEARCH) || s.focused();
        c.fill(left, top, left + w, top + FIELD_H, lit ? BookTokens.INK : BookTokens.RULE);
        c.fill(left + 1, top + 1, left + w - 1, top + FIELD_H - 1, BookTokens.WHITE);
        if (s.focused()) {
            c.fill(left + 1, top + FIELD_H - 2, left + w - 1, top + FIELD_H - 1, Section.FAULTS.colour());
        }
        String found = "";
        if (!s.query().isBlank()) {
            found = x.t("create_stats.book.index.found", x.results().total());
        }
        final int fw = found.isEmpty() ? 0 : c.width(found, false) + 8;
        final int tx = left + 6;
        final int area = w - 12 - fw;
        if (!found.isEmpty()) {
            c.textRight(found, left + w - 6, top + 5, BookTokens.MUTE, false);
        }

        final int start = firstShown(c, s.query(), area);
        if (s.query().isEmpty()) {
            if (!s.focused()) {
                c.text(x.t("create_stats.book.index.placeholder"), tx, top + 5, BookTokens.MUTE, false);
            }
        } else {
            final int a = Math.max(start, Math.min(s.cursor(), s.anchor()));
            final int b = Math.max(start, Math.max(s.cursor(), s.anchor()));
            if (b > a) {
                final int x0 = tx + c.width(s.query().substring(start, a), false);
                final int x1 = tx + c.width(s.query().substring(start, b), false);
                c.fill(x0, top + 3, x1 + 1, top + FIELD_H - 3,
                        BookTokens.alpha(Section.FAULTS.colour(), 2 * BookTokens.ROW_TINT_ALPHA));
            }
            c.text(s.query().substring(start), tx, top + 5, BookTokens.INK, false);
        }
        if (s.focused() && s.caret()) {
            final int cx = tx + c.width(s.query().substring(start, Math.max(start, s.cursor())), false);
            c.fill(cx, top + 4, cx + 1, top + FIELD_H - 4, BookTokens.INK);
        }
        c.hot(SEARCH, tx, top, tx + Math.max(8, area), top + FIELD_H);
    }

    private static int firstShown(BookCanvas c, String q, int area) {
        int start = 0;
        while (start < q.length() && c.width(q.substring(start), false) > area) {
            start++;
        }
        return start;
    }

    private static int symptoms(Ctx x, int left, int top, int w, boolean compact) {
        final BookCanvas c = x.c;
        Ink.tableHeading(c, left, top, w, x.t("create_stats.book.index.symptoms"), null);
        final int pitch = compact ? SYMPTOM_PITCH_COMPACT : SYMPTOM_PITCH;
        int y = top + Ink.TABLE_HEADING + 3;
        final IndexSearch.Result r = x.results();
        final int ink = Section.FAULTS.colour();
        for (final Symptom s : Symptom.values()) {
            final String id = "sym:" + s.name();
            final boolean picked = s == x.index.symptom();
            final int n = r.count(s);
            final String count = String.valueOf(n);
            if (picked) {
                c.fill(left - 2, y - 2, left + w + 2, y + pitch - 4, ink);
            } else if (x.lit(id)) {
                c.fill(left - 2, y - 2, left + w + 2, y + pitch - 4, BookTokens.alpha(ink, BookTokens.ROW_TINT_ALPHA));
            }
            final int col = picked ? BookTokens.WHITE : n == 0 ? BookTokens.MUTE : BookTokens.INK;
            final String name = x.t(s.nameKey());

            final boolean bold = picked && c.width(name, true) + c.width(count, true) + 14 <= w;
            c.text(name, left + 4, y + 2, col, bold);
            c.textRight(count, left + w - 4, y + 2, col, true);
            if (!picked) {
                c.hline(left, left + w, y + pitch - 3, BookTokens.ROW_RULE);
            }
            c.hot(id, left - 2, y - 2, left + w + 2, y + pitch - 4);
            y += pitch;
        }
        return y;
    }

    private static int sections(Ctx x, int left, int top, int w, boolean iconsOnly, int bottom) {
        final BookCanvas c = x.c;
        final boolean headed = !iconsOnly;
        final int first = top + (headed ? Ink.TABLE_HEADING + 4 : 0);
        if (first + CHIP_H > bottom) {
            return top;
        }
        if (headed) {
            Ink.tableHeading(c, left, top, w, x.t("create_stats.book.index.by_section"), null);
        }
        int y = first;
        int cx = left;
        final IndexSearch.Result r = x.results();
        for (final Section s : Section.values()) {
            if (s.creativeKey() == null) {
                continue;
            }
            final String name = x.t(s.nameKey());
            final int cw = iconsOnly ? CHIP_H : 22 + c.width(name, true) + 6;
            if (cx + cw > left + w && cx > left) {
                cx = left;
                y += CHIP_PITCH;
            }
            if (y + CHIP_H > bottom) {
                break;
            }
            final boolean indexed = r.indexed().contains(s);
            final boolean picked = s == x.index.section();
            final boolean pale = !indexed || (x.index.section() != null && !picked);
            final String id = "sec:" + s.name();
            final int col = s.colour();
            if (picked) {
                c.outline(cx - 2, y - 2, cx + cw + 2, y + CHIP_H + 2, BookTokens.INK);
            }
            if (pale) {
                c.fill(cx, y, cx + cw, y + CHIP_H, BookTokens.lerp(col, BookTokens.PAPER, 0.55f));
                c.fill(cx + 1, y + 1, cx + cw - 1, y + CHIP_H - 1, BookTokens.lerp(col, BookTokens.PAPER, 0.84f));
            } else {
                c.fill(cx, y, cx + cw, y + CHIP_H, x.lit(id) ? BookTokens.lerp(col, BookTokens.WHITE, 0.14f) : col);
            }
            c.fill(cx + 2, y + 2, cx + 18, y + 18, BookTokens.well(col));
            c.item(s.icon(), cx + 2, y + 2);
            if (!iconsOnly) {
                c.text(name, cx + 22, y + 6, pale ? col : BookTokens.WHITE, true);
            }
            if (indexed) {
                c.hot(id, cx, y, cx + cw, y + CHIP_H);
            } else if (iconsOnly) {
                c.hot("secname:" + s.name(), cx, y, cx + cw, y + CHIP_H);
            }
            cx += cw + 6;
        }
        return y + CHIP_H;
    }

    private static int note(Ctx x, int left, int top, int w, int bottom) {
        int y = top;
        final List<String> lines = x.c.wrap(x.t("create_stats.book.index.note"), w);
        if (top + lines.size() * BookTokens.LEAD > bottom) {
            return top;
        }
        for (final String line : lines) {
            x.c.text(line, left, y, BookTokens.MUTE, false);
            y += BookTokens.LEAD;
        }
        return y;
    }

    private static void back(Ctx x, int left, int top, int w, int bottom) {
        final BookCanvas c = x.c;
        final List<Sheet> sheets = new ArrayList<>();
        for (final Sheet s : Book.in(Section.FAULTS)) {
            if (!s.id().equals(BackMatter.INDEX_ID) && x.firstPage.containsKey(s.id())) {
                sheets.add(s);
            }
        }
        if (sheets.isEmpty() || top + Ink.TABLE_HEADING + 4 + CHIP_H > bottom) {
            return;
        }
        Ink.tableHeading(c, left, top, w, x.t("create_stats.book.index.also"), null);
        int y = top + Ink.TABLE_HEADING + 4;
        int cx = left;
        for (final Sheet s : sheets) {
            final String title = x.t(s.titleKey());
            final String page = x.firstPage.get(s.id());
            final int cw = 6 + c.width(title, false) + 8 + c.width(page, true) + 6;
            if (cx + cw > left + w && cx > left) {
                cx = left;
                y += CHIP_PITCH;
            }
            if (y + CHIP_H > bottom) {
                return;
            }
            final String id = "toc:" + s.id();
            c.fill(cx, y, cx + cw, y + CHIP_H, x.lit(id) ? Section.FAULTS.colour() : BookTokens.RULE);
            c.fill(cx + 1, y + 1, cx + cw - 1, y + CHIP_H - 1, BookTokens.WHITE);
            c.text(title, cx + 6, y + 6, BookTokens.INK, false);
            c.textRight(page, cx + cw - 6, y + 6, Section.FAULTS.colour(), true);
            c.hot(id, cx, y, cx + cw, y + CHIP_H);
            cx += cw + 6;
        }
    }

    private record Item(String heading, FaultIndex.Entry entry, int entryIndex, Sheet sheet, int ordinal) {
    }

    private static List<Item> items(Ctx x) {
        final IndexSearch.Result r = x.results();
        final List<Item> out = new ArrayList<>();
        final boolean grouped = x.index.symptom() == null;
        Symptom group = null;
        boolean first = true;
        int n = 0;
        for (int i = 0; i < r.entries().size(); i++) {
            final FaultIndex.Entry e = r.entries().get(i);
            if (grouped && (first || e.symptom() != group)) {
                out.add(new Item(e.symptom() == null ? x.t("create_stats.book.index.not_a_fault")
                        : x.t(e.symptom().nameKey()), null, -1, null, 0));
                group = e.symptom();
                first = false;
            }
            out.add(new Item(null, e, i, null, ++n));
        }
        if (!r.sheets().isEmpty()) {
            out.add(new Item(x.t("create_stats.book.index.sheets"), null, -1, null, 0));
            for (final Sheet s : r.sheets()) {
                out.add(new Item(null, null, -1, s, ++n));
            }
        }
        return out;
    }

    private static final int GROUP_H = 16;
    private static final int SHEET_H = 18;

    private record Layout(String who, int whoLines, int causeX, List<String> cause, List<String> head, int pageW) {
    }

    private static Layout entryLayout(Ctx x, FaultIndex.Entry e, int w) {
        final BookCanvas c = x.c;
        final String page = x.rowPage.getOrDefault(e.target(), "");
        final int pageW = c.width(page, true) + 8;
        final List<String> head = c.wrap(x.t(e.headKey(), e.headArgsOrNone()), w - 12 - pageW - 6, true);
        final String who = x.t(e.sheet().titleKey());
        final int whoW = c.width(who, false);
        final boolean beside = w - (30 + whoW + 8) - pageW >= CAUSE_MIN;
        final int causeX = beside ? 30 + whoW + 8 : 30;
        final int causeW = Math.max(40, w - causeX - (beside ? pageW : 0));
        final List<String> cause = c.wrap(x.t(e.lineKey()), causeW);
        return new Layout(who, beside ? 0 : BookTokens.LEAD, causeX, cause,
                head.isEmpty() ? List.of("") : head, pageW);
    }

    private static int itemHeight(Ctx x, Item it, int w) {
        if (it.heading() != null) {
            return GROUP_H;
        }
        if (it.sheet() != null) {
            return SHEET_H;
        }
        final Layout l = entryLayout(x, it.entry(), w);
        return Math.max(32, 13 + (l.head().size() - 1) * BookTokens.LEAD + l.whoLines()
                + l.cause().size() * BookTokens.LEAD + 2) + 8;
    }

    private static void results(Ctx x, int left, int top, int w, int bottom, boolean titled) {
        final BookCanvas c = x.c;

        c.hot(RESULTS, left - 2, top, left + w + 2, bottom);
        int y = top;
        if (titled) {
            final String title = bandTitle(x);
            final String count = bandCount(x);
            final boolean both = c.width(title, true) + 8 + c.width(count, false) <= w;
            Ink.tableHeading(c, left, y, w, title, both ? count : null);
            y += Ink.TABLE_HEADING + 3;
        }
        final List<Item> items = items(x);
        if (items.isEmpty()) {
            empty(x, left, y + 2, w, bottom);
            x.resultsFitted = 0;
            return;
        }
        int total = 0;
        for (final Item it : items) {
            total += itemHeight(x, it, w);
        }
        final String foot = x.t("create_stats.book.index.footnote");
        final int footH = c.wrap(foot, w).size() * BookTokens.LEAD + 6;
        final boolean paged = y + total > bottom - footH;
        final int limit = paged ? bottom - PAGER_H - 4 : bottom;
        final int from = Math.min(x.index.offset(), items.size() - 1);
        int i = from;

        if (paged && i > 0 && items.get(i).heading() == null) {
            for (int k = i - 1; k >= 0; k--) {
                if (items.get(k).heading() != null) {
                    if (y + GROUP_H + itemHeight(x, items.get(i), w) <= limit) {
                        groupHeading(x, left, y, w, x.t("create_stats.book.continued", items.get(k).heading()));
                        y += GROUP_H;
                    }
                    break;
                }
            }
        }
        int firstShown = 0;
        int lastShown = 0;
        while (i < items.size()) {
            final Item it = items.get(i);
            final int h = itemHeight(x, it, w);
            if (y + h > limit && i > from) {
                break;
            }

            if (it.heading() != null && i + 1 < items.size() && y + h + itemHeight(x, items.get(i + 1), w) > limit
                    && i > from) {
                break;
            }
            if (it.heading() != null) {
                groupHeading(x, left, y, w, it.heading());
            } else if (it.sheet() != null) {
                sheetRow(x, it.sheet(), left, y, w);
            } else {
                entry(x, it.entry(), it.entryIndex(), left, y, w);
            }
            if (it.ordinal() > 0) {
                firstShown = firstShown == 0 ? it.ordinal() : firstShown;
                lastShown = it.ordinal();
            }
            y += h;
            i++;
        }
        x.resultsFitted = i - from;
        if (paged) {
            pager(x, left, bottom - PAGER_H, w, firstShown, lastShown, x.results().total(), from > 0,
                    i < items.size());
        } else {
            int fy = y + 2;
            for (final String line : c.wrap(foot, w)) {
                c.text(line, left, fy, BookTokens.MUTE, false);
                fy += BookTokens.LEAD;
            }
        }
    }

    private static void groupHeading(Ctx x, int left, int y, int w, String name) {
        final BookCanvas c = x.c;
        c.text(name, left, y + 3, Section.FAULTS.colour(), true);
        c.hline(left + c.width(name, true) + 4, left + w, y + 7, BookTokens.RULE);
    }

    private static void entry(Ctx x, FaultIndex.Entry e, int index, int left, int top, int w) {
        final BookCanvas c = x.c;
        final Layout l = entryLayout(x, e, w);
        final String id = "hit:" + index;
        final int h = itemHeight(x, new Item(null, e, index, null, 1), w);
        final Section section = e.sheet().section();
        if (x.lit(id)) {
            c.fill(left - 2, top - 3, left + w + 2, top + h - 6, BookTokens.alpha(section.colour(), BookTokens.ROW_TINT_ALPHA));
        }
        if (e.mark() != null) {
            Ink.mark(c, e.mark(), left, top + 1);
        } else {
            causeSign(c, left, top + 1);
        }
        int y = top;
        for (final String line : l.head()) {
            c.text(line, left + 12, y, BookTokens.INK, true);
            y += BookTokens.LEAD;
        }
        final String page = x.rowPage.getOrDefault(e.target(), "");
        if (!page.isEmpty()) {
            c.fill(left + w - l.pageW(), top - 1, left + w, top + 10, section.colour());
            c.text(page, left + w - l.pageW() + 4, top + 1, BookTokens.WHITE, true);
        }
        y = top + 13 + (l.head().size() - 1) * BookTokens.LEAD;
        final String icon = e.sheet().icon();
        if (icon != null) {
            c.item(icon, left + 10, y - 2);
        }
        c.text(l.who(), left + 30, y + 2, BookTokens.MUTE, false);
        int cy = y + 2 + l.whoLines();
        for (final String line : l.cause()) {
            c.text(line, left + l.causeX(), cy, BookTokens.INK, false);
            cy += BookTokens.LEAD;
        }
        c.hline(left, left + w, top + h - 6, BookTokens.ROW_RULE);
        c.hot(id, left - 2, top - 3, left + w + 2, top + h - 6);
    }

    private static void causeSign(BookCanvas c, int x, int y) {
        c.fill(x, y + 3, x + 3, y + 4, BookTokens.INK);
        Ink.arrowRight(c, x + 4, y + 3, BookTokens.INK);
        c.outline(x + 5, y, x + 8, y + 7, BookTokens.INK);
    }

    private static void sheetRow(Ctx x, Sheet s, int left, int y, int w) {
        final BookCanvas c = x.c;
        final String id = "toc:" + s.id();
        final boolean lit = x.lit(id);
        if (s.icon() != null) {
            c.item(s.icon(), left, y - 1);
        }
        final String name = x.t(s.titleKey());
        final String page = x.firstPage.getOrDefault(s.id(), "");
        final int pw = c.width(page, true);
        c.text(name, left + 20, y + 3, lit ? s.section().colour() : BookTokens.INK, false);
        c.dots(left + 22 + c.width(name, false), left + w - pw - 3, y + 10, BookTokens.RULE, 2);
        c.text(page, left + w - pw, y + 3, BookTokens.INK, true);
        c.hot(id, left - 2, y - 2, left + w + 2, y + SHEET_H - 2);
    }

    private static void empty(Ctx x, int left, int top, int w, int bottom) {
        final BookCanvas c = x.c;
        final IndexState s = x.index;
        final String first = !s.query().isBlank() ? x.t("create_stats.book.index.none", s.query())
                : x.t("create_stats.book.index.none_filed");
        int y = top;
        for (final String line : c.wrap(first, w, true)) {
            c.text(line, left, y, BookTokens.INK, true);
            y += BookTokens.LEAD;
        }
        y += 4;
        for (final String line : c.wrap(x.t("create_stats.book.index.none_hint"), w)) {
            c.text(line, left, y, BookTokens.MUTE, false);
            y += BookTokens.LEAD;
        }
        final Section near = s.near();
        final String page = near == null ? null : x.sectionPage.get(near);
        if (near == null || y + 14 + CHIP_H > bottom) {
            return;
        }
        y += 8;
        c.text(x.t("create_stats.book.index.its_section"), left, y, BookTokens.MUTE, false);
        y += 12;
        final String name = near.number() + "  " + x.t(near.nameKey());
        final String id = "tab:" + near.ordinal();
        final int cw = 22 + c.width(name, true) + 8 + (page == null ? 0 : c.width(page, true) + 6);
        c.fill(left, y, left + cw, y + CHIP_H,
                x.lit(id) ? BookTokens.lerp(near.colour(), BookTokens.WHITE, 0.14f) : near.colour());
        c.fill(left + 2, y + 2, left + 18, y + 18, BookTokens.well(near.colour()));
        c.item(near.icon(), left + 2, y + 2);
        c.text(name, left + 22, y + 6, BookTokens.WHITE, true);
        if (page != null) {
            c.textRight(page, left + cw - 6, y + 6, BookTokens.WHITE, true);
        }
        c.hot(id, left, y, left + cw, y + CHIP_H);
    }

    private static void pager(Ctx x, int left, int top, int w, int from, int to, int count, boolean back,
                              boolean on) {
        final BookCanvas c = x.c;
        c.hline(left, left + w, top, BookTokens.RULE);
        c.text(x.t("create_stats.book.index.showing", from, to, count), left, top + 5, BookTokens.MUTE, false);
        pageBox(x, "res:prev", left + w - 30, top + 3, false, back);
        pageBox(x, "res:next", left + w - 14, top + 3, true, on);
    }

    private static void pageBox(Ctx x, String id, int bx, int by, boolean forward, boolean live) {
        final BookCanvas c = x.c;
        final int ink = live ? BookTokens.INK : BookTokens.RULE;
        c.fill(bx, by, bx + 14, by + 11, x.lit(id) && live ? Section.FAULTS.colour() : ink);
        c.fill(bx + 1, by + 1, bx + 13, by + 10, BookTokens.WHITE);
        if (forward) {
            Ink.arrowRight(c, bx + 8, by + 5, ink);
            c.fill(bx + 4, by + 5, bx + 8, by + 6, ink);
        } else {
            c.fill(bx + 5, by + 3, bx + 6, by + 8, ink);
            c.fill(bx + 4, by + 4, bx + 5, by + 7, ink);
            c.fill(bx + 6, by + 5, bx + 10, by + 6, ink);
        }
        if (live) {
            c.hot(id, bx, by, bx + 14, by + 11);
        }
    }
}
