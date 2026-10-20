package com.createstats.book;

import com.createstats.book.content.Book;
import com.createstats.book.content.Part;
import com.createstats.book.content.Section;
import com.createstats.book.content.Sheet;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookCoverageTest {
    private static final Path CATALOG_TEST =
            Path.of("src/test/java/com/createstats/registry/CreativeCatalogCoverageTest.java");
    private static final Path PLUGIN = Path.of("src/main/java/com/createstats/client/CreateStatsPonderPlugin.java");
    private static final Path BLOCKS = Path.of("src/main/java/com/createstats/registry/ModBlocks.java");
    private static final Path ITEMS = Path.of("src/main/java/com/createstats/registry/ModItems.java");

    private static final Map<String, String> NOT_IN_THE_BOOK = Map.of(
            "ModItems.SUPPORT_TERMINAL", "dev only: the author's tool, handed out by command ([D], brief 3.2)");

    private static final Pattern COMMENTS = Pattern.compile("(?s)/\\*.*?\\*/|//[^\\n]*");

    private static final Pattern REGISTRATION = Pattern.compile(
            "\\b([A-Z][A-Z0-9_]*)\\s*=\\s*CreateStats\\.registrate\\(\\)\\s*\\.(?:block|item)\\(\\s*\"([a-z0-9_]+)\"");
    private static final Pattern UNLISTED_BLOCK = Pattern.compile("(?s)UNLISTED\\s*=\\s*Set\\.of\\((.*?)\\);");
    private static final Pattern FIELD_NAME = Pattern.compile("\"(ModBlocks|ModItems)\\.([A-Z][A-Z0-9_]*)\"");
    private static final Pattern STORY_BOARD = Pattern.compile(
            "addStoryBoard\\(\\s*(?:com\\.createstats\\.registry\\.)?(ModBlocks|ModItems)\\.([A-Z][A-Z0-9_]*)\\.get\\(\\)");

    private static String source(Path p) throws IOException {
        return COMMENTS.matcher(Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n")).replaceAll("");
    }

    static Map<String, String> registryIds() throws IOException {
        final Map<String, String> out = new HashMap<>();
        for (final Path p : new Path[]{BLOCKS, ITEMS}) {
            final String owner = p == BLOCKS ? "ModBlocks" : "ModItems";
            final Matcher m = REGISTRATION.matcher(source(p));
            while (m.find()) {
                out.put(owner + "." + m.group(1), "create_stats:" + m.group(2));
            }
        }
        return out;
    }

    @Test
    void everyUnlistedItemIsOnTheRetiredSheet() throws IOException {
        final Map<String, String> ids = registryIds();
        assertTrue(ids.size() > 100, "the registration scan found only " + ids.size() + " fields; REGISTRATION drifted");
        final Matcher block = UNLISTED_BLOCK.matcher(source(CATALOG_TEST));
        assertTrue(block.find(), "CreativeCatalogCoverageTest no longer declares UNLISTED = Set.of(...)");
        final Set<String> expected = new TreeSet<>();
        final Matcher f = FIELD_NAME.matcher(block.group(1));
        int named = 0;
        while (f.find()) {
            named++;
            final String field = f.group(1) + "." + f.group(2);
            if (NOT_IN_THE_BOOK.containsKey(field)) {
                continue;
            }
            final String id = ids.get(field);
            assertTrue(id != null, field + " is unlisted but registers no id the scan can read");
            expected.add(id);
        }

        assertTrue(named >= 3, "the UNLISTED scan found only " + named + " entries; FIELD_NAME drifted");
        final Set<String> retired = new TreeSet<>();
        for (final Sheet s : Book.sheets()) {
            for (final Part p : s.parts()) {
                if (p instanceof Part.Retired r) {
                    r.rows().forEach(u -> retired.add(u.itemId()));
                }
            }
        }
        assertEquals(expected, retired, "the retired sheet must name exactly the unlisted items the book covers");
    }

    @Test
    void thePonderFixtureIsThePluginsRegistrations() throws IOException {
        final Map<String, String> ids = registryIds();
        final Set<String> registered = new TreeSet<>();
        final Matcher m = STORY_BOARD.matcher(source(PLUGIN));
        int boards = 0;
        while (m.find()) {
            boards++;
            final String field = m.group(1) + "." + m.group(2);
            final String id = ids.get(field);
            assertTrue(id != null, field + " carries a story board but registers no id the scan can read");
            registered.add(id);
        }
        assertTrue(boards >= 20, "the plugin scan found only " + boards + " story boards; STORY_BOARD drifted");
        final Set<String> fixture = new TreeSet<>();
        for (final Section s : Section.values()) {
            fixture.addAll(BookFixtures.ponder().ids(s));
        }
        assertEquals(registered, fixture, "BookFixtures.ponder() must list exactly the items with story boards");
    }
}
