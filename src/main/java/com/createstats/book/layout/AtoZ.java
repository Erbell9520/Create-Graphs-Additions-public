package com.createstats.book.layout;

import com.createstats.book.BookTokens;
import com.createstats.book.content.Book;
import com.createstats.book.content.Part;
import com.createstats.book.content.Sheet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class AtoZ extends Block {
    private record Line(String letter, String name, String sheetId) {
        boolean heading() {
            return name == null;
        }
    }

    private static final int HEADING_H = 16;
    private static final int ROW_H = 11;

    private final List<Line> lines;
    private final int from;
    private final int to;

    private AtoZ(List<Line> lines, int from, int to) {
        this.lines = lines;
        this.from = from;
        this.to = to;
    }

    static AtoZ of(Ctx x) {
        final List<Line> entries = new ArrayList<>();
        for (final Sheet s : Book.sheets()) {
            for (final String id : s.ids()) {
                entries.add(new Line(null, x.text.itemName(id), s.id()));
            }

            for (final String id : s.also()) {
                entries.add(new Line(null, x.text.itemName(id), s.id()));
            }
            for (final Part p : s.parts()) {
                if (p instanceof Part.Retired r) {
                    for (final Part.Unlisted u : r.rows()) {
                        entries.add(new Line(null, x.text.itemName(u.itemId()), s.id()));
                    }
                }
            }
        }
        entries.sort(Comparator.comparing((Line l) -> l.name().toLowerCase(Locale.ROOT)));
        final List<Line> out = new ArrayList<>();
        String letter = null;
        for (final Line e : entries) {
            final String first = e.name().isEmpty() ? "#" : e.name().substring(0, 1).toUpperCase(Locale.ROOT);
            if (!first.equals(letter)) {
                letter = first;
                out.add(new Line(first, null, null));
            }
            out.add(new Line(first, e.name(), e.sheetId()));
        }
        return new AtoZ(out, 0, out.size());
    }

    private int h(int i) {
        return lines.get(i).heading() ? HEADING_H : ROW_H;
    }

    @Override
    public int height(Ctx x, int w) {
        int h = 0;
        for (int i = from; i < to; i++) {
            h += h(i);
        }
        return h;
    }

    @Override
    public Block[] split(Ctx x, int w, int room) {
        int h = 0;
        int cut = from;
        while (cut < to && h + h(cut) <= room) {
            h += h(cut);
            cut++;
        }

        if (cut > from && cut < to && lines.get(cut - 1).heading()) {
            cut--;
        }
        if (cut == from || cut == to) {
            return null;
        }
        return new Block[]{new AtoZ(lines, from, cut), new AtoZ(lines, cut, to)};
    }

    @Override
    public void paint(Ctx x, int left, int top, int w) {
        final BookCanvas c = x.c;
        int y = top;
        for (int i = from; i < to; i++) {
            final Line l = lines.get(i);
            if (l.heading()) {
                c.text(l.letter(), left, y + 4, x.colour(), true);
                c.hline(left + c.width(l.letter(), true) + 4, left + w, y + 8, BookTokens.RULE);
                y += HEADING_H;
                continue;
            }
            final String id = "toc:" + l.sheetId();
            final boolean lit = x.lit(id);
            final String page = x.firstPage.getOrDefault(l.sheetId(), "");
            final int pw = c.width(page, true);
            c.text(l.name(), left + 6, y + 1, lit ? x.colour() : BookTokens.INK, false);
            c.dots(left + 8 + c.width(l.name(), false), left + w - pw - 3, y + 8, BookTokens.RULE, 2);
            c.text(page, left + w - pw, y + 1, BookTokens.INK, true);
            c.hot(id, left, y - 1, left + w, y + ROW_H - 1);
            y += ROW_H;
        }
    }
}
