package com.createstats.book;

import com.createstats.book.content.BackMatter;
import com.createstats.book.content.Book;
import com.createstats.book.content.FrontMatter;
import com.createstats.book.content.Mark;
import com.createstats.book.content.Part;
import com.createstats.book.content.Scenes;
import com.createstats.book.content.Section;
import com.createstats.book.content.Sheet;
import com.createstats.book.content.Symptom;
import com.createstats.book.layout.Callouts;
import com.createstats.book.specimen.SpecimenAnatomy;
import com.createstats.book.specimen.Specimens;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookContentTest {
    private static final Path BOOK_SRC = Path.of("src/main/java/com/createstats/book");

    private static final Map<String, String> DIGIT_ALLOWED = Map.of();

    private static final Map<String, String> PENDING_LINKS = Map.of(
            BookLinks.INVENTORY_DISPLAY, "Phase 4, Stats: the Inventory Display's sheet");

    @Test
    void everyKeyTheBookNamesIsWritten() throws IOException {
        final Map<String, String> lang = BookFixtures.lang();
        final List<String> missing = new ArrayList<>();
        for (final String key : namedKeys()) {
            if (!lang.containsKey(key)) {
                missing.add(key);
            }
        }
        assertTrue(missing.isEmpty(), "Keys the book names that en_us.json lacks: " + missing);
    }

    @Test
    void noBookKeyIsOrphaned() throws IOException {
        final Set<String> named = namedKeys();
        final List<String> orphans = new ArrayList<>();
        for (final String key : new TreeSet<>(BookFixtures.lang().keySet())) {
            if (key.startsWith("create_stats.book.") && !named.contains(key)) {
                orphans.add(key);
            }
        }
        assertTrue(orphans.isEmpty(), "create_stats.book keys nothing reads: " + orphans);
    }

    static Set<String> namedKeys() throws IOException {
        final Set<String> out = new HashSet<>();
        for (final Section s : Section.values()) {
            out.add(s.nameKey());
            out.add(s.hookKey());
        }
        for (final Sheet sheet : Book.sheets()) {
            out.add(sheet.titleKey());
            out.add(sheet.hookKey());
            out.add(sheet.groupKey());
            for (final Part p : sheet.parts()) {
                out.addAll(keysOf(p));
            }
        }

        for (final Symptom s : Symptom.values()) {
            out.add(s.nameKey());
        }
        for (final Mark m : Mark.values()) {
            final String key = "create_stats.book.mark." + m.name().toLowerCase(java.util.Locale.ROOT);
            out.add(key);
            out.add(key + ".means");
        }
        out.addAll(keysOf(FrontMatter.FINDING_YOUR_WAY));
        for (final Scenes.Row r : Scenes.all()) {
            out.add(r.labelKey());
        }
        final Pattern literal = Pattern.compile("\"((?:create_stats\\.book|gui\\.create_stats|item\\.create_stats\\.students_manual)\\.[a-z0-9_.]+)\"");
        try (Stream<Path> files = Files.walk(BOOK_SRC)) {
            for (final Path f : files.filter(x -> x.toString().endsWith(".java")).toList()) {
                final Matcher m = literal.matcher(Files.readString(f, StandardCharsets.UTF_8));
                while (m.find()) {
                    if (!m.group(1).endsWith(".")) {
                        out.add(m.group(1));
                    }
                }
            }
        }

        for (final Part.NoteKind k : Part.NoteKind.values()) {
            out.add("create_stats.book.note." + k.name().toLowerCase(java.util.Locale.ROOT));
        }
        out.add("item.create_stats.students_manual");
        out.add("item.create_stats.students_manual.tooltip");
        out.add("item.create_stats.students_manual.tooltip.usage");
        return out;
    }

    private static List<String> keysOf(Part p) {
        final List<String> k = new ArrayList<>();
        switch (p) {
            case Part.Figure f -> {
                k.add(f.headingKey());
                k.add(f.captionKey());
            }
            case Part.Controls c -> c.rows().forEach(r -> {
                k.add(r.nameKey());
                k.add(r.functionKey());
                if (r.keysKey() != null) {
                    k.add(r.keysKey());
                }
            });
            case Part.World w -> w.rows().forEach(r -> {
                k.add(r.gestureKey());
                k.add(r.effectKey());
            });
            case Part.Status s -> {
                k.add(s.headingKey());
                s.rows().forEach(r -> {
                    k.add(r.wordKey());
                    k.add(r.meaningKey());
                });
            }
            case Part.Ratings r -> {
                k.add(r.headingKey());
                r.rows().forEach(x -> {
                    k.add(x.labelKey());
                    k.add(x.valueKey());
                });
            }
            case Part.Grades g -> {
                k.add(g.headingKey());
                if (g.noteKey() != null) {
                    k.add(g.noteKey());
                }
                g.columns().forEach(c -> k.add(c.valueKey()));
                g.rows().forEach(r -> {
                    if (r.labelKey() != null) {
                        k.add(r.labelKey());
                    }
                    r.cells().forEach(c -> k.add(c.valueKey()));
                });
            }
            case Part.Curve cv -> {
                k.add(cv.headingKey());
                if (cv.noteKey() != null) {
                    k.add(cv.noteKey());
                }
                k.add(cv.unitKey());
                cv.ticks().forEach(t -> k.add(t.labelKey()));
                cv.lines().forEach(l -> k.add(l.labelKey()));
            }
            case Part.Application a -> {
                k.add(a.headingKey());
                a.chain().forEach(l -> k.add(l.labelKey()));
                k.add(a.captionKey());
            }
            case Part.Faults fl -> {
                k.add(fl.symptomKey());
                fl.steps().forEach(s -> {
                    k.add(s.questionKey());
                    k.add(s.yesKey());
                    k.add(s.yesNoteKey());
                });
                k.add(fl.endKey());
                k.add(fl.endNoteKey());
            }
            case Part.SeeAlso sa -> {
            }
            case Part.Note n -> k.add(n.key());
            case Part.Break b -> k.add(b.bandKey());
            case Part.Steps st -> {
                k.add(st.headingKey());
                st.steps().forEach(s -> {
                    k.add(s.textKey());
                    k.addAll(s.argKeys());
                });
            }
            case Part.Terms t -> {
                k.add(t.headingKey());
                t.rows().forEach(r -> {
                    k.add(r.termKey());
                    k.add(r.meaningKey());
                });
            }
            case Part.Marks m -> k.add(m.headingKey());
            case Part.Retired r -> r.rows().forEach(u -> k.add(u.lineKey()));
            case Part.FaultIndex fi -> {
            }
            case Part.AtoZ az -> {
            }
            case Part.Ponder pd -> {
            }
            case Part.Made md -> {
                k.add(md.headingKey());
                if (md.noteKey() != null) {
                    k.add(md.noteKey());
                }
                for (final String path : md.recipes()) {
                    k.addAll(com.createstats.book.layout.MadeText.keys(com.createstats.book.content.Recipes.read(path)));
                }
            }
        }
        return k;
    }

    @Test
    void everyFaultWordNamesItsSymptom() {
        final List<String> bad = new ArrayList<>();
        for (final Sheet sheet : Book.sheets()) {
            for (final Part p : sheet.parts()) {
                if (p instanceof Part.Status s) {
                    for (final Part.Word w : s.rows()) {
                        final boolean running = w.mark() == Mark.RUNNING;
                        if (running && w.symptom() != null) {
                            bad.add(sheet.id() + " " + w.wordKey() + ": a running word filed as " + w.symptom());
                        }
                        if (!running && w.symptom() == null) {
                            bad.add(sheet.id() + " " + w.wordKey() + ": a " + w.mark() + " word with no symptom");
                        }
                    }
                }
                if (p instanceof Part.Faults f && f.symptom() == null) {
                    bad.add(sheet.id() + ": fault finding with no symptom");
                }
            }
        }
        assertTrue(bad.isEmpty(), String.join("\n", bad));
    }

    @Test
    void bookProseHoldsNoDigits() throws IOException {
        final List<String> typed = new ArrayList<>();
        for (final Map.Entry<String, String> e : BookFixtures.lang().entrySet()) {
            if (e.getKey().startsWith("create_stats.book.") && e.getValue().matches(".*\\d.*")
                    && !DIGIT_ALLOWED.containsKey(e.getKey())) {
                typed.add(e.getKey() + " = " + e.getValue());
            }
        }
        assertTrue(typed.isEmpty(), "Book prose with a typed figure (take it from code as an argument): " + typed);
    }

    @Test
    void everyRatingResolvesWithoutAWorld() throws IOException {
        final Map<String, String> lang = BookFixtures.lang();
        final List<String> bad = new ArrayList<>();
        for (final Sheet sheet : Book.sheets()) {
            for (final Part p : sheet.parts()) {
                if (p instanceof Part.Ratings r) {
                    for (final Part.Rating row : r.rows()) {
                        check(sheet.id() + " " + row.labelKey(), lang.get(row.valueKey()), row.args().get(), bad);
                    }
                }
                if (p instanceof Part.Controls c) {
                    for (final Part.Control row : c.rows()) {
                        if (row.keysKey() != null) {
                            check(sheet.id() + " " + row.id(), lang.get(row.keysKey()),
                                    row.keysArgs() == null ? new Object[0] : row.keysArgs().get(), bad);
                        }
                    }
                }
                if (p instanceof Part.Grades g) {
                    if (g.noteKey() != null) {
                        check(sheet.id() + " " + g.noteKey(), lang.get(g.noteKey()),
                                g.noteArgs() == null ? new Object[0] : g.noteArgs().get(), bad);
                    }
                    for (final Part.Cell c : g.columns()) {
                        check(sheet.id() + " " + c.valueKey(), lang.get(c.valueKey()),
                                c.args() == null ? new Object[0] : c.args().get(), bad);
                    }
                    for (final Part.Grade r : g.rows()) {
                        for (final Part.Cell c : r.cells()) {
                            check(sheet.id() + " " + r.itemId() + " " + c.valueKey(), lang.get(c.valueKey()),
                                    c.args() == null ? new Object[0] : c.args().get(), bad);
                        }

                        if (r.labelKey() != null) {
                            check(sheet.id() + " " + r.labelKey(), lang.get(r.labelKey()), r.labelArgsOrNone(), bad);
                        }
                    }
                }
                if (p instanceof Part.Note n) {
                    check(sheet.id() + " " + n.key(), lang.get(n.key()), n.argsOrNone(), bad);
                }
                if (p instanceof Part.Curve cv) {
                    for (final Part.Tick tick : cv.ticks()) {
                        check(sheet.id() + " " + tick.labelKey(), lang.get(tick.labelKey()), tick.argsOrNone(), bad);
                    }
                    if (cv.noteKey() != null) {
                        check(sheet.id() + " " + cv.noteKey(), lang.get(cv.noteKey()),
                                cv.noteArgs() == null ? new Object[0] : cv.noteArgs().get(), bad);
                    }

                    for (final Part.Line l : cv.lines()) {
                        for (final double at : new double[]{0.0, cv.xMax() / 2, cv.xMax()}) {
                            check(sheet.id() + " " + l.labelKey(), lang.get(cv.unitKey()),
                                    new Object[]{com.createstats.book.content.Num.of(l.y().applyAsDouble(at))}, bad);
                        }
                    }
                }
                if (p instanceof Part.Status st) {
                    for (final Part.Word w : st.rows()) {
                        check(sheet.id() + " " + w.wordKey(), lang.get(w.wordKey()), w.argsOrNone(), bad);
                    }
                }
            }
        }
        assertTrue(bad.isEmpty(), "Ratings that do not resolve: " + bad);
    }

    private static void check(String where, String format, Object[] args, List<String> bad) {
        if (format == null) {
            bad.add(where + ": no format");
            return;
        }
        final int wanted = format.split("%s", -1).length - 1;
        if (wanted != args.length) {
            bad.add(where + ": format takes " + wanted + ", code gives " + args.length);
            return;
        }

        final String out = args.length == 0 ? format : String.format(java.util.Locale.ROOT, format, args);
        final int percents = args.length == 0 ? 0 : format.split("%%", -1).length - 1;
        if (out.chars().filter(ch -> ch == '%').count() != percents || out.isBlank()) {
            bad.add(where + ": " + out);
        }
    }

    @Test
    void theMeterRatingsMatchTheirConstants() throws IOException {
        final BookFixturesText t = new BookFixturesText();
        final List<String> values = new ArrayList<>();
        for (final Part p : com.createstats.book.content.PowerSheets.POWER_METER.parts()) {
            if (p instanceof Part.Ratings r) {
                for (final Part.Rating row : r.rows()) {
                    values.add(t.text.get(row.valueKey(), row.args().get()));
                }
            }
        }
        assertEquals(List.of(com.createstats.gui.meter.MeterHistory.SIZE + " samples a span", "1, 5, 15 minutes",
                "hold 1 s, or sneak right-click", "0 to 15", "2 a second, while flowing"), values);
    }

    @Test
    void theGeneratorsPrintTheirDesignedFigures() throws IOException {
        final com.createstats.book.layout.BookText t = BookFixtures.text(BookFixtures.lang());
        assertEquals(List.of("270 FE/t", "8,192 SU", "0.0330", "360 FE/t", "16,384 SU", "0.0220", "540 FE/t",
                "19,661 SU", "0.0275"), cells(t, com.createstats.book.content.GenerateSheets.DYNAMOS));
        assertEquals(List.of("x0.60", "19 FE/t", "216 FE/t", "x1.00", "32 FE/t", "360 FE/t", "x1.60", "51 FE/t",
                "576 FE/t"), cells(t, com.createstats.book.content.GenerateSheets.SOLAR_PANELS));
        assertEquals(List.of("1 s", "3,600 FE", "1 brick", "2 s", "7,200 FE", "2 bricks", "4 s", "14,400 FE",
                "4 bricks", "5 s", "18,000 FE", "5 bricks", "9 s", "32,400 FE", "none", "10 s", "36,000 FE", "none",
                "18 s", "64,800 FE", "none", "30 s", "108,000 FE", "none", "36 s", "129,600 FE", "none"),
                cells(t, com.createstats.book.content.GenerateSheets.BIOFUEL_BURNER));
        assertEquals(List.of("64,000 FE", "4,096 FE/t, every face together", "192 RPM"),
                ratings(t, com.createstats.book.content.GenerateSheets.DYNAMOS));
        assertEquals(List.of("180 FE/t", "16,000 FE", "1,024 FE/t, every face together", "64 items, one kind", "0 to 15"),
                ratings(t, com.createstats.book.content.GenerateSheets.BIOFUEL_BURNER));
        assertEquals(List.of("x1.25 at 9 panels", "x1.50 at 81 panels", "10 of every 20 minutes", "32% of noon",
                "x0.35", "x0.15", "4,000 FE"), ratings(t, com.createstats.book.content.GenerateSheets.SOLAR_PANELS));

        for (final Part p : com.createstats.book.content.GenerateSheets.SOLAR_PANELS.parts()) {
            if (p instanceof Part.Curve cv) {
                final double noon = cv.lines().get(0).y().applyAsDouble(cv.xMax() / 4);
                assertEquals(cv.lines().get(2).y().applyAsDouble(0), noon, 1.0e-9);
                assertEquals(360.0, noon, 1.0e-9);
            }
        }
    }

    private static List<String> cells(com.createstats.book.layout.BookText t, Sheet sheet) {
        final List<String> out = new ArrayList<>();
        for (final Part p : sheet.parts()) {
            if (p instanceof Part.Grades g) {
                for (final Part.Grade r : g.rows()) {
                    for (final Part.Cell c : r.cells()) {
                        out.add(t.get(c.valueKey(), c.args() == null ? new Object[0] : c.args().get()));
                    }
                }
            }
        }
        return out;
    }

    @Test
    void theStoresPrintTheirDesignedFigures() throws IOException {
        final com.createstats.book.layout.BookText t = BookFixtures.text(BookFixtures.lang());
        assertEquals(List.of("10,485,760 FE", "65,536 FE/t, falling to nothing over 16 s", "a shortfall held 5 s",
                "3,072 FE/t, only while nothing else is short", "2:50, minutes and seconds", "0 to 15"),
                ratings(t, com.createstats.book.content.StoreSheets.STARTER_CAPACITOR));
        assertEquals(List.of("1", "1,024,000 FE", "1,024,000 FE", "9", "11,480,656 FE", "1,275,628 FE", "27",
                "38,441,480 FE", "1,423,758 FE", "54", "82,401,116 FE", "1,525,946 FE"),
                cells(t, com.createstats.book.content.StoreSheets.CAPACITOR_BANK));
        assertEquals(List.of("One block", "3 by 3 by 1", "3 by 3 by 3", "3 by 3 by 6"),
                gradeLabels(t, com.createstats.book.content.StoreSheets.CAPACITOR_BANK));
        assertEquals(List.of("32,768 FE/t a connection, however big", "3 wide, 3 deep, 6 tall",
                "1, the core, to 9; new banks 5"), ratings(t, com.createstats.book.content.StoreSheets.CAPACITOR_BANK));
        assertEquals(List.of("3 inputs, 3 outputs", "131,072 FE/t, its wires adding",
                "65,536 FE/t, and no more than its wire", "3", "393,216 FE", "10 / 30 / 60 min of Critical",
                "floor 10%, band top 30%, healthy 60%", "60% of generation, at the floor", "the next 30 min"),
                ratings(t, com.createstats.book.content.StoreSheets.BATTERY_CONTROLLER));
        assertEquals(List.of("256 FE/t to 32,768 FE/t", "15, each row", "charges at 8,192 FE/t, keeps 50% behind",
                "5 points above it", "1 to 15 docked, 0 with nothing"),
                ratings(t, com.createstats.book.content.StoreSheets.PORTABLE_INTERFACE));

        final List<String> words = new ArrayList<>();
        for (final Sheet s : com.createstats.book.content.StoreSheets.all()) {
            for (final Part p : s.parts()) {
                if (p instanceof Part.Status st) {
                    for (final Part.Word w : st.rows()) {
                        if (w.args() != null) {
                            words.add(t.get(w.wordKey(), w.argsOrNone()));
                        }
                    }
                }
            }
        }
        assertEquals(List.of("Charging, full in 1:25", "Grid short: firing in 4.0 s",
                "Discharging 65,434 FE/t, 16.0 s left", "Burned off in the gap: 2,621,440 FE", "Charging: 360 FE/t",
                "Draining: 360 FE/t", "In 360 FE/t, out 360 FE/t", "Full in ~44:29", "Empty in ~44:29",
                "Managed by a Battery Controller: full in ~44:29", "Managed by a Battery Controller: settles near 30%",
                "Pooled across 27 blocks, fills bottom up", "Holding 518 FE/t back to refill the banks",
                "Holding 25% back for a board upstream (the floor is 10% without it)",
                "Input A is wired to a panel leg, which carries nothing", "Charging 8,192 FE/t", "Drawing 8,192 FE/t",
                "Paused: station charge is down to 50%", "Paused: train charge is down to 50%"), words);

        for (final Part p : com.createstats.book.content.StoreSheets.STARTER_CAPACITOR.parts()) {
            if (p instanceof Part.Curve cv) {
                assertEquals(65_434.0, cv.lines().get(0).y().applyAsDouble(0), 1.0e-9);
                assertEquals(0.0, cv.lines().get(0).y().applyAsDouble(cv.xMax()), 1.0e-9);
            }
        }
    }

    @Test
    void theBoardPrintsItsDesignedFigures() throws IOException {
        final com.createstats.book.layout.BookText t = BookFixtures.text(BookFixtures.lang());
        assertEquals(List.of("9: 8 studs on the bus, and the back boss", "65,536 FE/t on studs 1 and 2",
                "32,768 FE/t, and no more than its best wire",
                "4", "65,536 FE/t, and no more than its cable", "65,536 FE/t", "512,000 FE", "51 FE/t, out of its buffer",
                "15% of the buffer", "the buffer over 45%, or a bank charging", "10% of generation",
                "the buffer above the floor, over 30 s", "3 s", "one every 1 s",
                "kept for Critical under 20% of the banks"), ratings(t, com.createstats.book.content.ControlSheets.PDU));
        final List<String> words = new ArrayList<>();
        for (final Part p : com.createstats.book.content.ControlSheets.PDU.parts()) {
            if (p instanceof Part.Status st) {
                for (final Part.Word w : st.rows()) {
                    if (w.args() != null) {
                        words.add(t.get(w.wordKey(), w.argsOrNone()));
                    }
                }
            }
        }
        assertEquals(List.of("AUTO, MANUAL, OFF", "9 SERVED 2 SHED", "Mode: Active Auto", "Branch: 9 served, 2 shed",
                "Gen 720 FE/t, running 802 of 1,506", "Leg 3 runs to a Battery Controller input and carries nothing",
                "Leg 3 runs to another panel's stud and carries nothing",
                "Running", "Pinned on", "Off by you", "No supply", "Wire full", "Breaker", "Floor hold", "Below tier",
                "No spare", "Queued", "Anti-flap", "Held back", "Dimming", "Headroom", "Wire short", "Charging", "Draining",
                "Full",
                "Idle",

                "Live", "At capacity", "TRIPPED", "Open", "Not wired",
                "Board on this leg. No spare to export: its critical still runs",
                "Short ... FE before the supply is back", "Learning the day: 0 of 24 hours watched",
                "From buffer", "Shed 704 FE/t", "2 devices (704 FE/t) shed: 720 FE/t of generation stops short of them.",
                "Night outlook: ... FE short before supply is back; asking the boards on its legs to hold it.",
                "Unpriced feed", "Source buffers", "Holding ... FE back for a board upstream."), words);
    }

    @Test
    void theCarriersPrintTheirDesignedFigures() throws IOException {
        final com.createstats.book.layout.BookText t = BookFixtures.text(BookFixtures.lang());
        assertEquals(List.of("x0.50", "2 px", "4,096 FE/t", "x1.00", "4 px", "8,192 FE/t", "x2.00", "6 px",
                "16,384 FE/t", "x4.00", "7 px", "32,768 FE/t"), cells(t, com.createstats.book.content.CarrySheets.CABLES));
        assertEquals(List.of("25 FE", "48 blocks", "1,024 blocks"),
                ratings(t, com.createstats.book.content.CarrySheets.CABLES));
        assertEquals(List.of("1,024 FE/t, the whole run", "x0.125 the base", "8 FE each length",
                "through up to 8 blocks"), ratings(t, com.createstats.book.content.CarrySheets.INDOOR));
        assertEquals(List.of("6 studs, 3 channels", "8,192 FE/t on Copper Wire", "64 FE"),
                ratings(t, com.createstats.book.content.CarrySheets.JUNCTIONS));
        assertEquals(List.of("512 FE/t, the whole plate", "5 blocks", "1,024 FE", "every 2 s"),
                ratings(t, com.createstats.book.content.CarrySheets.OUTLETS));

        final List<String> words = new ArrayList<>();
        for (final Sheet s : com.createstats.book.content.CarrySheets.all()) {
            for (final Part p : s.parts()) {
                if (p instanceof Part.Status st) {
                    for (final Part.Word w : st.rows()) {
                        if (w.args() != null) {
                            words.add(t.get(w.wordKey(), w.argsOrNone()));
                        }
                    }
                }
            }
        }
        assertEquals(List.of("Carrying: 360 FE/t", "Capacity: 4,096 FE/t", "Carrying: 4,096 FE/t",
                "Run of 2 throttled to 4,096 FE/t by a thinner grade", "Branch held to 8,192 FE/t by an inline limiter",
                "Laid 2 cable.", "Need 2 cable, you have 1.", "Pulled up 4 cable.",
                "Carrying: 360 FE/t", "Capacity: 1,024 FE/t", "Run of 4 segments, 1 bore from here",
                "Bored through 1 block.", "No matching cable within 8 blocks that way.",
                "J00 OK", "J10 No bus", "J11 Open circuit", "J20 Source starved", "J30 Overload", "J31 Buffer full",
                "J40 Isolated", "Flow: 360 / 4,096 FE/t",
                "Needs 512, getting 0 FE/t", "Plugged in. 512 FE/t through the socket."), words);

        for (final Part p : com.createstats.book.content.CarrySheets.JUNCTIONS.parts()) {
            if (p instanceof Part.Note n && n.key().endsWith("junctions.note.card")) {
                assertEquals("The card also lists J12, J21 and J32. No box sets them, so none will ever show.",
                        t.get(n.key(), n.argsOrNone()));
            }
        }
    }

    @Test
    void theConnectorsAndWiresPrintTheirDesignedFigures() throws IOException {
        final com.createstats.book.layout.BookText t = BookFixtures.text(BookFixtures.lang());
        final Sheet connectors = com.createstats.book.content.WireSheets.CONNECTORS;
        final Sheet wires = com.createstats.book.content.WireSheets.WIRES;
        assertEquals(List.of("16,384 FE/t", "4", "Insulated Wire", "32,768 FE/t", "4", "Shielded Wire",
                "65,536 FE/t", "6", "HV Line"), cells(t, connectors));
        assertEquals(List.of("16,000 FE", "32 blocks", "8 blocks", "0.3 s", "5 px"), ratings(t, connectors));
        assertEquals(List.of("8,192 FE/t", "1.6 px", "Power", "16,384 FE/t", "2 px", "Power", "32,768 FE/t",
                "2.4 px", "Advanced", "65,536 FE/t", "3 px", "Industrial",

                "8.7%", "2.8 blocks", "3.9%", "1.2 blocks", "2.4%", "0.8 blocks", "1.2%", "0.4 blocks"),
                cells(t, wires));
        final List<String> words = new ArrayList<>();
        for (final Sheet s : com.createstats.book.content.WireSheets.all()) {
            for (final Part p : s.parts()) {
                if (p instanceof Part.Status st) {
                    for (final Part.Word w : st.rows()) {
                        if (w.args() != null) {
                            words.add(t.get(w.wordKey(), w.argsOrNone()));
                        }
                    }
                }
            }
        }
        assertEquals(List.of("Carrying: 360 FE/t", "Feeding block: 360 FE/t", "Drawing from block: 360 FE/t",
                "Capacity: 8,192 FE/t", "Carrying: 8,192 FE/t", "Branch held to 8,192 FE/t by an inline limiter",
                "4 of 6 wires: 1 Copper Wire, 1 Insulated Wire, 1 Shielded Wire, 1 HV Line",
                "1 bridging to another mod's network", "Rated 16,384 FE/t, its grade",
                "Rated 16,384 FE/t; its wiring carries 8,192", "Cable touching it: 4,096 FE/t",
                "Too far. Copper wire reaches 32 blocks.", "Stripped 1 wire(s) off the terminal."), words);
    }

    private static List<String> gradeLabels(com.createstats.book.layout.BookText t, Sheet sheet) {
        final List<String> out = new ArrayList<>();
        for (final Part p : sheet.parts()) {
            if (p instanceof Part.Grades g) {
                for (final Part.Grade r : g.rows()) {
                    out.add(t.get(r.labelKey(), r.labelArgsOrNone()));
                }
            }
        }
        return out;
    }

    private static List<String> ratings(com.createstats.book.layout.BookText t, Sheet sheet) {
        final List<String> out = new ArrayList<>();
        for (final Part p : sheet.parts()) {
            if (p instanceof Part.Ratings r) {
                for (final Part.Rating row : r.rows()) {
                    out.add(t.get(row.valueKey(), row.args().get()));
                }
            }
        }
        return out;
    }

    private static final class BookFixturesText {
        final com.createstats.book.layout.BookText text;

        BookFixturesText() throws IOException {
            this.text = BookFixtures.text(BookFixtures.lang());
        }
    }

    @Test
    void everyFigureIsPinnedOnceAndTabledOnce() {
        final List<String> bad = new ArrayList<>();
        for (final String id : Specimens.ids()) {
            final SpecimenAnatomy a = Specimens.anatomy(id).orElseThrow();
            for (final SpecimenAnatomy.Hot h : a.controls()) {
                if (h.x() < 0 || h.y() < 0 || h.x() + h.w() > a.width() || h.y() + h.h() > a.height() || h.w() <= 0
                        || h.h() <= 0) {
                    bad.add(id + ": " + h + " is not inside the " + a.width() + " x " + a.height() + " panel");
                }
            }
            final Map<String, Integer> n = Callouts.numbering(a);
            final Set<Integer> seen = new TreeSet<>(n.values());

            if (!a.controls().isEmpty() && (seen.size() != a.controls().size() || !seen.contains(1)
                    || !seen.contains(a.controls().size()))) {
                bad.add(id + ": pin numbers " + n + " are not 1 to " + a.controls().size() + " once each");
            }
        }
        for (final Sheet sheet : Book.sheets()) {
            for (final Part p : sheet.parts()) {
                if (!(p instanceof Part.Controls c)) {
                    continue;
                }
                final SpecimenAnatomy a = Specimens.anatomy(c.specimen()).orElse(null);
                if (a == null) {
                    bad.add(sheet.id() + ": no specimen " + c.specimen());
                    continue;
                }
                final List<String> rows = c.rows().stream().map(Part.Control::id).toList();
                final List<String> controls = a.controls().stream().map(SpecimenAnatomy.Hot::id).toList();
                if (rows.size() != new HashSet<>(rows).size()) {
                    bad.add(sheet.id() + ": a control has two rows: " + rows);
                }
                if (!new HashSet<>(rows).equals(new HashSet<>(controls))) {
                    bad.add(sheet.id() + ": rows " + rows + " are not the anatomy's controls " + controls);
                }
            }
        }
        assertTrue(bad.isEmpty(), String.join("\n", bad));
    }

    @Test
    void leadersDoNotCross() {
        for (final String id : Specimens.ids()) {
            final SpecimenAnatomy a = Specimens.anatomy(id).orElseThrow();
            final int[] k = a.referenceScale();
            final int fw = (int) Math.ceil(a.width() * k[0] / (double) k[1]);
            final int fh = (int) Math.ceil(a.height() * k[0] / (double) k[1]);
            final List<Callouts.Pin> pins = Callouts.layout(a, 33, 0, k[0], k[1], 0, 33 + fw + 20, -2, fh + 2,
                    Callouts.numbering(a));
            for (int i = 0; i < pins.size(); i++) {
                for (int j = i + 1; j < pins.size(); j++) {
                    final Callouts.Pin p = pins.get(i);
                    final Callouts.Pin q = pins.get(j);
                    assertTrue(!Callouts.crosses(p.leaderX(), p.leaderY(), p.anchorX(), p.anchorY(), q.leaderX(),
                            q.leaderY(), q.anchorX(), q.anchorY()), id + ": leaders of " + p.control() + " and "
                            + q.control() + " cross");
                }
            }
        }
    }

    @Test
    void everyDeepLinkLandsOnASheet() throws IllegalAccessException {
        final List<String> bad = new ArrayList<>();
        for (final Field f : BookLinks.class.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) || f.getType() != String.class) {
                continue;
            }
            final String id = (String) f.get(null);
            final boolean lands = Book.find(id).isPresent();
            if (!lands && !PENDING_LINKS.containsKey(id)) {
                bad.add(f.getName() + " (" + id + ") lands on no sheet");
            }
            if (lands && PENDING_LINKS.containsKey(id)) {
                bad.add(f.getName() + " (" + id + ") has its sheet now: take it off PENDING_LINKS");
            }
        }
        assertTrue(bad.isEmpty(), String.join("\n", bad));
    }

    @Test
    void sheetsAreFoundByAnyOfTheirIds() {
        assertTrue(Book.find("power_meter").isPresent());
        assertTrue(Book.find("create_stats:power_meter").isPresent());
        assertTrue(Book.find("create_stats:no_such_block").isEmpty());
        assertTrue(Book.find(null).isEmpty());
        assertEquals("retired", Book.find("create_stats:stats_alarm").orElseThrow().id());
        assertEquals(BackMatter.INDEX_ID, Book.find(BackMatter.INDEX_ID).orElseThrow().id());
        assertEquals("fe_battery_controller", Book.find("create_stats:fe_battery_controller").orElseThrow().id());
        assertTrue(Book.find("create_stats:seismic_scanner").isEmpty(),
                "the Seismic Scanner has no sheet yet; when it does, pick another block for this check");
    }

    @Test
    void everyPonderItemHasARow() {
        final List<String> bad = new ArrayList<>();
        for (final Section s : Section.values()) {
            for (final String id : BookFixtures.ponder().ids(s)) {
                if (!Scenes.covers(id)) {
                    bad.add(id + " has scenes but no Watch it run row names it");
                }
            }
        }
        for (final Scenes.Row r : Scenes.all()) {
            if (!BookFixtures.ponder().ids(r.section()).contains(r.itemId())) {
                bad.add(r.itemId() + " has a row in " + r.section() + " but no scenes there");
            }
        }
        assertTrue(bad.isEmpty(), String.join("\n", bad));
    }
}
