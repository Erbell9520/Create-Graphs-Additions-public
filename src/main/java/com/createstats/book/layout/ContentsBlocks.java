package com.createstats.book.layout;

import com.createstats.book.BookTokens;
import com.createstats.book.content.Book;
import com.createstats.book.content.Scenes;
import com.createstats.book.content.Section;
import com.createstats.book.content.Sheet;

import java.util.ArrayList;
import java.util.List;

public final class ContentsBlocks {
    private ContentsBlocks() {
    }

    static final class Title extends Block {
        private final Section section;

        Title(Section section) {
            this.section = section;
        }

        @Override
        public int height(Ctx x, int w) {
            return 28 + x.c.wrap(x.t(section.hookKey()), w).size() * BookTokens.LEAD + 4;
        }

        @Override
        public void paint(Ctx x, int left, int top, int w) {
            final BookCanvas c = x.c;
            c.textScaled(x.t(section.nameKey()), left, top, BookTokens.INK, 3);
            int y = top + 30;
            for (final String line : c.wrap(x.t(section.hookKey()), w)) {
                c.text(line, left, y, BookTokens.INK, false);
                y += BookTokens.LEAD;
            }
            c.hline(left, left + w, y + 1, BookTokens.INK);
        }
    }

    static final class Group extends Block {
        private final String groupKey;
        private final List<Sheet> sheets;
        private final boolean continued;

        Group(String groupKey, List<Sheet> sheets, boolean continued) {
            this.groupKey = groupKey;
            this.sheets = List.copyOf(sheets);
            this.continued = continued;
        }

        @Override
        public int gap() {
            return 4;
        }

        @Override
        public int height(Ctx x, int w) {
            return 12 + sheets.size() * 18;
        }

        @Override
        public Block[] split(Ctx x, int w, int room) {
            final int fit = (room - 12) / 18;
            if (fit < 1 || fit >= sheets.size()) {
                return null;
            }
            return new Block[]{new Group(groupKey, sheets.subList(0, fit), continued),
                    new Group(groupKey, sheets.subList(fit, sheets.size()), true)};
        }

        @Override
        public void paint(Ctx x, int left, int top, int w) {
            final BookCanvas c = x.c;
            final String head = x.t(groupKey);
            c.text(continued ? x.t("create_stats.book.continued", head) : head, left, top, x.colour(), true);
            int y = top + 12;
            for (final Sheet s : sheets) {
                final String id = "toc:" + s.id();
                final boolean lit = x.lit(id);
                if (s.icon() != null) {
                    c.item(s.icon(), left, y - 3);
                }
                final String name = x.t(s.titleKey());
                c.text(name, left + 20, y + 1, lit ? x.colour() : BookTokens.INK, false);
                final String page = x.firstPage.getOrDefault(s.id(), "");
                final int pw = c.width(page, true);
                c.text(page, left + w - pw, y + 1, BookTokens.INK, true);
                c.dots(left + 22 + c.width(name, false), left + w - pw - 3, y + 8, BookTokens.RULE, 2);
                c.hot(id, left - 2, y - 4, left + w + 2, y + 14);
                y += 18;
            }
        }
    }

    static final class Empty extends Block {
        private final String key;

        Empty(String key) {
            this.key = key;
        }

        @Override
        public int height(Ctx x, int w) {
            return x.c.wrap(x.t(key), w).size() * BookTokens.LEAD;
        }

        @Override
        public void paint(Ctx x, int left, int top, int w) {
            int y = top;
            for (final String line : x.c.wrap(x.t(key), w)) {
                x.c.text(line, left, y, BookTokens.MUTE, false);
                y += BookTokens.LEAD;
            }
        }
    }

    static final class PonderList extends Block {
        private final Section section;
        private final List<String> ids;
        private final List<Scenes.Row> rows;
        private final boolean continued;

        PonderList(Section section, List<String> ids, boolean continued) {
            this(section, ids, Scenes.rows(section, ids), continued);
        }

        private PonderList(Section section, List<String> ids, List<Scenes.Row> rows, boolean continued) {
            this.section = section;
            this.ids = List.copyOf(ids);
            this.rows = List.copyOf(rows);
            this.continued = continued;
        }

        private static final int HEAD = 20;
        private static final int ROW = 20;

        @Override
        public int height(Ctx x, int w) {
            return HEAD + Math.max(1, rows.size()) * ROW;
        }

        @Override
        public Block[] split(Ctx x, int w, int room) {
            final int fit = (room - HEAD) / ROW;
            if (fit < 1 || fit >= rows.size()) {
                return null;
            }
            return new Block[]{new PonderList(section, ids, rows.subList(0, fit), continued),
                    new PonderList(section, ids, rows.subList(fit, rows.size()), true)};
        }

        @Override
        public void paint(Ctx x, int left, int top, int w) {
            final BookCanvas c = x.c;
            final String title = x.t("create_stats.book.part.watch_it_run");
            final String head = continued ? x.t("create_stats.book.continued", title) : title;
            final String note = continued ? null : x.t("create_stats.book.note.ponder_scenes");
            if (top > x.contentTop) {
                c.hline(left, left + w, top, BookTokens.INK);
                c.text(head, left, top + 6, BookTokens.INK, true);
                if (note != null) {
                    c.textRight(note, left + w, top + 6, BookTokens.MUTE, false);
                }
            } else {
                Ink.tableHeading(c, left, top, w, head, note);
            }
            int y = top + HEAD;
            if (rows.isEmpty()) {
                c.text(x.t("create_stats.book.empty.ponder"), left, y + 5, BookTokens.MUTE, false);
                return;
            }
            for (final Scenes.Row r : rows) {
                ponderRow(x, left, y, w, r);
                y += ROW;
            }
        }
    }

    // shared so contents and sheet can't drift
    static void ponderRow(Ctx x, int left, int y, int w, Scenes.Row r) {
        final BookCanvas c = x.c;
        final String label = x.t("create_stats.book.button.ponder");
        final int bw = c.width(label, false) + 16;
        c.fill(left, y, left + 18, y + 18, BookTokens.RULE);
        c.fill(left + 1, y + 1, left + 17, y + 17, BookTokens.WHITE);
        c.item(r.itemId(), left + 1, y + 1);
        final String what = r.labelKey() != null ? x.t(r.labelKey()) : x.text.itemName(r.itemId());
        c.text(what, left + 24, y + 5, BookTokens.INK, false);
        final String hot = "ponder:" + r.itemId();
        final int bx = left + w - bw;
        c.sprite(x.lit(hot) ? "widget/button_highlighted" : "widget/button", bx, y, bw, 18);

        c.shadowText(label, bx + (bw - c.width(label, false)) / 2, y + 5, BookTokens.WHITE);
        c.hot(hot, bx, y, bx + bw, y + 18);
    }

    static final class Notes extends Block {
        @Override
        public int height(Ctx x, int w) {
            return 14;
        }

        @Override
        public void paint(Ctx x, int left, int top, int w) {
            final BookCanvas c = x.c;
            c.text(x.t("create_stats.book.part.notes"), left, top, BookTokens.MUTE, true);
            for (int y = top + 26; y < top + x.pageH - 4; y += 14) {
                c.hline(left, left + w, y, BookTokens.ROW_RULE);
            }
        }
    }

    static List<Block> toc(Section section, List<Sheet> sheets) {
        final List<Block> out = new ArrayList<>();
        out.add(new Title(section));
        if (sheets.isEmpty()) {
            out.add(new Empty("create_stats.book.empty.section"));
            return out;
        }
        groups(sheets, out);
        if (section == Section.BASICS) {
            groups(Book.in(Section.FAULTS), out);
        }
        return out;
    }

    private static void groups(List<Sheet> sheets, List<Block> out) {
        final List<String> order = new ArrayList<>();
        for (final Sheet s : sheets) {
            if (!order.contains(s.groupKey())) {
                order.add(s.groupKey());
            }
        }
        for (final String g : order) {
            final List<Sheet> in = new ArrayList<>();
            for (final Sheet s : sheets) {
                if (s.groupKey().equals(g)) {
                    in.add(s);
                }
            }
            out.add(new Group(g, in, false));
        }
    }
}
