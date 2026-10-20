package com.createstats.book.layout;

import com.createstats.book.content.Book;
import com.createstats.book.content.FrontMatter;
import com.createstats.book.content.Part;
import com.createstats.book.content.Section;
import com.createstats.book.content.Sheet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BookPages {
    private BookPages() {
    }

    public static List<Page> build(Ctx x) {
        final Rect content = x.geo.content(0);
        final int w = content.w();
        final int h = content.h();
        x.pageH = h;
        x.wide = !x.geo.spread() && w >= 480;

        final Map<String, String> placeholder = new HashMap<>();
        for (final Sheet s : Book.sheets()) {
            placeholder.put(s.id(), "8-88");
        }
        x.firstPage = placeholder;

        final List<Page> pages = new ArrayList<>();
        final Map<String, String> first = new HashMap<>();
        final Map<String, String> controls = new HashMap<>();
        final Map<String, String> figurePages = new HashMap<>();
        final Map<String, String> figures = new HashMap<>();
        final Map<String, String> rows = new HashMap<>();
        final Map<Section, String> sections = new java.util.EnumMap<>(Section.class);

        final int softBreak = x.geo.spread() ? 1 : 0;
        for (final Section section : Section.values()) {
            final int sectionStart = pages.size();
            x.section = section;
            x.sheet = null;
            if (section != Section.FAULTS) {
                final List<Block> contents = new ArrayList<>(ContentsBlocks.toc(section, Book.in(section)));
                if (section == Section.BASICS) {
                    contents.add(new SheetBlocks.Break("create_stats.book.kind.contents_continued", softBreak));
                    contents.add(SheetBlocks.Rows.terms(x, FrontMatter.FINDING_YOUR_WAY));
                } else {
                    contents.add(new SheetBlocks.Break("create_stats.book.kind.contents_continued", softBreak));
                    contents.add(new ContentsBlocks.PonderList(section, x.ponder.ids(section), false));
                }
                addEntry(x, pages, section, null, Page.Kind.CONTENTS, Paginator.flow(x, contents, w, h), sectionStart,
                        true);
            }
            int figure = 0;
            for (final Sheet sheet : Book.in(section)) {
                x.sheet = sheet;

                final boolean leftAligned = x.geo.spread() && sheet.opensLeft();
                final int at = addEntry(x, pages, section, sheet, Page.Kind.DATA,
                        Paginator.flow(x, SheetBlocks.of(x, sheet), w, h, leftAligned), sectionStart,
                        sheet.opensLeft());
                first.put(sheet.id(), pages.get(at).label());
                for (int i = at; i < pages.size(); i++) {
                    for (final Page.Placed p : pages.get(i).blocks()) {
                        if (p.block() instanceof SheetBlocks.Controls k) {
                            controls.putIfAbsent(Ctx.figureKey(sheet, k.specimen()), pages.get(i).label());
                        }
                        if (p.block() instanceof SheetBlocks.Figure f) {
                            figurePages.putIfAbsent(Ctx.figureKey(sheet, f.specimen()), pages.get(i).label());
                        }
                        if (p.block() instanceof Targets t) {
                            for (final String target : t.targets()) {
                                rows.putIfAbsent(target, pages.get(i).label());
                            }
                        }
                    }
                }

                for (final Part p : sheet.parts()) {
                    if (p instanceof Part.Figure f) {
                        figure++;
                        figures.put(Ctx.figureKey(sheet, f.specimen()), section.number() + "." + figure);
                    }
                }
            }
            if (pages.size() > sectionStart) {
                sections.put(section, pages.get(sectionStart).label());
            }
        }

        if (x.geo.spread() && pages.size() % 2 == 1) {
            final Section last = Section.values()[Section.values().length - 1];
            pages.add(new Page(last, null, Page.Kind.NOTES,
                    List.of(new Page.Placed(new ContentsBlocks.Notes(), 0)), null, 0, 1,
                    label(last, pages.size() - sectionStartOf(pages, last))));
        }
        x.firstPage = first;
        x.controlsPage = controls;
        x.figurePage = figurePages;
        x.figureNumber = figures;
        x.rowPage = rows;
        x.sectionPage = sections;
        x.section = null;
        x.sheet = null;
        return pages;
    }

    private static int addEntry(Ctx x, List<Page> pages, Section section, Sheet sheet, Page.Kind kind,
                                List<Paginator.Run> runs, int sectionStart, boolean opensLeft) {
        if (x.geo.spread() && opensLeft && pages.size() % 2 == 1) {
            pages.add(new Page(section, null, Page.Kind.NOTES,
                    List.of(new Page.Placed(new ContentsBlocks.Notes(), 0)), null, 0, 1,
                    label(section, pages.size() - sectionStart)));
        }
        final int at = pages.size();
        for (int i = 0; i < runs.size(); i++) {
            final Paginator.Run r = runs.get(i);
            pages.add(new Page(section, sheet, kind, r.blocks(), r.bandKey(), i, runs.size(),
                    label(section, pages.size() - sectionStart), r.bandArgs()));
        }
        return at;
    }

    private static int sectionStartOf(List<Page> pages, Section section) {
        for (int i = 0; i < pages.size(); i++) {
            if (pages.get(i).section() == section) {
                return i;
            }
        }
        return pages.size();
    }

    private static String label(Section section, int indexInSection) {
        return String.format(Locale.ROOT, "%d-%02d", section.number(), indexInSection + 1);
    }

    public static int find(List<Page> pages, String entryId) {
        for (int i = 0; i < pages.size(); i++) {
            if (pages.get(i).entryId().equals(entryId)) {
                return i;
            }
        }
        return -1;
    }

    public static int find(List<Page> pages, String entryId, int index) {
        final int start = find(pages, entryId);
        if (start < 0) {
            return -1;
        }
        int at = start;
        while (at + 1 < pages.size() && pages.get(at + 1).entryId().equals(entryId)
                && pages.get(at + 1).index() <= index) {
            at++;
        }
        return at;
    }

    public static int byLabel(List<Page> pages, String label) {
        if (label == null) {
            return -1;
        }
        for (int i = 0; i < pages.size(); i++) {
            if (pages.get(i).label().equals(label)) {
                return i;
            }
        }
        return -1;
    }

    public static int sectionStart(List<Page> pages, Section section) {
        for (int i = 0; i < pages.size(); i++) {
            if (pages.get(i).section() == section) {
                return i;
            }
        }
        return 0;
    }
}
