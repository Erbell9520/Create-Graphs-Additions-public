package com.createstats.client;

import com.createstats.client.ponder.coupler.CouplerPonderScript;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CouplerPonderLangTest {
    private static final Path SOURCE = Path.of("src/main/java/com/createstats/client/CouplerScenes.java");
    private static final Path LANG = Path.of("src/main/resources/assets/create_stats/lang/en_us.json");

    @Test
    @DisplayName("the Coupler's captions are its lang keys, in order")
    void captions() throws IOException {
        final int cues = CouplerPonderScript.CAPTIONS.length;
        final CrownCoilPonderLangTest.Scene s = CrownCoilPonderLangTest.scan(
                Files.readString(SOURCE, StandardCharsets.UTF_8).replace("\r\n", "\n"));
        assertEquals(cues, s.captions().size(), "captions and cue ticks differ in number");
        final String lang = Files.readString(LANG, StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertEquals(s.title(), value(lang, "create_stats.ponder." + s.id() + ".header"));
        for (int i = 0; i < cues; i++) {
            final String c = s.captions().get(i);
            assertTrue(c.length() < 90, "over 90 characters: " + c);
            assertTrue(!c.contains("—") && !c.contains("–"), "a dash in: " + c);
            assertEquals(c, value(lang, "create_stats.ponder." + s.id() + ".text_" + (i + 1)), "text_" + (i + 1));
        }
        assertEquals(null, value(lang, "create_stats.ponder." + s.id() + ".text_" + (cues + 1)), "a key left over");
    }

    @Test
    @DisplayName("each caption holds and clears before the next")
    void pacing() {
        final int[] at = CouplerPonderScript.CAPTIONS;
        for (int i = 1; i < at.length; i++) {
            assertTrue(at[i] >= at[i - 1] + CouplerPonderScript.HOLD + 10, "caption " + (i + 1) + " crowds the last");
        }
        assertTrue(CouplerPonderScript.HOLD >= 60);
        assertTrue(at[at.length - 1] + CouplerPonderScript.HOLD + 20 <= CouplerPonderScript.LENGTH,
                "the scene rests after its last caption");
        assertTrue(CouplerPonderScript.LENGTH <= 75 * 20 && CouplerPonderScript.LENGTH >= 40 * 20, "40 to 75 s");
    }

    @Test
    @DisplayName("the stand-in labels are translated")
    void labels() throws IOException {
        final String lang = Files.readString(LANG, StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertNotNull(value(lang, "create_stats.ponder.universal_power_coupler.label_theirs"));
        assertNotNull(value(lang, "create_stats.ponder.universal_power_coupler.label_ours"));
    }

    private static String value(String lang, String key) {
        final java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "\"" + java.util.regex.Pattern.quote(key) + "\":\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(lang);
        return m.find() ? m.group(1).replace("\\\"", "\"") : null;
    }
}
