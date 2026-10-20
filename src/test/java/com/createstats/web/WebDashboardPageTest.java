package com.createstats.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.createstats.statsmk3.instrument.Instrument;
import com.createstats.statsmk3.instrument.InstrumentRegistry;
import com.createstats.statsmk3.ui.Tokens;

import org.junit.jupiter.api.Test;

class WebDashboardPageTest {
    private static final String PAGE = WebDashboard.page();

    private static String script() {
        final Matcher m = Pattern.compile("<script>(.*?)</script>", Pattern.DOTALL).matcher(PAGE);
        assertTrue(m.find(), "the page has no script block");
        return m.group(1);
    }

    private static Set<String> matches(String regex, String haystack, int group) {
        final Set<String> found = new LinkedHashSet<>();
        final Matcher m = Pattern.compile(regex).matcher(haystack);
        while (m.find()) {
            found.add(m.group(group));
        }
        return found;
    }

    @Test
    void theDocumentIsPresentAndComplete() {
        assertTrue(PAGE.startsWith("<!doctype html>"), "not an HTML document: " + head(PAGE));
        assertTrue(PAGE.contains("<title>Stats Display Mk III</title>"), "no title");
        assertTrue(PAGE.trim().endsWith("</html>"), "document is truncated");
        assertTrue(PAGE.length() > 20_000, "suspiciously small: " + PAGE.length() + " chars");
    }

    @Test
    void theDocumentIsSelfContained() {
        assertFalse(PAGE.contains("http://"), "the page references an absolute http URL");
        assertFalse(PAGE.contains("https://"), "the page references an absolute https URL");
        assertFalse(PAGE.contains("<script src"), "the page loads an external script");
        assertFalse(PAGE.contains("rel=\"stylesheet\""), "the page loads an external stylesheet");
        assertFalse(PAGE.contains("@import"), "the page imports a stylesheet");
        assertFalse(PAGE.contains("url(http"), "the page loads a remote asset from CSS");
    }

    @Test
    void thePageFetchesTheRouteTheServerServes() {
        assertTrue(script().contains("fetch('" + LocalWebServer.SNAPSHOT_PATH + "'"),
                "the page does not fetch " + LocalWebServer.SNAPSHOT_PATH);
    }

    @Test
    void thePageExpectsTheSnapshotVersionTheGameSends() {
        final Matcher m = Pattern.compile("version:\\s*(\\d+)").matcher(script());
        assertTrue(m.find(), "the page does not declare a snapshot version");
        assertEquals(WebSnapshot.VERSION, Integer.parseInt(m.group(1)),
                "the page and WebSnapshot disagree about the snapshot version");
    }

    @Test
    void everyRegisteredInstrumentHasAPainter() {
        final Matcher m = Pattern.compile("const PAINTERS = \\{(.*?)\\};", Pattern.DOTALL)
                .matcher(script());
        assertTrue(m.find(), "the page has no painter table");
        final Set<String> painters = matches("(\\w+):", m.group(1), 1);

        for (final Instrument instrument : InstrumentRegistry.all()) {
            assertTrue(painters.contains(instrument.id()),
                    "no painter in the dashboard for instrument '" + instrument.id()
                            + "'. Painters: " + painters);
        }
    }

    @Test
    void everyElementTheScriptLooksUpExists() {
        final Set<String> declared = matches("\\bid=\"([^\"]+)\"", PAGE, 1);
        final Set<String> used = matches("\\$\\('([^']+)'\\)", script(), 1);
        assertFalse(used.isEmpty(), "the id scan found nothing, so this test is not testing");

        for (final String id : used) {
            assertTrue(declared.contains(id), "the script looks up #" + id + ", which is not in the markup");
        }
    }

    @Test
    void theDarkPaletteMatchesTheStudioTokens() {
        final Matcher m = Pattern.compile(":root \\{(.*?)\\n\\}", Pattern.DOTALL).matcher(PAGE);
        assertTrue(m.find(), "the page has no :root token block");
        final String root = m.group(1);

        assertVar(root, "--canvas", Tokens.CANVAS);
        assertVar(root, "--surface", Tokens.SURFACE);
        assertVar(root, "--raised", Tokens.RAISED);
        assertVar(root, "--overlay", Tokens.OVERLAY);
        assertVar(root, "--line", Tokens.LINE);
        assertVar(root, "--line-hi", Tokens.LINE_HI);
        assertVar(root, "--ink", Tokens.INK);
        assertVar(root, "--ink-2", Tokens.INK_2);
        assertVar(root, "--ink-3", Tokens.INK_3);
        assertVar(root, "--accent", Tokens.ACCENT);
        assertVar(root, "--good", Tokens.GOOD);
        assertVar(root, "--caution", Tokens.CAUTION);
        assertVar(root, "--critical", Tokens.CRITICAL);

        assertVar(root, "--held", Tokens.HELD);
        assertVar(root, "--limit", Tokens.LIMIT);
        assertVar(root, "--sky", com.createstats.statsmk3.page.AeroInstruments.SKY_TOP);
        assertVar(root, "--earth", com.createstats.statsmk3.page.AeroInstruments.GROUND_HI);
    }

    private static void assertVar(String block, String name, int argb) {
        final Matcher m = Pattern.compile(Pattern.quote(name) + ":\\s*(#[0-9a-fA-F]{6})\\s*;")
                .matcher(block);
        assertTrue(m.find(), "the page does not define " + name);
        assertEquals(WebJson.hex(argb), m.group(1).toLowerCase(java.util.Locale.ROOT),
                name + " has drifted from the studio token");
    }

    @Test
    void theDocumentIsServedFromTheAssetAndNotFromTheFallback() {
        assertFalse(PAGE.contains("The dashboard document is missing from this build."),
                "the packaged dashboard asset was not found on the classpath");
        assertFalse(PAGE.contains("The dashboard document could not be read."),
                "the packaged dashboard asset could not be read");
    }

    private static String head(String s) {
        return s.length() < 120 ? s : s.substring(0, 120);
    }
}
