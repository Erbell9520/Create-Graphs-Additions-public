package com.createstats.book.layout;

import com.createstats.book.BookTokens;
import com.createstats.book.content.Book;
import com.createstats.book.content.Mark;
import com.createstats.book.content.Num;
import com.createstats.book.content.Part;
import com.createstats.book.content.Sheet;
import com.createstats.book.specimen.SheetDrawing;
import com.createstats.book.specimen.SpecimenAnatomy;
import com.createstats.book.specimen.Specimens;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

public final class SheetBlocks {
    private SheetBlocks() {
    }

    static boolean keepWhole(Ctx x, int height) {
        return height <= x.pageH * 3 / 5;
    }

    public static List<Block> of(Ctx x, Sheet sheet) {
        final List<Block> out = new ArrayList<>();

        if (sheet.parts().isEmpty() || !(sheet.parts().get(0) instanceof Part.FaultIndex)) {
            out.add(new Head(sheet));
        }
        for (final Part p : sheet.parts()) {
            final boolean ruled = !(out.size() == 1 && out.get(0) instanceof Head);
            switch (p) {
                case Part.Figure f -> out.add(new Figure(sheet, f));
                case Part.Controls k -> out.add(new Controls(sheet, k, 0, k.rows().size(), false));
                case Part.World wd -> out.add(Rows.world(wd));
                case Part.Status s -> out.add(Rows.status(sheet, s));
                case Part.Ratings r -> out.add(new Ratings(r, ruled));
                case Part.Grades g -> out.add(new GradesTable(g, 0, g.rows().size(), false));
                case Part.Curve cv -> out.add(new CurveBlock(sheet, cv));
                case Part.Application a -> out.add(new Application(a, ruled));
                case Part.Faults fl -> out.add(new Faults(sheet, fl));
                case Part.SeeAlso sa -> out.add(new SeeAlso(sa));
                case Part.Note n -> out.add(new Note(n));
                case Part.Break b -> out.add(new Break(b.bandKey(), b.bandArgs(), b.left()));
                case Part.Steps st -> out.add(new StepsBlock(st, 0, st.steps().size(), false));
                case Part.Terms t -> out.add(Rows.terms(x, t));
                case Part.Marks m -> out.add(Rows.marks(m));
                case Part.Retired r -> out.add(new RetiredRows(r, 0, r.rows().size()));
                case Part.FaultIndex fi -> out.addAll(IndexPage.blocks(x));
                case Part.AtoZ az -> out.add(AtoZ.of(x));
                case Part.Ponder pd -> out.add(new PonderRow(pd));
                case Part.Made m -> out.add(new MadeTable(m, 0, m.recipes().size(), false));
            }
        }
        return out;
    }

    static final class Head extends Block {
        private final Sheet sheet;

        Head(Sheet sheet) {
            this.sheet = sheet;
        }

        private String title(Ctx x) {
            return x.t(sheet.titleKey()).toUpperCase(Locale.ROOT);
        }

        private int indent() {
            return sheet.icon() != null ? 22 : 0;
        }

        private boolean big(Ctx x, int w) {
            return (x.c.width(title(x), false) + 1) * 2 <= w - indent();
        }

        private List<String> hook(Ctx x, int w) {
            return x.c.wrap(x.t(sheet.hookKey()), w);
        }

        private List<String> ids(Ctx x, int w) {
            if (!sheet.coversBlocks()) {
                return List.of();
            }
            final StringBuilder b = new StringBuilder(sheet.ids().get(0));
            for (int i = 1; i < sheet.ids().size(); i++) {
                final String id = sheet.ids().get(i);
                b.append(", ").append(id.substring(id.indexOf(':') + 1));
            }
            final String page = x.firstPage.getOrDefault(sheet.id(), "8-88");
            final int pageW = x.c.width(x.t("create_stats.book.sheet_number", page), false) + 8;
            final List<String> lines = x.c.wrap(b.toString(), w - indent() - pageW);
            return lines.isEmpty() ? List.of(sheet.ids().get(0)) : lines;
        }

        private int hookTop(Ctx x, int w) {
            return sheet.coversBlocks() ? 32 + (ids(x, w).size() - 1) * BookTokens.LEAD : 22;
        }

        @Override
        public int height(Ctx x, int w) {
            return hookTop(x, w) + hook(x, w).size() * BookTokens.LEAD + 4;
        }

        @Override
        public void paint(Ctx x, int left, int top, int w) {
            final BookCanvas c = x.c;
            if (sheet.icon() != null) {
                c.item(sheet.icon(), left, top);
            }
            if (big(x, w)) {
                c.textScaled(title(x), left + indent(), top - 1, BookTokens.INK, 2);
            } else {
                c.text(title(x), left + indent(), top + 4, BookTokens.INK, true);
            }
            if (sheet.coversBlocks()) {
                int iy = top + 18;
                for (final String line : ids(x, w)) {
                    c.text(line, left + indent(), iy, BookTokens.MUTE, false);
                    iy += BookTokens.LEAD;
                }
                final String page = x.firstPage.get(sheet.id());
                if (page != null) {
                    c.textRight(x.t("create_stats.book.sheet_number", page), left + w, top + 18, BookTokens.MUTE,
                            false);
                }
            }
            int y = top + hookTop(x, w);

            final int hookInk = sheet.section() == com.createstats.book.content.Section.FAULTS
                    ? BookTokens.MUTE : BookTokens.INK;
            for (final String line : hook(x, w)) {
                c.text(line, left, y, hookInk, false);
                y += BookTokens.LEAD;
            }
            c.hline(left, left + w, y + 3, BookTokens.INK);
        }
    }

    static final class Figure extends Block {
        private final Sheet sheet;
        private final Part.Figure part;
        private final SpecimenAnatomy anatomy;
        private final Map<String, Integer> numbers;

        Figure(Sheet sheet, Part.Figure part) {
            this.sheet = sheet;
            this.part = part;
            this.anatomy = Specimens.anatomy(part.specimen())
                    .orElseThrow(() -> new IllegalStateException("no specimen " + part.specimen()));
            this.numbers = Callouts.numbering(anatomy);
        }

        String specimen() {
            return part.specimen();
        }

        int scale(Ctx x, int w) {
            final int den = x.geo.guiScale;
            if (anatomy.drawing()) {
                return den;
            }
            final int withHead = x.pageH - HEAD_ROOM - CHROME;
            final int alone = x.pageH - CHROME;
            int fallback = 0;
            for (int n = den; n >= 1; n--) {
                final int need = figW(n, den) + 2 * (BookTokens.PIN_W + 6) + 2 + (names(x) ? namesWidth(x) + 16 : 0);
                if (need > w) {
                    continue;
                }
                final int fh = figH(n, den);
                if (fh <= withHead) {
                    return n >= 2 || fallback == 0 || anatomy.block() ? n : fallback;
                }
                if (fallback == 0 && n >= 2 && fh <= alone) {
                    fallback = n;
                }
            }
            return fallback > 0 ? fallback : 1;
        }

        private static final int HEAD_ROOM = 56;
        private static final int CHROME = 31;

        int figW(int n, int den) {
            return (int) Math.ceil(anatomy.width() * n / (double) den);
        }

        int figH(int n, int den) {
            return (int) Math.ceil(anatomy.height() * n / (double) den);
        }

        private int namesWidth(Ctx x) {
            if (controls().isEmpty()) {
                return 0;
            }
            int max = 0;
            for (final Part.Control row : controls()) {
                max = Math.max(max, x.c.width(x.t(row.nameKey()), true));
            }
            return BookTokens.PIN_W + 6 + max;
        }

        private boolean names(Ctx x) {
            return x.wide && !controls().isEmpty();
        }

        private List<Part.Control> controls() {
            for (final Part p : sheet.parts()) {
                if (p instanceof Part.Controls k && k.specimen().equals(part.specimen())) {
                    final List<Part.Control> rows = new ArrayList<>(k.rows());
                    rows.sort((a, b) -> Integer.compare(numbers.getOrDefault(a.id(), 99),
                            numbers.getOrDefault(b.id(), 99)));
                    return rows;
                }
            }
            return List.of();
        }

        private int run(Ctx x, int w, int fw) {
            final int names = names(x) ? namesWidth(x) + 16 : 0;
            return Math.min(BookTokens.PIN_RUN, Math.max(6, (w - names - fw - 2 - 2 * BookTokens.PIN_W) / 2));
        }

        private int figureLeft(Ctx x, int w, int fw) {
            final int run = run(x, w, fw);
            final int span = fw + 2 * (run + BookTokens.PIN_W);
            return (names(x) ? 0 : Math.max(0, (w - span) / 2)) + BookTokens.PIN_W + run;
        }

        private List<String> caption(Ctx x, int w) {
            final int fw = figW(scale(x, w), x.geo.guiScale);
            final String number = x.figureNumber.getOrDefault(Ctx.figureKey(sheet, part.specimen()), "8.88");
            final String text = x.t("create_stats.book.figure", number) + "  " + x.t(part.captionKey());

            return x.c.width(text, false) <= w ? List.of(text) : x.c.wrap(text, Math.max(40, w - figureLeft(x, w, fw)));
        }

        private int captionLeft(Ctx x, int left, int w, int fx) {
            final List<String> lines = caption(x, w);
            if (lines.size() != 1) {
                return fx;
            }
            return Math.max(left, Math.min(fx, left + w - x.c.width(lines.get(0), false)));
        }

        @Override
        public int height(Ctx x, int w) {
            final int n = scale(x, w);
            final int base = 17 + figH(n, x.geo.guiScale) + 5 + BookTokens.LINE
                    + (Math.max(1, caption(x, w).size()) - 1) * BookTokens.LEAD;
            if (names(x)) {
                final int list = 17 + controls().size() * 12 + 4 + 2 * BookTokens.LEAD;
                return Math.max(base, list);
            }
            return base;
        }

        @Override
        public void paint(Ctx x, int left, int top, int w) {
            final BookCanvas c = x.c;
            final int den = x.geo.guiScale;
            final int n = scale(x, w);
            final int fw = figW(n, den);
            final int fh = figH(n, den);
            final int run = run(x, w, fw);
            final int fx = left + figureLeft(x, w, fw);
            final int fy = top + 17;
            final String heading = x.t(part.headingKey());
            c.text(heading, left, top, BookTokens.INK, true);
            if (part.tab() != null) {
                final int stripRight = names(x) ? fx + fw + run + BookTokens.PIN_W : left + w;
                Ink.tabStrip(c, stripRight, top, part.tab().names(), part.tab().index(), x.colour(),
                        stripRight - left - c.width(heading, true) - 10);
            }

            if (anatomy.block()) {
                c.fill(fx, fy, fx + fw, fy + fh, BookTokens.SCENE_WELL);
                Ink.corners(c, fx, fy, fx + fw, fy + fh, BookTokens.RULE);
            } else {
                c.outline(fx - 1, fy - 1, fx + fw + 1, fy + fh + 1, BookTokens.INK);
            }
            c.specimen(part.specimen(), fx, fy, n, den, x.mouseX, x.mouseY);
            if (anatomy.drawing()) {
                SheetDrawing.paint(c, fx, fy);
            }

            final int leftX = fx - run - BookTokens.PIN_W;
            final int rightX = fx + fw + run;
            final List<Callouts.Pin> pins = Callouts.layout(anatomy, fx, fy, n, den, leftX, rightX, fy - 2,
                    fy + fh + 2, numbers);
            final int col = x.colour();
            c.raise(1);

            for (final SpecimenAnatomy.Hot h : anatomy.controls()) {
                final int hx = fx + h.x() * n / den;
                final int hy = fy + h.y() * n / den;
                c.hot(Ctx.controlId(sheet, part.specimen(), h.id()), hx, hy, hx + Math.max(1, h.w() * n / den),
                        hy + Math.max(1, h.h() * n / den));
            }
            if (anatomy.cased()) {
                for (final Callouts.Pin p : pins) {
                    Ink.casing(c, p.leaderX(), p.leaderY(), p.anchorX(), p.anchorY(), BookTokens.PAPER);
                    c.fill(p.anchorX() - 2, p.anchorY() - 2, p.anchorX() + 3, p.anchorY() + 3, BookTokens.PAPER);
                }
            }
            for (final Callouts.Pin p : pins) {
                final String id = Ctx.controlId(sheet, part.specimen(), p.control());
                final boolean lit = x.lit(id);
                final int ink = lit ? col : BookTokens.INK;
                c.line(p.leaderX(), p.leaderY(), p.anchorX(), p.anchorY(), ink);
                c.fill(p.anchorX() - 1, p.anchorY() - 1, p.anchorX() + 2, p.anchorY() + 2, ink);
                c.fill(p.anchorX(), p.anchorY(), p.anchorX() + 1, p.anchorY() + 1, BookTokens.WHITE);
                if (lit) {
                    final SpecimenAnatomy.Hot h = anatomy.control(p.control());
                    final int x0 = fx + h.x() * n / den - 2;
                    final int y0 = fy + h.y() * n / den - 2;
                    final int x1 = fx + (int) Math.ceil((h.x() + h.w()) * n / (double) den) + 2;
                    final int y1 = fy + (int) Math.ceil((h.y() + h.h()) * n / (double) den) + 2;
                    c.outline(x0, y0, x1, y1, col);
                    c.outline(x0 - 1, y0 - 1, x1 + 1, y1 + 1, col);
                }
                Ink.pin(c, p.boxX(), p.boxY(), p.number(), col, lit);
                c.hot(id, p.boxX() - 1, p.boxY() - 1, p.boxX() + BookTokens.PIN_W + 1, p.boxY() + BookTokens.PIN_H + 1);
            }
            c.raise(0);

            int cy = fy + fh + 5;
            final int cx = captionLeft(x, left, w, fx);
            for (final String line : caption(x, w)) {
                c.text(line, cx, cy, BookTokens.MUTE, false);
                cy += BookTokens.LEAD;
            }

            if (names(x)) {
                paintNames(x, rightX + BookTokens.PIN_W + 16, top, left + w);
            }
        }

        private void paintNames(Ctx x, int nx, int top, int right) {
            final BookCanvas c = x.c;
            String heading = "create_stats.book.part.designations";
            for (final Part p : sheet.parts()) {
                if (p instanceof Part.Controls k && k.specimen().equals(part.specimen())) {
                    heading = k.headingKey();
                }
            }
            c.text(x.t(heading), nx, top, BookTokens.INK, true);
            c.hline(nx, right, top + 11, BookTokens.INK);
            int y = top + 17;
            for (final Part.Control row : controls()) {
                final String id = Ctx.controlId(sheet, part.specimen(), row.id());
                final boolean lit = x.lit(id);
                if (lit) {
                    c.fill(nx - 2, y - 2, right, y + 11, BookTokens.alpha(x.colour(), BookTokens.ROW_TINT_ALPHA));
                }
                Ink.pin(c, nx, y, numbers.getOrDefault(row.id(), 0), x.colour(), lit);
                c.text(x.t(row.nameKey()), nx + 18, y + 1, BookTokens.INK, true);
                c.hot(id, nx - 2, y - 2, right, y + 11);
                y += 12;
            }
            final String page = x.controlsPage.get(Ctx.figureKey(sheet, part.specimen()));
            if (page != null) {
                y += 4;

                boolean words = false;
                boolean faults = false;
                boolean after = false;
                for (final Part p : sheet.parts()) {
                    if (p instanceof Part.Controls k && k.specimen().equals(part.specimen())) {
                        after = true;
                    } else if (after && p instanceof Part.Figure) {
                        break;
                    } else if (after) {
                        words |= p instanceof Part.Status;
                        faults |= p instanceof Part.Faults;
                    }
                }
                final String key = faults ? "create_stats.book.continued_on"
                        : words ? "create_stats.book.continued_on.words" : "create_stats.book.continued_on.short";
                for (final String line : c.wrap(x.t(key, page), right - nx)) {
                    c.text(line, nx, y, BookTokens.MUTE, false);
                    y += BookTokens.LEAD;
                }
            }
        }
    }

    static final class Controls extends Block {
        private final Sheet sheet;
        private final Part.Controls part;
        private final int from;
        private final int to;
        private final boolean continued;
        private final Map<String, Integer> numbers;

        Controls(Sheet sheet, Part.Controls part, int from, int to, boolean continued) {
            this.sheet = sheet;
            this.part = part;
            this.from = from;
            this.to = to;
            this.continued = continued;
            this.numbers = Callouts.numbering(Specimens.anatomy(part.specimen()).orElseThrow());
        }

        String specimen() {
            return part.specimen();
        }

        private Part.Tab tab() {
            for (final Part p : sheet.parts()) {
                if (p instanceof Part.Figure f && f.specimen().equals(part.specimen())) {
                    return f.tab();
                }
            }
            return null;
        }

        private List<Part.Control> rows() {
            final List<Part.Control> rows = new ArrayList<>(part.rows());
            rows.sort((a, b) -> Integer.compare(numbers.getOrDefault(a.id(), 99), numbers.getOrDefault(b.id(), 99)));
            return rows;
        }

        private int nameCol(Ctx x, int w) {
            int max = 0;
            for (final Part.Control r : part.rows()) {
                max = Math.max(max, x.c.width(x.t(r.nameKey()), true));
            }
            return Math.min(max + 10, (int) ((w - 18) * 0.42f));
        }

        private int rowHeight(Ctx x, Part.Control r, int w) {
            final int nameW = nameCol(x, w);
            final int fw = w - 18 - nameW;
            final int name = x.c.wrap(x.t(r.nameKey()), nameW - 6, true).size();
            final List<String> fn = x.c.wrap(x.text.joined(r.functionKey()), fw);
            int lines = fn.size();
            if (r.keysKey() != null && !inline(x, fn, keys(x, r), fw)) {
                lines += x.c.wrap(keys(x, r), fw).size();
            }
            return Math.max(14, Math.max(name, lines) * BookTokens.LEAD + 4);
        }

        private static String keys(Ctx x, Part.Control r) {
            return x.t(r.keysKey(), r.keysArgs() == null ? new Object[0] : r.keysArgs().get());
        }

        private static boolean inline(Ctx x, List<String> fn, String keys, int fw) {
            return !fn.isEmpty() && x.c.width(fn.get(fn.size() - 1), false) + 7 + x.c.width(keys, false) <= fw;
        }

        private int headH() {
            return Ink.TABLE_HEADING + 14;
        }

        @Override
        public int height(Ctx x, int w) {
            int h = headH();
            final List<Part.Control> rows = rows();
            for (int i = from; i < to; i++) {
                h += rowHeight(x, rows.get(i), w);
            }
            return h;
        }

        @Override
        public Block[] split(Ctx x, int w, int room) {
            if (keepWhole(x, height(x, w))) {
                return null;
            }
            int h = headH();
            final List<Part.Control> rows = rows();
            int cut = from;
            while (cut < to && h + rowHeight(x, rows.get(cut), w) <= room) {
                h += rowHeight(x, rows.get(cut), w);
                cut++;
            }
            if (cut == from || cut == to) {
                return null;
            }
            return new Block[]{new Controls(sheet, part, from, cut, continued), new Controls(sheet, part, cut, to, true)};
        }

        @Override
        public void paint(Ctx x, int left, int top, int w) {
            final BookCanvas c = x.c;
            final String title = x.t(part.headingKey());
            final String heading = continued ? x.t("create_stats.book.continued", title) : title;
            Ink.tableHeading(c, left, top, w, heading, null);
            final Part.Tab tab = tab();
            if (tab != null) {
                Ink.tabStrip(c, left + w, top, tab.names(), tab.index(), x.colour(), w - c.width(heading, true) - 10);
            }
            final int nameW = nameCol(x, w);
            final int fx = left + 18 + nameW;
            final int fw = w - 18 - nameW;
            c.text(x.t("create_stats.book.column.number"), left, top + 15, BookTokens.MUTE, false);
            c.text(x.t("create_stats.book.column.name"), left + 18, top + 15, BookTokens.MUTE, false);
            c.text(x.t("create_stats.book.column.function"), fx, top + 15, BookTokens.MUTE, false);
            c.hline(left, left + w, top + 25, BookTokens.RULE);
            int y = top + headH();
            final List<Part.Control> rows = rows();
            for (int i = from; i < to; i++) {
                final Part.Control r = rows.get(i);
                final int h = rowHeight(x, r, w);
                final String id = Ctx.controlId(sheet, part.specimen(), r.id());
                final boolean lit = x.lit(id);
                if (lit) {
                    c.fill(left - 2, y - 2, left + w + 2, y + h - 2, BookTokens.alpha(x.colour(), BookTokens.ROW_TINT_ALPHA));
                    c.fill(left - 2, y - 2, left, y + h - 2, x.colour());
                }
                Ink.pin(c, left, y, numbers.getOrDefault(r.id(), 0), x.colour(), lit);
                int ny = y + 1;
                for (final String line : c.wrap(x.t(r.nameKey()), nameW - 6, true)) {
                    c.text(line, left + 18, ny, BookTokens.INK, true);
                    ny += BookTokens.LEAD;
                }
                int fy = y + 1;
                final List<String> fn = c.wrap(x.text.joined(r.functionKey()), fw);
                for (final String line : fn) {
                    c.text(line, fx, fy, BookTokens.INK, false);
                    fy += BookTokens.LEAD;
                }
                if (r.keysKey() != null) {
                    final String k = keys(x, r);
                    if (inline(x, fn, k, fw)) {
                        c.text(k, fx + c.width(fn.get(fn.size() - 1), false) + 7, fy - BookTokens.LEAD,
                                BookTokens.MUTE, false);
                    } else {
                        for (final String line : c.wrap(k, fw)) {
                            c.text(line, fx, fy, BookTokens.MUTE, false);
                            fy += BookTokens.LEAD;
                        }
                    }
                }
                c.hline(left, left + w, y + h - 2, BookTokens.ROW_RULE);
                c.hot(id, left - 2, y - 2, left + w + 2, y + h - 2);
                y += h;
            }
        }
    }

    static final class Rows extends Block implements Targets {
        enum Kind {
            STATUS, WORLD, TERMS, MARKS
        }

        @Override
        public List<String> targets() {
            final List<String> out = new ArrayList<>();
            for (int i = from; i < to; i++) {
                if (rows.get(i).pulse() != null) {
                    out.add(rows.get(i).pulse());
                }
            }
            return out;
        }

        record Row(Mark mark, String termKey, Supplier<Object[]> termArgs, String meaningKey, Supplier<Object[]> args,
                   String pulse) {
        }

        private final Kind kind;
        private final String headingKey;
        private final List<Row> rows;
        private final int from;
        private final int to;
        private final boolean continued;

        Rows(Kind kind, String headingKey, List<Row> rows, int from, int to, boolean continued) {
            this.kind = kind;
            this.headingKey = headingKey;
            this.rows = rows;
            this.from = from;
            this.to = to;
            this.continued = continued;
        }

        static Rows status(Sheet sheet, Part.Status s) {
            final List<Row> rows = new ArrayList<>();

            final int base = com.createstats.book.content.FaultIndex.wordBase(sheet, s);
            for (int i = 0; i < s.rows().size(); i++) {
                final Part.Word w = s.rows().get(i);
                rows.add(new Row(w.mark(), w.wordKey(), w.args(), w.meaningKey(), null,
                        "word:" + sheet.id() + ":" + (base + i)));
            }
            return new Rows(Kind.STATUS, s.headingKey(), rows, 0, rows.size(), false);
        }

        static Rows world(Part.World wd) {
            final List<Row> rows = new ArrayList<>();
            for (final Part.Gesture g : wd.rows()) {
                rows.add(new Row(null, g.gestureKey(), null, g.effectKey(), null, null));
            }
            return new Rows(Kind.WORLD, "create_stats.book.part.in_the_world", rows, 0, rows.size(), false);
        }

        static Rows terms(Ctx x, Part.Terms t) {
            final List<Row> rows = new ArrayList<>();
            for (final Part.Term term : t.rows()) {
                rows.add(new Row(null, term.termKey(), null, term.meaningKey(), term.args(), null));
            }
            if (t.alphabetical()) {
                rows.sort(Comparator.comparing((Row r) -> x.t(r.termKey()).toLowerCase(Locale.ROOT)));
            }
            return new Rows(Kind.TERMS, t.headingKey(), rows, 0, rows.size(), false);
        }

        static Rows marks(Part.Marks m) {
            final List<Row> rows = new ArrayList<>();
            for (final Mark mark : Mark.values()) {
                final String key = "create_stats.book.mark." + mark.name().toLowerCase(Locale.ROOT);
                rows.add(new Row(mark, key, null, key + ".means", null, null));
            }
            return new Rows(Kind.MARKS, m.headingKey(), rows, 0, rows.size(), false);
        }

        private String term(Ctx x, int i) {
            final Row r = rows.get(i);
            return r.termArgs() == null ? x.t(r.termKey()) : x.t(r.termKey(), r.termArgs().get());
        }

        private String meaning(Ctx x, int i) {
            final Row r = rows.get(i);
            return r.args() == null ? x.t(r.meaningKey()) : x.t(r.meaningKey(), r.args().get());
        }

        private boolean marked() {
            return kind == Kind.STATUS || kind == Kind.MARKS;
        }

        private int indent() {
            return marked() ? 12 : 0;
        }

        private int col(Ctx x, int w) {
            int max = 0;
            for (int i = 0; i < rows.size(); i++) {
                max = Math.max(max, x.c.width(term(x, i), true));
            }
            return Math.min(indent() + max + 10, (int) (w * 0.42f));
        }

        private boolean ruled() {
            return kind == Kind.TERMS;
        }

        private int rowHeight(Ctx x, int i, int w) {
            final int col = col(x, w);
            final int termLines = x.c.wrap(term(x, i), col - indent() - 6, true).size();
            final int lines = x.c.wrap(meaning(x, i), w - col).size();
            return Math.max(13, Math.max(termLines, lines) * BookTokens.LEAD + 3) + (ruled() ? 4 : 0);
        }

        @Override
        public int height(Ctx x, int w) {
            int h = Ink.TABLE_HEADING + 2;
            for (int i = from; i < to; i++) {
                h += rowHeight(x, i, w);
            }
            return h;
        }

        @Override
        public Block[] split(Ctx x, int w, int room) {
            if (keepWhole(x, height(x, w))) {
                return null;
            }
            int h = Ink.TABLE_HEADING + 2;
            int cut = from;
            while (cut < to && h + rowHeight(x, cut, w) <= room) {
                h += rowHeight(x, cut, w);
                cut++;
            }
            if (cut == from || cut == to) {
                return null;
            }
            return new Block[]{new Rows(kind, headingKey, rows, from, cut, continued),
                    new Rows(kind, headingKey, rows, cut, to, true)};
        }

        @Override
        public void paint(Ctx x, int left, int top, int w) {
            final BookCanvas c = x.c;
            String title = x.t(headingKey);
            if (continued) {
                title = x.t("create_stats.book.continued", title);
            }
            final String note = marked() && !continued ? x.t("create_stats.book.note.shape_carries_state") : null;
            Ink.tableHeading(c, left, top, w, title, note);
            final int col = col(x, w);
            int y = top + Ink.TABLE_HEADING + 2;
            for (int i = from; i < to; i++) {
                final Row r = rows.get(i);
                final int h = rowHeight(x, i, w);
                if (r.pulse() != null && r.pulse().equals(x.pulse)) {
                    c.fill(left - 2, y - 2, left + w + 2, y + h - 2, BookTokens.alpha(x.colour(), BookTokens.ROW_TINT_ALPHA));
                    c.fill(left - 2, y - 2, left, y + h - 2, x.colour());
                }
                if (r.mark() != null) {
                    Ink.mark(c, r.mark(), left, y + 1);
                }
                int ty = y + 1;
                for (final String line : c.wrap(term(x, i), col - indent() - 6, true)) {
                    c.text(line, left + indent(), ty, BookTokens.INK, true);
                    ty += BookTokens.LEAD;
                }
                int my = y + 1;
                for (final String line : c.wrap(meaning(x, i), w - col)) {
                    c.text(line, left + col, my, BookTokens.INK, false);
                    my += BookTokens.LEAD;
                }
                if (ruled() && i < to - 1) {
                    c.hline(left, left + w, y + h - 3, BookTokens.ROW_RULE);
                }
                y += h;
            }
        }
    }

    static final class Ratings extends Block {
        private final Part.Ratings part;
        private final boolean ruled;

        Ratings(Part.Ratings part, boolean ruled) {
            this.part = part;
            this.ruled = ruled;
        }

        private boolean twoColumns(Ctx x, int w) {
            if (w < 460) {
                return false;
            }
            final int colW = (w - 16) / 2;
            for (final Part.Rating r : part.rows()) {
                final int need = x.c.width(x.t(r.labelKey()), false) + x.c.width(x.t(r.valueKey(), r.args().get()), false)
                        + 24;
                if (need > colW) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int height(Ctx x, int w) {
            final int rows = twoColumns(x, w) ? (part.rows().size() + 1) / 2 : part.rows().size();
            return Ink.RULED_HEADING + rows * 12 + 4 + BookTokens.LINE;
        }

        @Override
        public void paint(Ctx x, int left, int top, int w) {
            final BookCanvas c = x.c;

            Ink.ruledHeading(c, left, top, w, x.t(part.headingKey()), ruled && top > x.contentTop);
            final boolean two = twoColumns(x, w);
            final int colW = two ? (w - 16) / 2 : w;
            final int y0 = top + Ink.RULED_HEADING;
            final int col = x.colour();
            for (int i = 0; i < part.rows().size(); i++) {
                final Part.Rating r = part.rows().get(i);
                final int cx = left + (two ? (i % 2) * (colW + 16) : 0);
                final int y = y0 + (two ? i / 2 : i) * 12;
                final String label = x.t(r.labelKey());
                final String value = x.t(r.valueKey(), r.args().get());
                final int vw = c.width(value, false);
                c.text(label, cx, y, BookTokens.INK, false);
                c.text(value, cx + colW - 9 - vw, y, BookTokens.INK, false);
                c.dots(cx + c.width(label, false) + 3, cx + colW - 12 - vw, y + 7, BookTokens.RULE, 2);
                Ink.plus(c, cx + colW - 5, y + 1, col);
            }
            final int rows = two ? (part.rows().size() + 1) / 2 : part.rows().size();
            final int ly = y0 + rows * 12 + 4;
            Ink.plus(c, left, ly + 1, col);
            c.text(x.t("create_stats.book.note.read_live"), left + 9, ly, BookTokens.MUTE, false);
        }
    }

    static final class GradesTable extends Block {
        private final Part.Grades part;
        private final int from;
        private final int to;
        private final boolean continued;

        GradesTable(Part.Grades part, int from, int to, boolean continued) {
            this.part = part;
            this.from = from;
            this.to = to;
            this.continued = continued;
        }

        private static final int ICON = 20;
        private static final int GAP = 8;
        private static final int PLUS = 7;

        private static String value(Ctx x, Part.Cell cell) {
            return x.t(cell.valueKey(), cell.args() == null ? new Object[0] : cell.args().get());
        }

        private String label(Ctx x, Part.Grade g) {
            return g.labelKey() != null ? x.t(g.labelKey(), g.labelArgsOrNone()) : x.text.itemName(g.itemId());
        }

        private int[] columns(Ctx x) {
            final int n = part.columns().size();
            final int[] out = new int[n];
            for (int j = 0; j < n; j++) {
                int wide = x.c.width(value(x, part.columns().get(j)), false);
                for (final Part.Grade g : part.rows()) {
                    wide = Math.max(wide, x.c.width(value(x, g.cells().get(j)), false));
                }
                out[j] = wide + PLUS;
            }
            return out;
        }

        private int iconColumn() {
            for (final Part.Grade g : part.rows()) {
                if (g.itemId() != null) {
                    return ICON;
                }
            }
            return 0;
        }

        private int nameWidth(Ctx x, int w) {
            int used = iconColumn();
            for (final int c : columns(x)) {
                used += c + GAP;
            }
            return Math.max(40, w - used);
        }

        private int rowHeight(Ctx x, int i, int w) {
            final int lines = x.c.wrap(label(x, part.rows().get(i)), nameWidth(x, w)).size();
            return Math.max(20, lines * BookTokens.LEAD + 6);
        }

        private int headH() {
            return Ink.TABLE_HEADING + 14;
        }

        @Override
        public int height(Ctx x, int w) {
            int h = headH();
            for (int i = from; i < to; i++) {
                h += rowHeight(x, i, w);
            }
            return h;
        }

        @Override
        public Block[] split(Ctx x, int w, int room) {
            if (keepWhole(x, height(x, w))) {
                return null;
            }
            int h = headH();
            int cut = from;
            while (cut < to && h + rowHeight(x, cut, w) <= room) {
                h += rowHeight(x, cut, w);
                cut++;
            }
            if (cut == from || cut == to) {
                return null;
            }
            return new Block[]{new GradesTable(part, from, cut, continued), new GradesTable(part, cut, to, true)};
        }

        @Override
        public void paint(Ctx x, int left, int top, int w) {
            final BookCanvas c = x.c;
            final String title = x.t(part.headingKey());
            final String note = part.noteKey() == null || continued ? null
                    : x.t(part.noteKey(), part.noteArgs() == null ? new Object[0] : part.noteArgs().get());
            Ink.tableHeading(c, left, top, w, continued ? x.t("create_stats.book.continued", title) : title, note);
            final int[] cols = columns(x);

            final int[] right = new int[cols.length];
            int edge = left + w;
            for (int j = cols.length - 1; j >= 0; j--) {
                right[j] = edge;
                edge -= cols[j] + GAP;
            }
            final int col = x.colour();
            for (int j = 0; j < cols.length; j++) {
                final String head = value(x, part.columns().get(j));
                c.textRight(head, right[j] - PLUS, top + 15, BookTokens.MUTE, false);
                Ink.plus(c, right[j] - 5, top + 16, col);
            }
            c.hline(left, left + w, top + 25, BookTokens.RULE);
            final int nameW = nameWidth(x, w);
            int y = top + headH();
            for (int i = from; i < to; i++) {
                final Part.Grade g = part.rows().get(i);
                final int h = rowHeight(x, i, w);
                if (g.itemId() != null) {
                    c.item(g.itemId(), left, y);
                }
                int ty = y + 4;
                for (final String line : c.wrap(label(x, g), nameW)) {
                    c.text(line, left + iconColumn(), ty, BookTokens.INK, false);
                    ty += BookTokens.LEAD;
                }
                for (int j = 0; j < cols.length; j++) {
                    c.textRight(value(x, g.cells().get(j)), right[j] - PLUS, y + 4, BookTokens.INK, false);
                }
                if (i < to - 1) {
                    c.hline(left, left + w, y + h - 2, BookTokens.ROW_RULE);
                }
                y += h;
            }
        }
    }

    static final class MadeTable extends Block {
        private final Part.Made part;
        private final int from;
        private final int to;
        private final boolean continued;

        MadeTable(Part.Made part, int from, int to, boolean continued) {
            this.part = part;
            this.from = from;
            this.to = to;
            this.continued = continued;
        }

        private static final int ICON = 20;

        private static MadeText.Words words(Ctx x) {
            return new MadeText.Words(x::t, x.text::itemName);
        }

        private static com.createstats.book.content.Recipes.Recipe recipe(Ctx x, String path) {
            return com.createstats.book.content.Recipes.read(path);
        }

        private static int makesWidth(Ctx x, MadeText.Words t, com.createstats.book.content.Recipes.Recipe r, int w) {
            return Math.max(40, w - ICON - x.c.width(t.machine(r), false) - 8);
        }

        private int rowHeight(Ctx x, int i, int w) {
            final MadeText.Words t = words(x);
            final com.createstats.book.content.Recipes.Recipe r = recipe(x, part.recipes().get(i));
            final int first = x.c.wrap(t.makes(r), makesWidth(x, t, r, w)).size();
            final int rest = x.c.wrap(t.from(r), w - ICON).size();
            return Math.max(20, 4 + (first + rest) * BookTokens.LEAD + 3);
        }

        private int headH() {
            return Ink.TABLE_HEADING + 4;
        }

        @Override
        public int height(Ctx x, int w) {
            int h = headH();
            for (int i = from; i < to; i++) {
                h += rowHeight(x, i, w);
            }
            return h;
        }

        @Override
        public Block[] split(Ctx x, int w, int room) {
            if (keepWhole(x, height(x, w))) {
                return null;
            }
            int h = headH();
            int cut = from;
            while (cut < to && h + rowHeight(x, cut, w) <= room) {
                h += rowHeight(x, cut, w);
                cut++;
            }
            if (cut == from || cut == to) {
                return null;
            }
            return new Block[]{new MadeTable(part, from, cut, continued), new MadeTable(part, cut, to, true)};
        }

        @Override
        public void paint(Ctx x, int left, int top, int w) {
            final BookCanvas c = x.c;
            final String title = x.t(part.headingKey());
            final String note = part.noteKey() == null || continued ? null : x.t(part.noteKey());
            Ink.tableHeading(c, left, top, w, continued ? x.t("create_stats.book.continued", title) : title, note);
            c.hline(left, left + w, top + Ink.TABLE_HEADING, BookTokens.RULE);
            final MadeText.Words t = words(x);
            int y = top + headH();
            for (int i = from; i < to; i++) {
                final com.createstats.book.content.Recipes.Recipe r = recipe(x, part.recipes().get(i));
                final int h = rowHeight(x, i, w);
                c.item(r.made().id(), left, y + 1);
                int ty = y + 4;
                final String machine = t.machine(r);
                c.textRight(machine, left + w, ty, BookTokens.MUTE, false);
                for (final String line : c.wrap(t.makes(r), makesWidth(x, t, r, w))) {
                    c.text(line, left + ICON, ty, BookTokens.INK, false);
                    ty += BookTokens.LEAD;
                }
                for (final String line : c.wrap(t.from(r), w - ICON)) {
                    c.text(line, left + ICON, ty, BookTokens.MUTE, false);
                    ty += BookTokens.LEAD;
                }
                if (i < to - 1) {
                    c.hline(left, left + w, y + h - 2, BookTokens.ROW_RULE);
                }
                y += h;
            }
        }
    }

    static final class CurveBlock extends Block {
        private final Sheet sheet;
        private final Part.Curve part;

        CurveBlock(Sheet sheet, Part.Curve part) {
            this.sheet = sheet;
            this.part = part;
        }

        private static final int PLOT_H = 64;

        private static final int ABOVE = 12;
        private static final int BELOW = 13;
        private static final int LEGEND = 12;

        @Override
        public int height(Ctx x, int w) {
            return Ink.TABLE_HEADING + ABOVE + PLOT_H + BELOW + LEGEND * legendRows(x, w - plotLeft(x, w));
        }

        private int plotLeft(Ctx x, int w) {
            double peak = 0.0;
            for (final Part.Line line : part.lines()) {
                for (int px = 0; px < w; px++) {
                    peak = Math.max(peak, at(line, px, 0, w));
                }
            }
            return x.c.width(x.t(part.unitKey(), Num.of(peak)), false) + 5;
        }

        private int legendWidth(Ctx x, Part.Line line) {
            return 14 + x.c.width(x.t(line.labelKey()), false) + 10;
        }

        private int legendRows(Ctx x, int width) {
            int rows = 1;
            int lx = 0;
            for (final Part.Line line : part.lines()) {
                final int need = legendWidth(x, line);
                if (lx + need > width && lx > 0) {
                    rows++;
                    lx = 0;
                }
                lx += need;
            }
            return rows;
        }

        private double at(Part.Line line, int px, int x0, int x1) {
            final double t = x1 - 1 <= x0 ? 0.0 : (px - x0) / (double) (x1 - 1 - x0);
            return line.y().applyAsDouble(part.xMax() * t);
        }

        private static boolean on(Part.LineStyle style, int i) {
            return switch (style) {
                case SUBJECT -> true;
                case DOTTED -> i % 2 == 0;
                case REFERENCE -> i % 5 < 3;
            };
        }

        private static int ink(Part.LineStyle style, int section) {
            return switch (style) {
                case SUBJECT -> section;
                case DOTTED -> BookTokens.INK;
                case REFERENCE -> BookTokens.MUTE;
            };
        }

        @Override
        public void paint(Ctx x, int left, int top, int w) {
            final BookCanvas c = x.c;
            final String note = part.noteKey() == null ? null
                    : x.t(part.noteKey(), part.noteArgs() == null ? new Object[0] : part.noteArgs().get());
            Ink.tableHeading(c, left, top, w, x.t(part.headingKey()), note);

            double peak = 0.0;
            for (final Part.Line line : part.lines()) {
                for (int px = left; px < left + w; px++) {
                    peak = Math.max(peak, at(line, px, left, left + w));
                }
            }
            final String topLabel = x.t(part.unitKey(), Num.of(peak));
            final int x0 = left + c.width(topLabel, false) + 5;
            final int x1 = left + w;
            final int y0 = top + Ink.TABLE_HEADING + ABOVE;
            final int y1 = y0 + PLOT_H;
            final int col = x.colour();
            c.textRight(topLabel, x0 - 4, y0 - 4, BookTokens.MUTE, false);
            c.dots(x0, x1, y0, BookTokens.RULE, 2);
            for (final Part.Tick t : part.ticks()) {
                final int tx = x0 + (int) Math.round((x1 - 1 - x0) * t.x() / part.xMax());
                for (int y = y0 + 2; y < y1; y += 3) {
                    c.fill(tx, y, tx + 1, y + 1, BookTokens.ROW_RULE);
                }
            }
            if (peak > 0.0) {
                for (final Part.Line line : part.lines()) {
                    if (line.style() != Part.LineStyle.SUBJECT) {
                        continue;
                    }
                    for (int px = x0; px < x1; px++) {
                        final int h = (int) Math.round(PLOT_H * at(line, px, x0, x1) / peak);
                        if (h > 0) {
                            c.fill(px, y1 - h, px + 1, y1, BookTokens.alpha(col, BookTokens.ROW_TINT_ALPHA));
                        }
                    }
                }
                for (final Part.Line line : part.lines()) {
                    int prev = Integer.MIN_VALUE;
                    for (int px = x0; px < x1; px++) {
                        final int y = y1 - (int) Math.round(PLOT_H * at(line, px, x0, x1) / peak);
                        if (on(line.style(), px - x0)) {
                            final int from = prev == Integer.MIN_VALUE || line.style() != Part.LineStyle.SUBJECT
                                    ? y : prev;
                            c.fill(px, Math.min(from, y), px + 1, Math.max(from, y) + 1, ink(line.style(), col));
                        }
                        prev = y;
                    }
                }
            }
            c.hline(x0, x1, y1, BookTokens.INK);

            int lastRight = Integer.MIN_VALUE;
            for (final Part.Tick t : part.ticks()) {
                final String label = x.t(t.labelKey(), t.argsOrNone());
                final int tx = x0 + (int) Math.round((x1 - 1 - x0) * t.x() / part.xMax());
                final int lw = c.width(label, false);
                final int lx = Math.max(x0, Math.min(x1 - lw, tx - lw / 2));
                if (lx > lastRight + 3) {
                    c.fill(tx, y1, tx + 1, y1 + 3, BookTokens.INK);
                    c.text(label, lx, y1 + 4, BookTokens.MUTE, false);
                    lastRight = lx + lw;
                }
            }

            int lx = x0;
            int ly = y1 + BELOW + 2;
            for (final Part.Line line : part.lines()) {
                final String label = x.t(line.labelKey());
                final int need = legendWidth(x, line);
                if (lx + need > x1 && lx > x0) {
                    lx = x0;
                    ly += LEGEND;
                }
                for (int i = 0; i < 10; i++) {
                    if (on(line.style(), i)) {
                        c.fill(lx + i, ly + 3, lx + i + 1, ly + 4, ink(line.style(), col));
                    }
                }
                c.text(label, lx + 14, ly, BookTokens.MUTE, false);
                lx += need;
            }

            c.hot("curve:" + sheet.id(), x0, y0, x1, y1 + 1);
            if (x.mouseX >= x0 && x.mouseX < x1 && x.mouseY >= y0 - ABOVE && x.mouseY <= y1 && peak > 0.0) {
                for (final Part.Line line : part.lines()) {
                    if (line.style() != Part.LineStyle.SUBJECT) {
                        continue;
                    }
                    final double v = at(line, x.mouseX, x0, x1);
                    final int vy = y1 - (int) Math.round(PLOT_H * v / peak);
                    c.vline(x.mouseX, y0, y1, BookTokens.RULE);
                    c.fill(x.mouseX - 1, vy - 1, x.mouseX + 2, vy + 2, col);
                    final String reading = x.t(part.unitKey(), Num.of(v));
                    final int rw = c.width(reading, true);
                    final int rx = Math.max(x0, Math.min(x1 - rw, x.mouseX - rw / 2));
                    c.text(reading, rx, y0 - ABOVE + 1, col, true);
                }
            }
        }
    }

    static final class Application extends Block {
        private final Part.Application part;
        private final boolean ruled;

        Application(Part.Application part, boolean ruled) {
            this.part = part;
            this.ruled = ruled;
        }

        @Override
        public boolean deferrable() {
            return true;
        }

        private List<String> caption(Ctx x, int w) {
            return x.c.wrap(x.t(part.captionKey()), w);
        }

        @Override
        public int height(Ctx x, int w) {
            return 24 + 40 + caption(x, w).size() * BookTokens.LEAD - BookTokens.LEAD + BookTokens.LINE;
        }

        @Override
        public void paint(Ctx x, int left, int top, int w) {
            final BookCanvas c = x.c;
            Ink.ruledHeading(c, left, top, w, x.t(part.headingKey()), ruled && top > x.contentTop);
            final int n = part.chain().size();
            final int pitch = n <= 1 ? 0 : Math.min(70, (w - 32) / (n - 1));
            final int span = 16 + pitch * (n - 1);
            final int x0 = left + Math.max(16, (w - span) / 2);
            final int ay = top + 24;
            for (int i = 0; i < n; i++) {
                final Part.Link link = part.chain().get(i);
                final int cx = x0 + i * pitch;
                c.fill(cx - 2, ay - 2, cx + 18, ay + 18, link.subject() ? x.colour() : BookTokens.RULE);
                c.fill(cx - 1, ay - 1, cx + 17, ay + 17, BookTokens.PAPER);
                c.item(link.itemId(), cx, ay);
                c.textCentre(x.t(link.labelKey()), cx + 8, ay + 22, link.subject() ? BookTokens.INK : BookTokens.MUTE,
                        false);
                if (i < n - 1) {
                    c.dots(cx + 20, cx + pitch - 4, ay + 8, BookTokens.INK, 3);
                }
            }
            int y = ay + 40;
            for (final String line : caption(x, w)) {
                c.text(line, left, y, BookTokens.MUTE, false);
                y += BookTokens.LEAD;
            }
        }
    }

    static final class Faults extends Block implements Targets {
        private final Sheet sheet;
        private final Part.Faults part;

        private final int fromRow;
        private final int toRow;

        private final List<String> printed;

        Faults(Sheet sheet, Part.Faults part) {
            this(sheet, part, 0, -1, null);
        }

        private Faults(Sheet sheet, Part.Faults part, int fromRow, int toRow, List<String> printed) {
            this.sheet = sheet;
            this.part = part;
            this.fromRow = fromRow;
            this.toRow = toRow;
            this.printed = printed;
        }

        @Override
        public List<String> targets() {
            if (printed != null) {
                return printed;
            }
            final List<String> out = new ArrayList<>();
            for (int i = 0; i < part.steps().size(); i++) {
                out.add("cause:" + sheet.id() + ":" + (base() + i));
            }
            if (part.endCause()) {
                out.add("cause:" + sheet.id() + ":" + (base() + part.steps().size()));
            }
            return out;
        }

        private List<String> printedBy(Layout l, int from, int to) {
            final List<String> out = new ArrayList<>();
            for (final Box cause : l.causes()) {
                if (cause.line() >= from && cause.line() < to) {
                    out.add("cause:" + sheet.id() + ":" + (base() + cause.row()));
                }
            }
            for (final Box b : l.boxes()) {
                if (b.cause() && b.line() >= from && b.line() < to) {
                    out.add("cause:" + sheet.id() + ":" + (base() + b.row()));
                }
            }
            return out;
        }

        private int base() {
            return com.createstats.book.content.FaultIndex.causeBase(sheet, part);
        }

        private static final int BOX_H = 14;

        private static final int RUN = 22;
        private static final int DROP = 15;

        private record Box(String text, boolean cause, boolean question, int x, int y, int w, List<String> lines,
                           String noteText, List<String> note, int noteY, boolean wrapped, int row, int line) {
            int h() {
                return lines.size() * BookTokens.LEAD + 4;
            }
        }

        private record Layout(List<Box> boxes, List<Box> causes, int height, List<Integer> rowTop,
                              List<Integer> rowBottom) {
            int rows() {
                return rowTop.size();
            }
        }

        private int end(Layout l) {
            return toRow < 0 ? l.rows() : toRow;
        }

        private boolean whole(Layout l) {
            return fromRow == 0 && end(l) == l.rows();
        }

        private Layout layout(Ctx x, int w) {
            final BookCanvas c = x.c;

            final List<String> chain = new ArrayList<>();
            chain.add(x.t(part.symptomKey()));
            for (final Part.Step s : part.steps()) {
                chain.add(x.t(s.questionKey()));
            }
            chain.add(x.t(part.endKey()));
            final int n = chain.size();
            final int[] bx = new int[n];
            final int[] row = new int[n];
            final int[] bw = new int[n];
            int cx = 0;
            int r = 0;
            for (int i = 0; i < n; i++) {
                final boolean bold = i == n - 1 && part.endCause();
                bw[i] = Math.min(w, c.width(chain.get(i), bold) + 10);
                int at = i == 0 ? 0 : cx + RUN;
                if (i > 0 && at + bw[i] > w) {
                    r++;
                    at = RUN;
                }
                bx[i] = at;
                row[i] = r;
                cx = at + bw[i];
            }

            final List<Box> boxes = new ArrayList<>();
            final List<Box> causes = new ArrayList<>();
            final List<Integer> tops = new ArrayList<>();
            final List<Integer> bottoms = new ArrayList<>();
            int y = Ink.TABLE_HEADING + 4;
            int i = 0;
            while (i < n) {
                final int rowIndex = row[i];
                tops.add(y);
                int bottom = y + BOX_H;
                int j = i;
                while (j < n && row[j] == rowIndex) {
                    j++;
                }
                for (int k = i; k < j; k++) {
                    final boolean end = k == n - 1;
                    final boolean question = !end && k > 0;
                    final List<String> lines = List.of(chain.get(k));
                    List<String> note = List.of();
                    String noteText = null;
                    int noteY = y + BOX_H + 3;
                    if (end) {
                        noteText = x.t(part.endNoteKey());
                        note = c.wrap(noteText, w - bx[k]);
                        bottom = Math.max(bottom, noteY + note.size() * BookTokens.LEAD);
                    }
                    boxes.add(new Box(chain.get(k), end && part.endCause(), question || (end && !part.endCause()),
                            bx[k], y, bw[k], lines, noteText, note, noteY, k > 0 && row[k] != row[k - 1],
                            end ? part.steps().size() : -1, rowIndex));
                    if (question) {
                        final Part.Step s = part.steps().get(k - 1);

                        final boolean nextInRow = k + 1 < j;
                        final boolean nextIsEnd = k + 1 == n - 1;
                        int limit = w - bx[k];
                        if (nextInRow && !(nextIsEnd && c.wrap(x.t(part.endNoteKey()), w - bx[k + 1]).size() <= 1)) {
                            limit = bx[k + 1] - 6 - bx[k];
                        }
                        final List<String> leaf = c.wrap(x.t(s.yesKey()), Math.max(40, limit - 10), true);
                        int lw = 0;
                        for (final String line : leaf) {
                            lw = Math.max(lw, c.width(line, true));
                        }
                        final int ly = y + BOX_H + DROP;
                        final String nt = x.t(s.yesNoteKey());
                        final List<String> ln = c.wrap(nt, Math.max(40, limit));
                        final int lh = leaf.size() * BookTokens.LEAD + 4;
                        causes.add(new Box(x.t(s.yesKey()), true, false, bx[k], ly, lw + 10, leaf, nt, ln,
                                ly + lh + 2, false, k - 1, rowIndex));
                        bottom = Math.max(bottom, ly + lh + 2 + ln.size() * BookTokens.LEAD);
                    }
                }
                bottoms.add(bottom);
                y = bottom + 8;
                i = j;
            }
            return new Layout(boxes, causes, y - 8, tops, bottoms);
        }

        private int lift(Layout l) {
            return l.rowTop().get(fromRow) - (Ink.TABLE_HEADING + 4);
        }

        @Override
        public int height(Ctx x, int w) {
            final Layout l = layout(x, w);
            if (whole(l)) {
                return l.height();
            }
            return l.rowBottom().get(end(l) - 1) - lift(l);
        }

        @Override
        public Block[] split(Ctx x, int w, int room) {
            final Layout l = layout(x, w);
            final int end = end(l);
            int cut = -1;
            for (int r = fromRow + 1; r < end; r++) {
                if (l.rowBottom().get(r - 1) - lift(l) <= room) {
                    cut = r;
                }
            }
            if (cut < 0) {
                return null;
            }
            return new Block[]{new Faults(sheet, part, fromRow, cut, printedBy(l, fromRow, cut)),
                    new Faults(sheet, part, cut, end, printedBy(l, cut, end))};
        }

        @Override
        public void paint(Ctx x, int left, int top, int w) {
            final BookCanvas c = x.c;
            final Layout l = layout(x, w);
            final String title = x.t("create_stats.book.part.fault_finding");
            Ink.tableHeading(c, left, top, w, fromRow > 0 ? x.t("create_stats.book.continued", title) : title,
                    x.t("create_stats.book.note.one_question"));

            final int end = end(l);
            top -= lift(l);
            final String yes = x.t("create_stats.book.answer.yes");
            final String no = x.t("create_stats.book.answer.no");
            for (int k = 0; k < l.boxes().size(); k++) {
                final Box b = l.boxes().get(k);
                if (b.line() < fromRow || b.line() >= end) {
                    continue;
                }
                box(x, left + b.x(), top + b.y(), b);
                if (k > 0) {
                    final Box prev = l.boxes().get(k - 1);
                    final int x0 = b.wrapped() ? left : left + prev.x() + prev.w() + 2;
                    final int x1 = left + b.x() - 3;
                    final int ay = top + b.y() + 7;
                    c.hline(x0, x1, ay, BookTokens.INK);
                    Ink.arrowRight(c, x1, ay, BookTokens.INK);
                    if (k > 1 || b.wrapped()) {
                        c.text(no, x0 + 4, ay + 3, BookTokens.MUTE, false);
                    }
                }
                for (int i = 0; i < b.note().size(); i++) {
                    c.text(b.note().get(i), left + b.x(), top + b.noteY() + i * BookTokens.LEAD, BookTokens.MUTE,
                            false);
                }
            }
            for (final Box cause : l.causes()) {
                if (cause.line() < fromRow || cause.line() >= end) {
                    continue;
                }

                final int ax = left + cause.x() + 10;
                final int y0 = top + cause.y() - DROP + 1;
                c.vline(ax, y0, top + cause.y() - 2, BookTokens.INK);
                Ink.arrowDown(c, ax, top + cause.y() - 3, BookTokens.INK);
                c.text(yes, ax + 5, y0 + 2, BookTokens.MUTE, false);
                box(x, left + cause.x(), top + cause.y(), cause);
                for (int i = 0; i < cause.note().size(); i++) {
                    c.text(cause.note().get(i), left + cause.x(), top + cause.noteY() + i * BookTokens.LEAD,
                            BookTokens.MUTE, false);
                }
            }
        }

        private void box(Ctx x, int bx, int by, Box b) {
            final BookCanvas c = x.c;
            final int h = b.h();
            if (b.cause() && ("cause:" + sheet.id() + ":" + (base() + b.row())).equals(x.pulse)) {
                c.outline(bx - 3, by - 3, bx + b.w() + 3, by + h + 3, x.colour());
                c.outline(bx - 2, by - 2, bx + b.w() + 2, by + h + 2, x.colour());
            }
            c.fill(bx, by, bx + b.w(), by + h, BookTokens.INK);
            c.fill(bx + 1, by + 1, bx + b.w() - 1, by + h - 1, b.cause() ? x.colour() : BookTokens.WHITE);
            int ty = by + 3;
            for (final String line : b.lines()) {
                c.text(line, bx + 5, ty, b.cause() ? BookTokens.WHITE : BookTokens.INK, b.cause());
                ty += BookTokens.LEAD;
            }
        }
    }

    static final class SeeAlso extends Block {
        private final Part.SeeAlso part;

        SeeAlso(Part.SeeAlso part) {
            this.part = part;
        }

        @Override
        public boolean deferrable() {
            return true;
        }

        private List<String> present(Ctx x) {
            final List<String> out = new ArrayList<>();
            for (final String id : part.sheetIds()) {
                if (x.firstPage.containsKey(id)) {
                    out.add(id);
                }
            }
            return out;
        }

        private int chipW(Ctx x, String id) {
            final String page = x.firstPage.getOrDefault(id, "8-88");
            return 22 + x.c.width(x.text.itemName("create_stats:" + id), false) + 6 + x.c.width(page, true) + 6;
        }

        private List<int[]> flow(Ctx x, int w) {
            final List<int[]> out = new ArrayList<>();
            final int start = x.c.width(x.t("create_stats.book.part.see_also"), true) + 12;
            int cx = start;
            int row = 0;
            for (final String id : present(x)) {
                final int cw = chipW(x, id);
                if (cx + cw > w && cx > start) {
                    cx = start;
                    row++;
                }
                out.add(new int[]{cx, row * 24, cw});
                cx += cw + 6;
            }
            return out;
        }

        @Override
        public int height(Ctx x, int w) {
            final List<int[]> f = flow(x, w);
            if (f.isEmpty()) {
                return 0;
            }
            return 6 + f.get(f.size() - 1)[1] + 20;
        }

        @Override
        public void paint(Ctx x, int left, int top, int w) {
            final BookCanvas c = x.c;
            final List<String> ids = present(x);
            if (ids.isEmpty()) {
                return;
            }
            if (top > x.contentTop) {
                c.hline(left, left + w, top, BookTokens.RULE);
            }
            c.text(x.t("create_stats.book.part.see_also"), left, top + 12, BookTokens.INK, true);
            final List<int[]> f = flow(x, w);
            for (int i = 0; i < ids.size(); i++) {
                final String id = ids.get(i);
                final int cx = left + f.get(i)[0];
                final int cy = top + 6 + f.get(i)[1];
                final int cw = f.get(i)[2];
                final boolean lit = x.lit("chip:" + id);
                c.fill(cx, cy, cx + cw, cy + 20, lit ? x.colour() : BookTokens.RULE);
                c.fill(cx + 1, cy + 1, cx + cw - 1, cy + 19, BookTokens.WHITE);
                c.item("create_stats:" + id, cx + 3, cy + 2);
                c.text(x.text.itemName("create_stats:" + id), cx + 22, cy + 6, BookTokens.INK, false);
                c.textRight(x.firstPage.get(id), cx + cw - 6, cy + 6, sectionOf(id).colour(), true);
                c.hot("chip:" + id, cx, cy, cx + cw, cy + 20);
            }
        }

        private static com.createstats.book.content.Section sectionOf(String id) {
            return Book.byId(id).map(Sheet::section).orElse(com.createstats.book.content.Section.FAULTS);
        }
    }

    static final class Note extends Block {
        private final Part.Note part;

        Note(Part.Note part) {
            this.part = part;
        }

        private String label(Ctx x) {
            return x.t("create_stats.book.note." + part.kind().name().toLowerCase(Locale.ROOT));
        }

        @Override
        public int height(Ctx x, int w) {
            final int lw = x.c.width(label(x), true) + 6;
            return x.c.wrap(x.t(part.key(), part.argsOrNone()), w - lw - 4).size() * BookTokens.LEAD + 4;
        }

        @Override
        public void paint(Ctx x, int left, int top, int w) {
            final BookCanvas c = x.c;
            final int lw = c.width(label(x), true) + 6;
            final int h = height(x, w);
            c.fill(left, top, left + 2, top + h - 2, x.colour());
            c.text(label(x), left + 5, top + 1, x.colour(), true);
            int y = top + 1;
            for (final String line : c.wrap(x.t(part.key(), part.argsOrNone()), w - lw - 4)) {
                c.text(line, left + lw + 4, y, BookTokens.INK, false);
                y += BookTokens.LEAD;
            }
        }
    }

    static final class StepsBlock extends Block {
        private final Part.Steps part;
        private final int from;
        private final int to;
        private final boolean continued;

        StepsBlock(Part.Steps part, int from, int to, boolean continued) {
            this.part = part;
            this.from = from;
            this.to = to;
            this.continued = continued;
        }

        private static final int TEXT_X = 40;

        private String text(Ctx x, Part.Instruction s) {
            final Object[] args = new Object[s.argKeys().size()];
            for (int i = 0; i < args.length; i++) {
                args[i] = x.t(s.argKeys().get(i));
            }
            return x.t(s.textKey(), args);
        }

        private int rowHeight(Ctx x, int i, int w) {
            final int lines = x.c.wrap(text(x, part.steps().get(i)), w - TEXT_X).size();
            return Math.max(22, lines * BookTokens.LEAD + 8);
        }

        @Override
        public int height(Ctx x, int w) {
            int h = Ink.TABLE_HEADING + 4;
            for (int i = from; i < to; i++) {
                h += rowHeight(x, i, w);
            }
            return h;
        }

        @Override
        public Block[] split(Ctx x, int w, int room) {
            int h = Ink.TABLE_HEADING + 4;
            int cut = from;
            while (cut < to && h + rowHeight(x, cut, w) <= room) {
                h += rowHeight(x, cut, w);
                cut++;
            }
            if (cut == from || cut == to) {
                return null;
            }
            return new Block[]{new StepsBlock(part, from, cut, continued), new StepsBlock(part, cut, to, true)};
        }

        @Override
        public void paint(Ctx x, int left, int top, int w) {
            final BookCanvas c = x.c;
            final String title = x.t(part.headingKey());
            Ink.tableHeading(c, left, top, w, continued ? x.t("create_stats.book.continued", title) : title, null);
            int y = top + Ink.TABLE_HEADING + 4;
            for (int i = from; i < to; i++) {
                final Part.Instruction s = part.steps().get(i);
                final int h = rowHeight(x, i, w);
                c.textRight(String.valueOf(i + 1), left + 10, y + 5, x.colour(), true);
                if (s.itemId() != null) {
                    c.fill(left + 16, y, left + 34, y + 18, BookTokens.RULE);
                    c.fill(left + 17, y + 1, left + 33, y + 17, BookTokens.WHITE);
                    c.item(s.itemId(), left + 17, y + 1);
                }
                int ty = y + 5;
                for (final String line : c.wrap(text(x, s), w - TEXT_X)) {
                    c.text(line, left + TEXT_X, ty, BookTokens.INK, false);
                    ty += BookTokens.LEAD;
                }
                if (i < to - 1) {
                    c.hline(left + TEXT_X, left + w, y + h - 3, BookTokens.ROW_RULE);
                }
                y += h;
            }
        }
    }

    static final class RetiredRows extends Block {
        private final Part.Retired part;
        private final int from;
        private final int to;

        RetiredRows(Part.Retired part, int from, int to) {
            this.part = part;
            this.from = from;
            this.to = to;
        }

        private static final int TEXT_X = 24;

        private int rowHeight(Ctx x, int i, int w) {
            final Part.Unlisted u = part.rows().get(i);
            return Math.max(26, 13 + x.c.wrap(x.t(u.lineKey()), w - TEXT_X).size() * BookTokens.LEAD + 6);
        }

        @Override
        public int height(Ctx x, int w) {
            int h = Ink.TABLE_HEADING + 4;
            for (int i = from; i < to; i++) {
                h += rowHeight(x, i, w);
            }
            return h;
        }

        @Override
        public Block[] split(Ctx x, int w, int room) {
            int h = Ink.TABLE_HEADING + 4;
            int cut = from;
            while (cut < to && h + rowHeight(x, cut, w) <= room) {
                h += rowHeight(x, cut, w);
                cut++;
            }
            if (cut == from || cut == to) {
                return null;
            }
            return new Block[]{new RetiredRows(part, from, cut), new RetiredRows(part, cut, to)};
        }

        @Override
        public void paint(Ctx x, int left, int top, int w) {
            final BookCanvas c = x.c;
            Ink.tableHeading(c, left, top, w, x.t("create_stats.book.part.off_the_menu"), null);
            int y = top + Ink.TABLE_HEADING + 4;
            for (int i = from; i < to; i++) {
                final Part.Unlisted u = part.rows().get(i);
                final int h = rowHeight(x, i, w);
                c.fill(left, y, left + 18, y + 18, BookTokens.RULE);
                c.fill(left + 1, y + 1, left + 17, y + 17, BookTokens.WHITE);
                c.item(u.itemId(), left + 1, y + 1);
                c.text(x.text.itemName(u.itemId()), left + TEXT_X, y + 1, BookTokens.INK, true);
                c.textRight(x.t(u.retired() ? "create_stats.book.retired.state.retired"
                        : "create_stats.book.retired.state.unlisted"), left + w, y + 1, BookTokens.MUTE, true);
                int ty = y + 13;
                for (final String line : c.wrap(x.t(u.lineKey()), w - TEXT_X)) {
                    c.text(line, left + TEXT_X, ty, BookTokens.INK, false);
                    ty += BookTokens.LEAD;
                }
                c.hline(left, left + w, y + h - 3, BookTokens.ROW_RULE);
                y += h;
            }
        }
    }

    static final class PonderRow extends Block {
        private final Part.Ponder part;

        PonderRow(Part.Ponder part) {
            this.part = part;
        }

        private static final int HEAD = 20;
        private static final int ROW = 20;

        private com.createstats.book.content.Scenes.Row row(Ctx x) {
            final List<String> ids = x.ponder.ids(x.section);
            if (!ids.contains(part.itemId())) {
                return null;
            }
            for (final com.createstats.book.content.Scenes.Row r : com.createstats.book.content.Scenes.rows(x.section, ids)) {
                if (r.covers(part.itemId())) {
                    return r;
                }
            }
            return null;
        }

        @Override
        public boolean deferrable() {
            return true;
        }

        @Override
        public int height(Ctx x, int w) {
            return row(x) == null ? 0 : HEAD + ROW - 2;
        }

        @Override
        public void paint(Ctx x, int left, int top, int w) {
            final com.createstats.book.content.Scenes.Row r = row(x);
            if (r == null) {
                return;
            }
            Ink.ruledHeading(x.c, left, top, w, x.t("create_stats.book.part.watch_it_run"), top > x.contentTop);
            ContentsBlocks.ponderRow(x, left, top + HEAD, w, r);
        }
    }

    static final class Break extends Block {
        private final String bandKey;
        private final Supplier<Object[]> bandArgs;
        private final boolean left;
        private final int before;

        Break(String bandKey) {
            this(bandKey, Integer.MAX_VALUE);
        }

        Break(String bandKey, int before) {
            this(bandKey, null, false, before);
        }

        Break(String bandKey, Supplier<Object[]> bandArgs, boolean left) {
            this(bandKey, bandArgs, left, Integer.MAX_VALUE);
        }

        private Break(String bandKey, Supplier<Object[]> bandArgs, boolean left, int before) {
            this.bandKey = bandKey;
            this.bandArgs = bandArgs;
            this.left = left;
            this.before = before;
        }

        @Override
        public Supplier<Object[]> bandArgs() {
            return bandArgs;
        }

        @Override
        public boolean breaksToLeft() {
            return left;
        }

        @Override
        public int height(Ctx x, int w) {
            return 0;
        }

        @Override
        public void paint(Ctx x, int left, int top, int w) {
        }

        @Override
        public boolean isBreak() {
            return true;
        }

        @Override
        public int breakBefore() {
            return before;
        }

        @Override
        public String bandKey() {
            return bandKey;
        }
    }
}
