package com.createstats.client;

import com.createstats.chunkloader.ponder.CrownPonderScript;
import com.createstats.stormcoil.ponder.CoilPonderScript;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrownCoilPonderLangTest {
    private static final Path LANG = Path.of("src/main/resources/assets/create_stats/lang/en_us.json");
    private static final Pattern TITLE = Pattern.compile("\\.title\\(\"([a-z_]+)\",\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\)");

    private static final Pattern TEXT = Pattern.compile("\\.text\\(\\s*(\"((?:[^\"\\\\]|\\\\.)*)\"\\s*([,)+])|[^\"\\s])");

    record Scene(String id, String title, List<String> captions) {
    }

    static Scene scan(String source) {
        final Matcher t = TITLE.matcher(source);
        assertTrue(t.find(), "no scene.title(\"id\", \"Title\") call");
        final List<String> captions = new ArrayList<>();
        final Matcher m = TEXT.matcher(source);
        while (m.find()) {
            assertNotNull(m.group(2), "a caption that is not a literal: .text(" + m.group(1));
            assertEquals(")", m.group(3), "a caption with something added to its literal: " + m.group(2));
            captions.add(m.group(2).replace("\\\"", "\""));
        }
        return new Scene(t.group(1), t.group(2), captions);
    }

    private static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static String langValue(String lang, String key) {
        final Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\":\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(lang);
        return m.find() ? m.group(1).replace("\\\"", "\"") : null;
    }

    private static void check(Path source, int cues) throws IOException {
        final Scene s = scan(read(source));
        final String lang = read(LANG);
        assertEquals(cues, s.captions().size(), s.id() + ": captions and cues differ in number");
        assertEquals(s.title(), langValue(lang, "create_stats.ponder." + s.id() + ".header"), s.id() + " header");
        for (int i = 0; i < s.captions().size(); i++) {
            final String c = s.captions().get(i);
            assertTrue(c.length() < 90, "over 90 characters: " + c);
            assertFalse(c.contains("\u2014") || c.contains("\u2013"), "a dash in: " + c);
            assertEquals(c, langValue(lang, "create_stats.ponder." + s.id() + ".text_" + (i + 1)),
                    s.id() + " text_" + (i + 1) + " is not the scene's caption; run gen_ponder_lang.py --write");
        }
        assertEquals(null, langValue(lang, "create_stats.ponder." + s.id() + ".text_" + (s.captions().size() + 1)),
                s.id() + ": a key left over past the last caption");
        for (String line : read(source).split("\n")) {
            if (line.contains(".text(\"")) {
                assertTrue(line.trim().endsWith("\")") || line.trim().endsWith("\");") || line.contains("\")."),
                        "a caption split over lines: " + line.trim());
            }
        }
    }

    @Test
    @DisplayName("the Spark Crown's captions are its lang keys, in order")
    void crown() throws IOException {
        check(Path.of("src/main/java/com/createstats/client/SparkCrownScenes.java"), CrownPonderScript.cues().size());
    }

    @Test
    @DisplayName("the Storm Coil's captions are its lang keys, in order")
    void coil() throws IOException {
        check(Path.of("src/main/java/com/createstats/client/StormCoilScenes.java"), CoilPonderScript.cues().size());
    }
}
