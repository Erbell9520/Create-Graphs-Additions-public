package com.createstats.book.layout;

import com.createstats.book.content.Section;
import com.createstats.book.content.Sheet;

import java.util.List;
import java.util.Map;

public final class Ctx {
    public interface Ponder {
        List<String> ids(Section section);
    }

    public final BookCanvas c;
    public final BookText text;
    public final BookGeometry geo;
    public final Ponder ponder;

    public Map<String, String> firstPage = Map.of();

    public Map<String, String> controlsPage = Map.of();
    public Map<String, String> figurePage = Map.of();

    public Map<String, String> figureNumber = Map.of();

    public Map<String, String> rowPage = Map.of();

    public Map<Section, String> sectionPage = Map.of();

    public IndexState index = IndexState.REST;

    public int resultsFitted = -1;
    private IndexSearch.Result results;
    private IndexState resultsFor;

    public Section section;
    public Sheet sheet;

    public int pageH;

    public int contentTop;

    public boolean wide;

    public String hover;
    public String pulse;
    public int mouseX = -1;
    public int mouseY = -1;

    public Ctx(BookCanvas c, BookText text, BookGeometry geo, Ponder ponder) {
        this.c = c;
        this.text = text;
        this.geo = geo;
        this.ponder = ponder;
    }

    public int colour() {
        return section.colour();
    }

    public Ctx with(BookCanvas canvas) {
        final Ctx x = new Ctx(canvas, text, geo, ponder);
        x.firstPage = firstPage;
        x.controlsPage = controlsPage;
        x.figurePage = figurePage;
        x.figureNumber = figureNumber;
        x.rowPage = rowPage;
        x.sectionPage = sectionPage;
        x.section = section;
        x.sheet = sheet;
        x.pageH = pageH;
        x.contentTop = contentTop;
        x.wide = wide;
        x.hover = hover;
        x.pulse = pulse;
        x.mouseX = mouseX;
        x.mouseY = mouseY;
        x.index = index;
        return x;
    }

    public boolean lit(String id) {
        return id.equals(hover) || id.equals(pulse);
    }

    public String t(String key, Object... args) {
        return text.get(key, args);
    }

    public IndexSearch.Result results() {
        if (results == null || resultsFor != index) {
            results = IndexSearch.run(text, index);
            resultsFor = index;
        }
        return results;
    }

    public static String figureKey(Sheet sheet, String specimen) {
        return sheet.id() + ":" + specimen;
    }

    public static String controlId(Sheet sheet, String specimen, String control) {
        return "ctl:" + sheet.id() + ":" + specimen + ":" + control;
    }

    public static String controlSheet(String id) {
        final String[] p = id == null ? new String[0] : id.split(":", 4);
        return p.length == 4 && p[0].equals("ctl") ? p[1] : null;
    }

    public static String controlFigure(String id) {
        final String[] p = id == null ? new String[0] : id.split(":", 4);
        return p.length == 4 && p[0].equals("ctl") ? p[1] + ":" + p[2] : null;
    }
}
